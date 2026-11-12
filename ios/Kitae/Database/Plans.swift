// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import GRDB

/// Plans and their days, read and written the way Kitae for Android does
/// (its `PlanDao`, `PlanHistoryDao` and `RoutineDao`). A day is a routine in the database.
nonisolated enum Plans {
    /// Every plan with how many days and exercises it has, oldest first.
    static func all(_ db: Database) throws -> [PlanSummary] {
        try Row.fetchAll(db, sql: """
            SELECT `plans`.`id`, `plans`.`name`,
            (SELECT COUNT(*)
            FROM `routines`
            WHERE `routines`.`planId` = `plans`.`id`) AS `dayCount`,
            (SELECT COUNT(DISTINCT `routine_exercises`.`exerciseId`)
            FROM `routine_exercises`
            INNER JOIN `routines` ON `routines`.`id` = `routine_exercises`.`routineId`
            WHERE `routines`.`planId` = `plans`.`id`) AS `exerciseCount`
            FROM `plans`
            ORDER BY `plans`.`id` ASC
            """).map { row in
            PlanSummary(id: row["id"], name: row["name"], dayCount: row["dayCount"], exerciseCount: row["exerciseCount"])
        }
    }

    /// The plan being followed: the one whose plan history row hasn't ended.
    static func currentID(_ db: Database) throws -> Int64? {
        try Int64.fetchOne(db, sql: """
            SELECT `planId`
            FROM `plan_history`
            WHERE `end` IS NULL
            AND `start` IS NOT NULL
            """)
    }

    /// Ends the current plan today and starts following `planID`.
    static func setCurrent(_ db: Database, planID: Int64, today: Int) throws {
        let current = try Row.fetchOne(db, sql: """
            SELECT `id`, `planId`
            FROM `plan_history`
            WHERE `end` IS NULL
            AND `start` IS NOT NULL
            """)
        if let current {
            if current["planId"] as Int64? == planID { return }
            try db.execute(sql: "UPDATE `plan_history` SET `end` = ? WHERE `id` = ?", arguments: [today, current["id"] as Int64])
        }
        try db.execute(sql: "INSERT INTO `plan_history` (`planId`, `start`) VALUES (?, ?)", arguments: [planID, today])
    }

    /// A plan with its days in order, each with its exercises and planned sets.
    static func plan(_ db: Database, id: Int64) throws -> Plan? {
        guard let name = try String.fetchOne(db, sql: "SELECT `name` FROM `plans` WHERE `id` = ?", arguments: [id]) else {
            return nil
        }
        let routines = try Row.fetchAll(db, sql: """
            SELECT `id`, `name`
            FROM `routines`
            WHERE `planId` = ?
            ORDER BY `position` ASC, `id` ASC
            """, arguments: [id])
        let days = try routines.map { row in
            TrainingDay(id: row["id"], name: row["name"], exercises: try dayExercises(db, dayID: row["id"]))
        }
        return Plan(id: id, name: name, days: days)
    }

    static func dayExercises(_ db: Database, dayID: Int64) throws -> [DayExercise] {
        let slots = try Row.fetchAll(db, sql: """
            SELECT `routine_exercises`.`id` AS `slotId`, `exercises`.*
            FROM `routine_exercises`
            INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
            WHERE `routine_exercises`.`routineId` = ?
            ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC
            """, arguments: [dayID])
        var sets: [Int64: [PlannedSet]] = [:]
        let rows = try Row.fetchAll(db, sql: """
            SELECT `routine_sets`.*
            FROM `routine_sets`
            INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
            WHERE `routine_exercises`.`routineId` = ?
            ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
            `routine_sets`.`position` ASC, `routine_sets`.`id` ASC
            """, arguments: [dayID])
        for row in rows {
            sets[row["routineExerciseId"], default: []].append(PlannedSet(
                id: row["id"],
                type: SetType(stored: row["type"]),
                reps: row["reps"],
                weightKg: row["weight"],
                restSeconds: row["restSeconds"]
            ))
        }
        return slots.map { row in
            let slotID: Int64 = row["slotId"]
            return DayExercise(id: slotID, exercise: Exercise(row: row), sets: sets[slotID] ?? [])
        }
    }

    /// The day after the one done last, going back to the first after the last day.
    /// Kitae for Android's `nextAfter`.
    static func dayAfter(_ lastDayID: Int64?, in days: [TrainingDay]) -> TrainingDay? {
        guard !days.isEmpty else { return nil }
        guard let index = days.firstIndex(where: { $0.id == lastDayID }) else { return days.first }
        return days[(index + 1) % days.count]
    }

    // MARK: Taking a workout back into the plan

    /// Copies what was done in a workout back into its day, so next time starts from there.
    /// With `addNewSets`, sets added during the workout join the day too. Kitae for Android's
    /// `RoutineDao.applyWorkout`.
    static func applyWorkout(_ db: Database, sessionID: Int64, addNewSets: Bool) throws {
        try db.execute(sql: """
            UPDATE `routine_sets` SET
            `reps` =
            (SELECT `sets`.`reps`
            FROM `sets`
            WHERE `sets`.`routineSetId` = `routine_sets`.`id`
            AND `sets`.`sessionId` = :sessionId
            AND `sets`.`isCompleted` = 1),
            `weight` =
            (SELECT `sets`.`weight`
            FROM `sets`
            WHERE `sets`.`routineSetId` = `routine_sets`.`id`
            AND `sets`.`sessionId` = :sessionId
            AND `sets`.`isCompleted` = 1),
            `type` =
            (SELECT `sets`.`type`
            FROM `sets`
            WHERE `sets`.`routineSetId` = `routine_sets`.`id`
            AND `sets`.`sessionId` = :sessionId
            AND `sets`.`isCompleted` = 1),
            `restSeconds` =
            (SELECT `sets`.`restSeconds`
            FROM `sets`
            WHERE `sets`.`routineSetId` = `routine_sets`.`id`
            AND `sets`.`sessionId` = :sessionId
            AND `sets`.`isCompleted` = 1)
            WHERE `id` IN
            (SELECT `routineSetId`
            FROM `sets`
            WHERE `sessionId` = :sessionId
            AND `isCompleted` = 1
            AND `routineSetId` IS NOT NULL)
            """, arguments: ["sessionId": sessionID])

        let touched = try Int64.fetchAll(db, sql: """
            SELECT DISTINCT `routine_sets`.`routineExerciseId`
            FROM `routine_sets`
            INNER JOIN `sets` ON `sets`.`routineSetId` = `routine_sets`.`id`
            WHERE `sets`.`sessionId` = ?
            AND `sets`.`isCompleted` = 1
            """, arguments: [sessionID])
        for slotID in touched {
            try groupWarmups(db, slotID: slotID)
        }

        guard addNewSets,
              let routineID = try Int64.fetchOne(db, sql: "SELECT `routineId` FROM `sessions` WHERE `id` = ?", arguments: [sessionID])
        else { return }

        let newSets = try Row.fetchAll(db, sql: """
            SELECT *
            FROM `sets`
            WHERE `sessionId` = ?
            AND `isCompleted` = 1
            AND `routineSetId` IS NULL
            ORDER BY `order` ASC, `id` ASC
            """, arguments: [sessionID])
        var exerciseOrder: [Int64] = []
        var byExercise: [Int64: [Row]] = [:]
        for row in newSets {
            let exerciseID: Int64 = row["exerciseId"]
            if byExercise[exerciseID] == nil { exerciseOrder.append(exerciseID) }
            byExercise[exerciseID, default: []].append(row)
        }
        for exerciseID in exerciseOrder {
            let slotID = try slot(db, routineID: routineID, exerciseID: exerciseID)
            for set in byExercise[exerciseID] ?? [] {
                try db.execute(sql: """
                    INSERT INTO `routine_sets` (`routineExerciseId`, `reps`, `weight`, `type`, `position`, `restSeconds`)
                    VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_sets` WHERE `routineExerciseId` = ?), ?)
                    """, arguments: [slotID, set["reps"] as Int, set["weight"] as Double, set["type"] as String, slotID, set["restSeconds"] as Int?])
                try db.execute(
                    sql: "UPDATE `sets` SET `routineSetId` = ? WHERE `id` = ?",
                    arguments: [db.lastInsertedRowID, set["id"] as Int64]
                )
            }
            try groupWarmups(db, slotID: slotID)
        }
    }

    /// The exercise's first place in the day, added at the end if it isn't there yet.
    private static func slot(_ db: Database, routineID: Int64, exerciseID: Int64) throws -> Int64 {
        if let existing = try Int64.fetchOne(db, sql: """
            SELECT `id`
            FROM `routine_exercises`
            WHERE `routineId` = ?
            AND `exerciseId` = ?
            ORDER BY `position` ASC, `id` ASC
            LIMIT 1
            """, arguments: [routineID, exerciseID]) {
            return existing
        }
        try db.execute(sql: """
            INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`)
            VALUES (?, ?, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_exercises` WHERE `routineId` = ?))
            """, arguments: [routineID, exerciseID, routineID])
        return db.lastInsertedRowID
    }

    /// Moves an exercise's planned warm-ups in front of its other sets.
    static func groupWarmups(_ db: Database, slotID: Int64) throws {
        let sets = try Row.fetchAll(db, sql: """
            SELECT `id`, `position`, `type`
            FROM `routine_sets`
            WHERE `routineExerciseId` = ?
            ORDER BY `position` ASC, `id` ASC
            """, arguments: [slotID])
        let moves = warmupsFirstSlots(sets, slot: { $0["position"] as Int }, isWarmup: { $0["type"] as String == SetType.warmup.rawValue })
        for (set, position) in moves {
            try db.execute(sql: "UPDATE `routine_sets` SET `position` = ? WHERE `id` = ?", arguments: [position, set["id"] as Int64])
        }
    }
}
