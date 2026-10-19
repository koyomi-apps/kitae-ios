// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

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

    /// The name, trimmed, if no other plan has it.
    static func usableName(_ db: Database, _ name: String, excluding planID: Int64? = nil) throws -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw NameProblem.empty }
        let taken = try Bool.fetchOne(db, sql: """
            SELECT EXISTS
            (SELECT 1
            FROM `plans`
            WHERE `name` = ?
            AND `id` IS NOT ?)
            """, arguments: [trimmed, planID]) ?? false
        guard !taken else { throw NameProblem.taken }
        return trimmed
    }

    @discardableResult
    static func create(_ db: Database, name: String) throws -> Int64 {
        let name = try usableName(db, name)
        try db.execute(sql: "INSERT INTO `plans` (`name`) VALUES (?)", arguments: [name])
        return db.lastInsertedRowID
    }

    static func rename(_ db: Database, planID: Int64, to name: String) throws {
        let name = try usableName(db, name, excluding: planID)
        try db.execute(sql: "UPDATE `plans` SET `name` = ? WHERE `id` = ?", arguments: [name, planID])
    }

    /// Removes a plan and its days. Past workouts stay, without their plan.
    static func delete(_ db: Database, planID: Int64) throws {
        try db.execute(sql: "DELETE FROM `plans` WHERE `id` = ?", arguments: [planID])
    }

    static func hasExercises(_ db: Database, planID: Int64) throws -> Bool {
        try Bool.fetchOne(db, sql: """
            SELECT EXISTS
            (SELECT 1
            FROM `routine_exercises`
            INNER JOIN `routines` ON `routines`.`id` = `routine_exercises`.`routineId`
            WHERE `routines`.`planId` = ?)
            """, arguments: [planID]) ?? false
    }

    // MARK: Days

    @discardableResult
    static func addDay(_ db: Database, planID: Int64, name: String) throws -> Int64 {
        let name = try dayName(name)
        try db.execute(sql: """
            INSERT INTO `routines` (`planId`, `name`, `position`)
            VALUES (?, ?, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routines` WHERE `planId` = ?))
            """, arguments: [planID, name, planID])
        return db.lastInsertedRowID
    }

    static func renameDay(_ db: Database, dayID: Int64, to name: String) throws {
        try db.execute(sql: "UPDATE `routines` SET `name` = ? WHERE `id` = ?", arguments: [try dayName(name), dayID])
    }

    /// Removes a day with its exercises and planned sets. Past workouts of it stay.
    static func deleteDay(_ db: Database, dayID: Int64) throws {
        try db.execute(sql: "DELETE FROM `routines` WHERE `id` = ?", arguments: [dayID])
    }

    /// Puts days in this order.
    static func orderDays(_ db: Database, _ dayIDs: [Int64]) throws {
        for (position, id) in dayIDs.enumerated() {
            try db.execute(sql: "UPDATE `routines` SET `position` = ? WHERE `id` = ?", arguments: [position, id])
        }
    }

    private static func dayName(_ name: String) throws -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw NameProblem.empty }
        return trimmed
    }

    // MARK: Exercises in a day

    /// Adds an exercise at the end of a day with these sets. Returns its place in the day.
    @discardableResult
    static func addExercise(_ db: Database, dayID: Int64, exerciseID: Int64, sets: [SetDraft]) throws -> Int64 {
        try db.execute(sql: """
            INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`)
            VALUES (?, ?, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_exercises` WHERE `routineId` = ?))
            """, arguments: [dayID, exerciseID, dayID])
        let slotID = db.lastInsertedRowID
        for (position, set) in sets.enumerated() {
            try insertSet(db, slotID: slotID, set: set, position: position)
        }
        return slotID
    }

    /// Takes an exercise out of a day, with its planned sets.
    static func removeExercise(_ db: Database, slotID: Int64) throws {
        try db.execute(sql: "DELETE FROM `routine_exercises` WHERE `id` = ?", arguments: [slotID])
    }

    /// Puts a day's exercises in this order.
    static func orderExercises(_ db: Database, _ slotIDs: [Int64]) throws {
        for (position, id) in slotIDs.enumerated() {
            try db.execute(sql: "UPDATE `routine_exercises` SET `position` = ? WHERE `id` = ?", arguments: [position, id])
        }
    }

    /// What an exercise starts with when it's added to a day: the sets done the last time it
    /// was trained, or three of `fallback`. Kitae for Android's `suggestedSets`.
    static func suggestedSets(_ db: Database, exerciseID: Int64, fallback: SetDraft) throws -> [SetDraft] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT *
            FROM `sets`
            WHERE `exerciseId` = ?
            AND `isCompleted` = 1
            AND `sessionId` =
            (SELECT `sets`.`sessionId`
            FROM `sets`
            INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
            WHERE `sets`.`exerciseId` = ?
            AND `sets`.`isCompleted` = 1
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC
            LIMIT 1)
            ORDER BY `order` ASC, `id` ASC
            """, arguments: [exerciseID, exerciseID])
        guard !rows.isEmpty else { return Array(repeating: fallback, count: 3) }
        return rows.map(SetDraft.init(row:))
    }

    // MARK: Planned sets

    /// Adds a set after an exercise's other planned sets, keeping warm-ups first. Returns its id.
    @discardableResult
    static func addSet(_ db: Database, slotID: Int64, set: SetDraft) throws -> Int64 {
        let position = try Int.fetchOne(db, sql: """
            SELECT COALESCE(MAX(`position`), -1) + 1
            FROM `routine_sets`
            WHERE `routineExerciseId` = ?
            """, arguments: [slotID]) ?? 0
        let id = try insertSet(db, slotID: slotID, set: set, position: position)
        try groupWarmups(db, slotID: slotID)
        return id
    }

    /// Saves a planned set's reps, weight, type and rest, keeping warm-ups first.
    static func updateSet(_ db: Database, setID: Int64, set: SetDraft) throws {
        try db.execute(sql: """
            UPDATE `routine_sets`
            SET `reps` = ?, `weight` = ?, `type` = ?, `restSeconds` = ?
            WHERE `id` = ?
            """, arguments: [set.reps, set.weightKg, set.type.rawValue, set.restSeconds, setID])
        if let slotID = try Int64.fetchOne(db, sql: "SELECT `routineExerciseId` FROM `routine_sets` WHERE `id` = ?", arguments: [setID]) {
            try groupWarmups(db, slotID: slotID)
        }
    }

    static func deleteSet(_ db: Database, setID: Int64) throws {
        try db.execute(sql: "DELETE FROM `routine_sets` WHERE `id` = ?", arguments: [setID])
    }

    /// Plans a set, without weight if the exercise doesn't use any.
    @discardableResult
    private static func insertSet(_ db: Database, slotID: Int64, set: SetDraft, position: Int) throws -> Int64 {
        let usesWeight = try Bool.fetchOne(db, sql: """
            SELECT `exercises`.`usesWeight`
            FROM `routine_exercises`
            INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
            WHERE `routine_exercises`.`id` = ?
            """, arguments: [slotID]) ?? true
        try db.execute(sql: """
            INSERT INTO `routine_sets` (`routineExerciseId`, `reps`, `weight`, `type`, `position`, `restSeconds`)
            VALUES (?, ?, ?, ?, ?, ?)
            """, arguments: [slotID, set.reps, usesWeight ? set.weightKg : 0, set.type.rawValue, position, set.restSeconds])
        return db.lastInsertedRowID
    }
}
