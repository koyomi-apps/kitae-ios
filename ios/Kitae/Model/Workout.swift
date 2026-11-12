// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
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
        exercises = try database.read { db in try Sessions.exercises(db, sessionID: id, dayID: session.dayID) }
        self.database = database
        self.restStore = restStore
        self.defaultRestSeconds = defaultRestSeconds
        self.defaultWeightKg = defaultWeightKg
        if let saved = restStore.load(workoutID: id, workoutStartedAt: startedAt, now: .now) {
            rest = saved
            scheduleRestEnd()
        }
    }

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets.count } }
    var doneCount: Int { exercises.reduce(0) { $0 + $1.sets.filter(\.isDone).count } }
    var notDoneCount: Int { setCount - doneCount }
    /// Sets added during the workout and done, which Finish offers to add to the day.
    var addedDoneCount: Int {
        guard dayID != nil else { return 0 }
        return exercises.reduce(0) { $0 + $1.sets.filter { $0.isAdded && $0.isDone }.count }
    }

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
        for exercise in exercises {
            if let set = exercise.sets.first(where: { !$0.isDone }) {
                return (exercise.exercise, set)
            }
        }
        return nil
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
        guard let set = set(id), let exercise = exercise(containing: id) else { return }
        let isLastSet = notDoneCount <= 1
        let isDone = !set.isDone
        save { db in try Sessions.setDone(db, setID: id, isDone: isDone) }
        let seconds = set.restSeconds ?? exercise.exercise.restSeconds ?? defaultRestSeconds
        if isDone, !isLastSet, seconds > 0 {
            startRest(seconds: seconds)
        }
    }

    /// Adds a set like the exercise's last one, not done yet. A warm-up is followed by a
    /// standard set. With no set to copy, it's like the day's planned sets or the last time
    /// the exercise was done, or 12 reps of the default weight.
    func addSet(to exerciseID: WorkoutExercise.ID) {
        guard let exercise = exercises.first(where: { $0.id == exerciseID }) else { return }
        let sessionID = id
        let dayID = dayID
        let defaultSet = SetDraft(reps: 12, weightKg: defaultWeightKg)
        let last = exercise.sets.last.map {
            SetDraft(reps: $0.reps, weightKg: $0.weightKg, type: $0.type, restSeconds: $0.restSeconds)
        }
        save { db in
            var set = try last ?? Sessions.lastKnownSet(db, exerciseID: exerciseID, dayID: dayID) ?? defaultSet
            if set.type == .warmup { set.type = .standard }
            try Sessions.addSet(db, sessionID: sessionID, exerciseID: exerciseID, set: set)
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
            exercises = try database.read { db in try Sessions.exercises(db, sessionID: sessionID, dayID: dayID) }
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
