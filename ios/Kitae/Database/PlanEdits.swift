// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// Why a name can't be used.
nonisolated enum NameProblem: Error, Equatable, LocalizedError {
    case empty
    /// Another plan has it. Plan names are unique, as in Kitae for Android.
    case taken
    /// Another exercise has it, as Kitae for Android checks too.
    case exerciseTaken

    var errorDescription: String? {
        switch self {
        case .empty: "Give it a name."
        case .taken: "Another plan already has that name."
        case .exerciseTaken: "Another exercise already has that name."
        }
    }
}

/// Changing plans, the way Kitae for Android's plan editor does (its `LocalPlanRepo` and
/// `RoutineDao`). Days are routines in the database, and an exercise's place in a day is a
/// routine exercise.
nonisolated extension Plans {
    // MARK: Plans

    @discardableResult
    static func create(_ db: Database, name: String) throws -> Int64 {
        try naming { try db.core.createPlan(name: name) }
    }

    static func rename(_ db: Database, planID: Int64, to name: String) throws {
        try naming { try db.core.renamePlan(planId: planID, name: name) }
    }

    /// Removes a plan and its days. Past workouts stay, without their plan.
    static func delete(_ db: Database, planID: Int64) throws {
        try db.core.deletePlan(planId: planID)
    }

    static func hasExercises(_ db: Database, planID: Int64) throws -> Bool {
        try db.core.planHasExercises(planId: planID)
    }

    // MARK: Days

    @discardableResult
    static func addDay(_ db: Database, planID: Int64, name: String) throws -> Int64 {
        try naming { try db.core.addDay(planId: planID, name: name) }
    }

    static func renameDay(_ db: Database, dayID: Int64, to name: String) throws {
        try naming { try db.core.renameDay(dayId: dayID, name: name) }
    }

    /// Removes a day with its exercises and planned sets. Past workouts of it stay.
    static func deleteDay(_ db: Database, dayID: Int64) throws {
        try db.core.deleteDay(dayId: dayID)
    }

    /// Puts days in this order.
    static func orderDays(_ db: Database, _ dayIDs: [Int64]) throws {
        try db.core.orderDays(dayIds: dayIDs.map(\.kotlin))
    }

    // MARK: Exercises in a day

    /// Adds an exercise at the end of a day with these sets. Returns its place in the day.
    @discardableResult
    static func addExercise(_ db: Database, dayID: Int64, exerciseID: Int64, sets: [SetDraft]) throws -> Int64 {
        try db.core.addExercise(dayId: dayID, exerciseId: exerciseID, sets: sets.map(\.core))
    }

    /// Takes an exercise out of a day, with its planned sets.
    static func removeExercise(_ db: Database, slotID: Int64) throws {
        try db.core.removeExercise(dayExerciseId: slotID)
    }

    /// Puts a day's exercises in this order.
    static func orderExercises(_ db: Database, _ slotIDs: [Int64]) throws {
        try db.core.orderExercises(dayExerciseIds: slotIDs.map(\.kotlin))
    }

    /// What an exercise starts with when it's added to a day: the sets done the last time it
    /// was trained, or three of `fallback`. Kitae for Android's `suggestedSets`.
    static func suggestedSets(_ db: Database, exerciseID: Int64, fallback: SetDraft) throws -> [SetDraft] {
        try db.core.suggestedSets(exerciseId: exerciseID, fallback: fallback.core).map { SetDraft($0) }
    }

    // MARK: Planned sets

    /// Adds a set after an exercise's other planned sets, keeping warm-ups first. Returns its id.
    @discardableResult
    static func addSet(_ db: Database, slotID: Int64, set: SetDraft) throws -> Int64 {
        try db.core.addPlannedSet(dayExerciseId: slotID, set: set.core)
    }

    /// Saves a planned set's reps, weight, type and rest, keeping warm-ups first.
    static func updateSet(_ db: Database, setID: Int64, set: SetDraft) throws {
        try db.core.updatePlannedSet(plannedSetId: setID, set: set.core)
    }

    static func deleteSet(_ db: Database, setID: Int64) throws {
        try db.core.deletePlannedSet(plannedSetId: setID)
    }

}
