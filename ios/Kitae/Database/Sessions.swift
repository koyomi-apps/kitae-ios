// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import GRDB

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
        if let existing = try Int64.fetchOne(db, sql: """
            SELECT `id`
            FROM `sessions`
            WHERE `date` = ?
            AND `routineId` = ?
            AND `isFinished` = 0
            ORDER BY `id` DESC
            LIMIT 1
            """, arguments: [today, dayID]) {
            return existing
        }
        guard let planID = try Int64.fetchOne(db, sql: "SELECT `planId` FROM `routines` WHERE `id` = ?", arguments: [dayID]) else {
            throw KitaeDatabaseError.missingDay(dayID)
        }
        try db.execute(sql: """
            INSERT INTO `sessions` (`date`, `planId`, `routineId`, `isFinished`, `startedAt`)
            VALUES (?, ?, ?, 0, ?)
            """, arguments: [today, planID, dayID, now])
        let sessionID = db.lastInsertedRowID
        let planned = try Row.fetchAll(db, sql: """
            SELECT `routine_sets`.*, `routine_exercises`.`exerciseId`, `exercises`.`usesWeight`
            FROM `routine_sets`
            INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
            INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
            WHERE `routine_exercises`.`routineId` = ?
            ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
            `routine_sets`.`position` ASC, `routine_sets`.`id` ASC
            """, arguments: [dayID])
        for (order, set) in planned.enumerated() {
            try db.execute(sql: """
                INSERT INTO `sets` (`reps`, `weight`, `type`, `order`, `sessionId`, `exerciseId`, `rir`, `isCompleted`, `routineSetId`, `restSeconds`)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, arguments: [
                    set["reps"] as Int, (set["usesWeight"] as Bool) ? set["weight"] as Double : 0, set["type"] as String, order, sessionID,
                    set["exerciseId"] as Int64, defaultRir, set["id"] as Int64, set["restSeconds"] as Int?,
                ])
        }
        return sessionID
    }

    // MARK: Reading

    /// The newest workout that isn't finished, if it was started today or yesterday, so a
    /// workout that went past midnight still counts.
    static func active(_ db: Database, today: Int) throws -> ActiveSession? {
        try Row.fetchOne(db, sql: """
            SELECT `sessions`.*, `routines`.`name` AS `routineName`
            FROM `sessions`
            LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
            WHERE `sessions`.`date` >= ?
            AND `sessions`.`isFinished` = 0
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC
            LIMIT 1
            """, arguments: [today - 1]).map(ActiveSession.init(row:))
    }

    static func session(_ db: Database, id: Int64) throws -> ActiveSession? {
        try Row.fetchOne(db, sql: """
            SELECT `sessions`.*, `routines`.`name` AS `routineName`
            FROM `sessions`
            LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
            WHERE `sessions`.`id` = ?
            """, arguments: [id]).map(ActiveSession.init(row:))
    }

    /// The day of the newest workout that had something done.
    static func lastDoneDayID(_ db: Database) throws -> Int64? {
        try Int64.fetchOne(db, sql: """
            SELECT `routineId`
            FROM `sessions`
            WHERE `routineId` IS NOT NULL
            AND EXISTS
            (SELECT 1
            FROM `sets`
            WHERE `sets`.`sessionId` = `sessions`.`id`
            AND `sets`.`isCompleted` = 1)
            ORDER BY `date` DESC, `id` DESC
            LIMIT 1
            """)
    }

    /// Whether any set was ever done, so Today can tell a first workout apart.
    static func hasDoneSets(_ db: Database) throws -> Bool {
        try Bool.fetchOne(db, sql: "SELECT EXISTS (SELECT 1 FROM `sets` WHERE `isCompleted` = 1)") ?? false
    }

    /// A workout's exercises and sets. While it's going on, the day's exercises come first in
    /// the plan's order, even ones without sets, then any others in the order they were added.
    static func exercises(_ db: Database, sessionID: Int64, dayID: Int64?) throws -> [WorkoutExercise] {
        var order: [Int64] = []
        var groups: [Int64: WorkoutExercise] = [:]
        if let dayID {
            let planned = try Row.fetchAll(db, sql: """
                SELECT `exercises`.*
                FROM `routine_exercises`
                INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
                WHERE `routine_exercises`.`routineId` = ?
                ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC
                """, arguments: [dayID])
            for row in planned {
                let exercise = Exercise(row: row)
                if groups[exercise.id] == nil {
                    order.append(exercise.id)
                    groups[exercise.id] = WorkoutExercise(id: exercise.id, exercise: exercise, sets: [])
                }
            }
        }
        let rows = try Row.fetchAll(db, sql: """
            SELECT `sets`.`id` AS `setId`, `sets`.`reps`, `sets`.`weight`, `sets`.`type`,
            `sets`.`restSeconds` AS `setRestSeconds`, `sets`.`isCompleted`, `sets`.`routineSetId`,
            `exercises`.*
            FROM `sets`
            INNER JOIN `exercises` ON `exercises`.`id` = `sets`.`exerciseId`
            WHERE `sets`.`sessionId` = ?
            ORDER BY `sets`.`order` ASC, `sets`.`id` ASC
            """, arguments: [sessionID])
        for row in rows {
            let exercise = Exercise(row: row)
            if groups[exercise.id] == nil {
                order.append(exercise.id)
                groups[exercise.id] = WorkoutExercise(id: exercise.id, exercise: exercise, sets: [])
            }
            groups[exercise.id]?.sets.append(WorkoutSet(
                id: row["setId"],
                type: SetType(stored: row["type"]),
                reps: row["reps"],
                weightKg: row["weight"],
                restSeconds: row["setRestSeconds"],
                isDone: row["isCompleted"],
                isAdded: (row["routineSetId"] as Int64?) == nil
            ))
        }
        return order.compactMap { groups[$0] }
    }

    /// What a new set of an exercise starts as when there's nothing to copy in the workout:
    /// the day's last planned set, or the last set ever done, or nil.
    static func lastKnownSet(_ db: Database, exerciseID: Int64, dayID: Int64?) throws -> SetDraft? {
        if let dayID, let row = try Row.fetchOne(db, sql: """
            SELECT `routine_sets`.*
            FROM `routine_sets`
            INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
            WHERE `routine_exercises`.`routineId` = ?
            AND `routine_exercises`.`exerciseId` = ?
            ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
            `routine_sets`.`position` DESC, `routine_sets`.`id` DESC
            LIMIT 1
            """, arguments: [dayID, exerciseID]) {
            return SetDraft(row: row)
        }
        if let row = try Row.fetchOne(db, sql: """
            SELECT `sets`.*
            FROM `sets`
            INNER JOIN `sessions` ON `sets`.`sessionId` = `sessions`.`id`
            WHERE `sets`.`exerciseId` = ?
            AND `sets`.`isCompleted` = 1
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` DESC, `sets`.`id` DESC
            LIMIT 1
            """, arguments: [exerciseID]) {
            return SetDraft(row: row)
        }
        return nil
    }

    // MARK: Changing sets

    static func setDone(_ db: Database, setID: Int64, isDone: Bool) throws {
        try db.execute(sql: "UPDATE `sets` SET `isCompleted` = ? WHERE `id` = ?", arguments: [isDone, setID])
    }

    /// Saves a set's reps, weight, type and rest, keeping its exercise's warm-ups first.
    static func update(_ db: Database, set: WorkoutSet) throws {
        try db.execute(sql: """
            UPDATE `sets`
            SET `reps` = ?, `weight` = ?, `type` = ?, `restSeconds` = ?
            WHERE `id` = ?
            """, arguments: [set.reps, set.weightKg, set.type.rawValue, set.restSeconds, set.id])
        if let row = try Row.fetchOne(db, sql: "SELECT `sessionId`, `exerciseId` FROM `sets` WHERE `id` = ?", arguments: [set.id]) {
            try groupWarmups(db, sessionID: row["sessionId"], exerciseID: row["exerciseId"])
        }
    }

    /// Adds a set that isn't done yet after the workout's other sets, without weight if the
    /// exercise doesn't use any. Returns its id.
    @discardableResult
    static func addSet(_ db: Database, sessionID: Int64, exerciseID: Int64, set: SetDraft) throws -> Int64 {
        let usesWeight = try Bool.fetchOne(db, sql: "SELECT `usesWeight` FROM `exercises` WHERE `id` = ?", arguments: [exerciseID]) ?? true
        try db.execute(sql: """
            INSERT INTO `sets` (`reps`, `weight`, `type`, `order`, `sessionId`, `exerciseId`, `rir`, `isCompleted`, `restSeconds`)
            VALUES (?, ?, ?, (SELECT COALESCE(MAX(`order`), -1) + 1 FROM `sets` WHERE `sessionId` = ?), ?, ?, ?, 0, ?)
            """, arguments: [
                set.reps, usesWeight ? set.weightKg : 0, set.type.rawValue, sessionID, sessionID, exerciseID, defaultRir,
                set.restSeconds,
            ])
        let setID = db.lastInsertedRowID
        try groupWarmups(db, sessionID: sessionID, exerciseID: exerciseID)
        return setID
    }

    static func deleteSet(_ db: Database, setID: Int64) throws {
        try db.execute(sql: "DELETE FROM `sets` WHERE `id` = ?", arguments: [setID])
    }

    /// Moves an exercise's warm-ups in front of its other sets in a workout.
    static func groupWarmups(_ db: Database, sessionID: Int64, exerciseID: Int64) throws {
        let sets = try Row.fetchAll(db, sql: """
            SELECT `id`, `order`, `type`
            FROM `sets`
            WHERE `sessionId` = ?
            AND `exerciseId` = ?
            ORDER BY `order` ASC, `id` ASC
            """, arguments: [sessionID, exerciseID])
        let moves = warmupsFirstSlots(sets, slot: { $0["order"] as Int }, isWarmup: { $0["type"] as String == SetType.warmup.rawValue })
        for (set, order) in moves {
            try db.execute(sql: "UPDATE `sets` SET `order` = ? WHERE `id` = ?", arguments: [order, set["id"] as Int64])
        }
    }

    // MARK: Finishing

    /// Finishes a workout: what was done goes back into the day's plan, and the sets that
    /// weren't done are kept or removed as `mode` says. A workout left with no sets is removed.
    static func finish(_ db: Database, sessionID: Int64, mode: FinishMode, addNewSets: Bool, now: Int64) throws {
        try Plans.applyWorkout(db, sessionID: sessionID, addNewSets: addNewSets)
        if mode != .keepSkipped {
            if mode == .removeFromPlan {
                let slots = try Int64.fetchAll(db, sql: """
                    SELECT DISTINCT `routine_sets`.`routineExerciseId`
                    FROM `routine_sets`
                    WHERE `routine_sets`.`id` IN
                    (SELECT `routineSetId`
                    FROM `sets`
                    WHERE `sessionId` = ?
                    AND `isCompleted` = 0)
                    """, arguments: [sessionID])
                try db.execute(sql: """
                    DELETE FROM `routine_sets`
                    WHERE `id` IN
                    (SELECT `routineSetId`
                    FROM `sets`
                    WHERE `sessionId` = ?
                    AND `isCompleted` = 0
                    AND `routineSetId` IS NOT NULL)
                    """, arguments: [sessionID])
                if !slots.isEmpty {
                    try db.execute(sql: """
                        DELETE FROM `routine_exercises`
                        WHERE `id` IN (\(databaseQuestionMarks(count: slots.count)))
                        AND NOT EXISTS
                        (SELECT 1
                        FROM `routine_sets`
                        WHERE `routine_sets`.`routineExerciseId` = `routine_exercises`.`id`)
                        """, arguments: StatementArguments(slots))
                }
            }
            try db.execute(sql: "DELETE FROM `sets` WHERE `sessionId` = ? AND `isCompleted` = 0", arguments: [sessionID])
        }
        try db.execute(sql: """
            UPDATE `sessions`
            SET `isFinished` = 1, `finishedAt` = COALESCE(`finishedAt`, ?)
            WHERE `id` = ?
            """, arguments: [now, sessionID])
        try db.execute(sql: """
            DELETE FROM `sessions`
            WHERE `id` = ?
            AND NOT EXISTS
            (SELECT 1
            FROM `sets`
            WHERE `sets`.`sessionId` = ?)
            """, arguments: [sessionID, sessionID])
    }

    /// Removes a workout and all its sets. The plan stays as it was.
    static func discard(_ db: Database, sessionID: Int64) throws {
        try db.execute(sql: "DELETE FROM `sessions` WHERE `id` = ?", arguments: [sessionID])
    }
}

nonisolated extension SetDraft {
    /// The values of a row from `sets` or `routine_sets`.
    init(row: Row) {
        self.init(reps: row["reps"], weightKg: row["weight"], type: SetType(stored: row["type"]), restSeconds: row["restSeconds"])
    }
}

nonisolated extension ActiveSession {
    init(row: Row) {
        self.init(
            id: row["id"],
            planID: row["planId"],
            dayID: row["routineId"],
            dayName: row["routineName"],
            startedAt: row["startedAt"],
            date: row["date"]
        )
    }
}
