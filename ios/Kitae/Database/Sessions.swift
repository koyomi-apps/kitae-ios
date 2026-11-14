// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import KitaeCore

/// How Finish treats the sets that weren't done. Kitae for Android's `FinishMode`.
nonisolated enum FinishMode: Sendable {
    /// They stay in the workout as not done, and the plan stays the same.
    case keepSkipped
    /// They're removed from the workout and from the day's plan, along with exercises that
    /// had nothing done.
    case removeFromPlan
    /// They're removed from this workout only.
    case discard
}

/// A workout that hasn't been finished.
nonisolated struct ActiveSession: Hashable, Sendable {
    var id: Int64
    var planID: Int64?
    var dayID: Int64?
    var dayName: String?
    var startedAt: Int64?
    var date: Int
}

/// Workouts and their sets, read and written the way Kitae for Android does (its
/// `SessionDao`, `SetsDao` and `LocalSessionRepo`). A workout is a session in the database.
nonisolated enum Sessions {
    /// Reps in reserve, which Kitae for Android stores with each set but doesn't ask for yet.
    static let defaultRir = 2

    // MARK: Starting

    /// Starts a workout of a day, with the day's planned sets not done yet. A workout of the
    /// same day that was started today and not finished carries on instead.
    static func start(_ db: Database, dayID: Int64, today: Int, now: Int64) throws -> Int64 {
        try db.core.startWorkout(dayId: dayID, today: Int64(today), now: now)
    }

    // MARK: Reading

    /// The newest workout that isn't finished, if it was started today or yesterday, so a
    /// workout that went past midnight still counts.
    static func active(_ db: Database, today: Int) throws -> ActiveSession? {
        try db.core.activeWorkout(today: Int64(today)).map { ActiveSession($0) }
    }

    static func session(_ db: Database, id: Int64) throws -> ActiveSession? {
        try db.core.workout(id: id).map { ActiveSession($0) }
    }

    /// The day of the newest workout that had something done.
    static func lastDoneDayID(_ db: Database) throws -> Int64? {
        try db.core.lastDoneDayId()?.int64Value
    }

    /// Whether any set was ever done, so Today can tell a first workout apart.
    static func hasDoneSets(_ db: Database) throws -> Bool {
        try db.core.hasDoneSets()
    }

    /// A workout's exercises and sets. While it's going on, the day's exercises come first in
    /// the plan's order, even ones without sets, then any others in the order they were added.
    static func exercises(_ db: Database, sessionID: Int64, dayID: Int64?) throws -> [WorkoutExercise] {
        try db.core.workoutExercises(workoutId: sessionID, dayId: dayID?.kotlin).map { WorkoutExercise($0) }
    }

    /// What a new set of an exercise starts as when there's nothing to copy in the workout:
    /// the day's last planned set, or the last set ever done, or nil.
    static func lastKnownSet(_ db: Database, exerciseID: Int64, dayID: Int64?) throws -> SetDraft? {
        try db.core.lastKnownSet(exerciseId: exerciseID, dayId: dayID?.kotlin).map { SetDraft($0) }
    }

    // MARK: Changing sets

    static func setDone(_ db: Database, setID: Int64, isDone: Bool) throws {
        try db.core.setDone(setId: setID, isDone: isDone)
    }

    /// Saves a set's reps, weight, type and rest, keeping its exercise's warm-ups first.
    static func update(_ db: Database, set: WorkoutSet) throws {
        let draft = SetDraft(reps: set.reps, weightKg: set.weightKg, type: set.type, restSeconds: set.restSeconds)
        try db.core.updateSet(setId: set.id, set: draft.core)
    }

    /// Adds a set that isn't done yet after the workout's other sets, without weight if the
    /// exercise doesn't use any. Returns its id.
    @discardableResult
    static func addSet(_ db: Database, sessionID: Int64, exerciseID: Int64, set: SetDraft) throws -> Int64 {
        try db.core.addSet(workoutId: sessionID, exerciseId: exerciseID, set: set.core)
    }

    static func deleteSet(_ db: Database, setID: Int64) throws {
        try db.core.deleteSet(setId: setID)
    }

    // MARK: Finishing

    /// Finishes a workout: what was done goes back into the day's plan, and the sets that
    /// weren't done are kept or removed as `mode` says. A workout left with no sets is removed.
    static func finish(_ db: Database, sessionID: Int64, mode: FinishMode, addNewSets: Bool, now: Int64) throws {
        let name = switch mode {
        case .keepSkipped: "KeepSkipped"
        case .removeFromPlan: "RemoveFromPlan"
        case .discard: "Discard"
        }
        try db.core.finishWorkout(workoutId: sessionID, mode: name, addNewSets: addNewSets, now: now)
    }

    /// Removes a workout and all its sets. The plan stays as it was.
    static func discard(_ db: Database, sessionID: Int64) throws {
        try db.core.discardWorkout(workoutId: sessionID)
    }
}
