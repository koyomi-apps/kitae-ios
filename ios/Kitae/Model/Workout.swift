// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore
import Observation
import os

nonisolated struct WorkoutSet: Identifiable, Hashable, Sendable {
    var id: Int64
    var type: SetType
    var reps: Int
    var weightKg: Double
    var restSeconds: Int?
    var isDone = false
    /// Added during the workout rather than planned.
    var isAdded = false
}

nonisolated struct WorkoutExercise: Identifiable, Hashable, Sendable {
    /// The exercise's id: a workout lists each exercise once.
    var id: Int64
    var exercise: Exercise
    var sets: [WorkoutSet]
}

/// A workout in progress: the day's sets, which are done, and the rest timer.
///
/// Every change is saved straight away and the sets are read back, so the screen always
/// shows what's in the database.
@Observable
final class Workout: Identifiable {
    let id: Int64
    /// The plan this workout is from. nil if the plan was deleted.
    let planID: Int64?
    /// The plan's day this workout is of. nil if the day was deleted.
    let dayID: Int64?
    let dayName: String
    let startedAt: Date
    private(set) var exercises: [WorkoutExercise]
    /// The shared module's count of the same sets (`WorkoutProgress.kt`), read with them.
    @ObservationIgnored private var progress: KitaeCore.WorkoutProgress
    private(set) var rest = RestTimer()
    /// Goes up each time a rest starts, and each time one runs out, so the Lock Screen can
    /// tell a new rest apart and the screen can tap the phone.
    private(set) var restsStarted = 0
    private(set) var restsFinished = 0
    var defaultRestSeconds: Int
    var defaultWeightKg: Double
    /// Called after every change, so the Lock Screen and alerts can follow.
    @ObservationIgnored var onChange: (() -> Void)?

    private let database: KitaeDatabase
    private let restStore: RestTimerStore
    @ObservationIgnored private var restEnd: Task<Void, Never>?

    private static let log = Logger(subsystem: "ca.gureet.kitae", category: "workout")

    /// The workout with this id, or nil if there isn't one. A rest that was going when
    /// Kitae was last closed carries on.
    init?(
        id: Int64,
        database: KitaeDatabase,
        restStore: RestTimerStore,
        defaultRestSeconds: Int,
        defaultWeightKg: Double
    ) throws {
        guard let session = try database.read({ db in try Sessions.session(db, id: id) }) else { return nil }
        self.id = id
        planID = session.planID
        dayID = session.dayID
        dayName = session.dayName ?? "Workout"
        startedAt = session.startedAt.map(Date.init(epochMilliseconds:)) ?? EpochDay.date(session.date)
        let progress = try database.read { db in try Sessions.progress(db, sessionID: id, dayID: session.dayID) }
        self.progress = progress
        exercises = progress.exercises.map { WorkoutExercise($0) }
        self.database = database
        self.restStore = restStore
        self.defaultRestSeconds = defaultRestSeconds
        self.defaultWeightKg = defaultWeightKg
        if let saved = restStore.load(workoutID: id, workoutStartedAt: startedAt, now: .now) {
            rest = saved
            scheduleRestEnd()
        }
    }

    var setCount: Int { Int(progress.setCount) }
    var doneCount: Int { Int(progress.doneCount) }
    var notDoneCount: Int { Int(progress.notDoneCount) }
    /// Sets added during the workout and done, which Finish offers to add to the day.
    var addedDoneCount: Int { Int(progress.addedDoneCount) }

    func set(_ id: WorkoutSet.ID) -> WorkoutSet? {
        for exercise in exercises {
            if let found = exercise.sets.first(where: { $0.id == id }) { return found }
        }
        return nil
    }

    func exercise(containing setID: WorkoutSet.ID) -> WorkoutExercise? {
        exercises.first { $0.sets.contains { $0.id == setID } }
    }

    /// The first set that isn't done yet, in the order of the screen.
    var nextSet: (exercise: Exercise, set: WorkoutSet)? {
        progress.nextSet.map { (Exercise($0.exercise), WorkoutSet($0.set)) }
    }

    /// Changes a set's reps, weight, type or rest.
    func update(_ id: WorkoutSet.ID, _ change: (inout WorkoutSet) -> Void) {
        guard let old = set(id) else { return }
        var new = old
        change(&new)
        guard new != old else { return }
        save { db in try Sessions.update(db, set: new) }
    }

    /// Ticks a set off, or unticks it. Ticking starts the rest timer, unless it was the last set.
    func toggleDone(_ id: WorkoutSet.ID) {
        guard let set = set(id) else { return }
        let seconds = progress.restAfterTicking(setId: id, defaultRestSeconds: Int32(clamping: defaultRestSeconds))
        save { db in try Sessions.setDone(db, setID: id, isDone: !set.isDone) }
        if let seconds {
            startRest(seconds: seconds.intValue)
        }
    }

    /// Adds a set like the exercise's last one, not done yet. A warm-up is followed by a
    /// standard set. With no set to copy, it's like the day's planned sets or the last time
    /// the exercise was done, or 12 reps of the default weight.
    func addSet(to exerciseID: WorkoutExercise.ID) {
        guard exercises.contains(where: { $0.id == exerciseID }) else { return }
        let sessionID = id
        let dayID = dayID
        let defaultWeightKg = defaultWeightKg
        save { db in
            try Sessions.addSetLikeLast(db, sessionID: sessionID, exerciseID: exerciseID, dayID: dayID,
                                        defaultWeightKg: defaultWeightKg)
        }
    }

    func deleteSet(_ id: WorkoutSet.ID) {
        save { db in try Sessions.deleteSet(db, setID: id) }
    }

    /// Reads the sets again, after the day's plan was edited.
    func reload() {
        save { _ in }
    }

    /// Saves a change, then reads the sets back.
    private func save(_ change: (Database) throws -> Void) {
        let sessionID = id
        let dayID = dayID
        do {
            try database.write(change)
            progress = try database.read { db in try Sessions.progress(db, sessionID: sessionID, dayID: dayID) }
            exercises = progress.exercises.map { WorkoutExercise($0) }
        } catch {
            Self.log.error("Couldn't save the workout: \(error.localizedDescription, privacy: .public)")
            assertionFailure("Couldn't save the workout: \(error)")
        }
        onChange?()
    }

    // MARK: Rest timer

    func startRest(seconds: Int) {
        rest.start(seconds: seconds, at: .now)
        restsStarted += 1
        restChanged()
    }

    func pauseRest() {
        rest.pause(at: .now)
        restChanged()
    }

    func resumeRest() {
        rest.resume(at: .now)
        restChanged()
    }

    /// Adds or takes away time. Taking away more than is left ends the rest.
    func addRest(_ seconds: TimeInterval) {
        rest.add(seconds, at: .now)
        restChanged()
    }

    func skipRest() {
        guard rest.isActive else { return }
        rest.stop()
        restChanged()
    }

    private func restChanged() {
        scheduleRestEnd()
        restStore.save(rest, workoutID: id, workoutStartedAt: startedAt)
        onChange?()
    }

    /// Ends the rest when it runs out, while Kitae is running.
    private func scheduleRestEnd() {
        restEnd?.cancel()
        guard let endsAt = rest.endsAt else { return }
        restEnd = Task { [weak self] in
            let delay = endsAt.timeIntervalSinceNow
            if delay > 0 {
                try? await Task.sleep(for: .seconds(delay))
            }
            guard !Task.isCancelled, let self, self.rest.endsAt == endsAt else { return }
            self.rest.stop()
            self.restsFinished += 1
            self.restChanged()
        }
    }
}
