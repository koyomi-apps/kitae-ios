// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

/// What a new database starts with: Kitae for Android's exercises, with the same ids, and its
/// starter plan, and the exercises of Kitae's library (see `ExerciseLibrary`). `Seed.json` is
/// made from Kitae for Android's starter database by `scripts/make_seed.py`.
nonisolated struct Seed: Decodable, Sendable {
    /// One of Kitae's exercises.
    nonisolated struct Exercise: Decodable, Sendable {
        /// The starter exercises' ids, as in Kitae for Android. Exercises added to the library
        /// later have none: a database gives them the next free one.
        var id: Int64?
        /// Its library key, which never changes (`exercises.libraryKey`).
        var key: String
        var name: String
        var target: String
        var isIsometric: Bool
        var reference: String?
        var restSeconds: Int?
        var instructions: String?

        /// Adds it to the `exercises` table, with its id if it has one.
        func insert(_ db: Database) throws {
            try db.execute(
                sql: """
                INSERT INTO `exercises` (`id`, `name`, `target`, `reference`, `isIsometric`, `restSeconds`, `instructions`, `libraryKey`)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                arguments: [id, name, target, reference, isIsometric, restSeconds, instructions, key]
            )
        }
    }

    nonisolated struct Plan: Decodable, Sendable {
        var name: String
        var description: String?
        var difficulty: String?
        var focus: String?
        var equipment: String?
        var time: String?
        var days: [Day]
    }

    nonisolated struct Day: Decodable, Sendable {
        var name: String
        var exercises: [DayExercise]
    }

    nonisolated struct DayExercise: Decodable, Sendable {
        var exercise: Int64
        var sets: [PlannedSet]
    }

    nonisolated struct PlannedSet: Decodable, Sendable {
        var reps: Int
        var weight: Double
        var type: String
        var restSeconds: Int?
    }

    var exercises: [Exercise]
    var plans: [Plan]

    /// The starter exercises' library keys, by id.
    var starterKeys: [Int64: String] {
        Dictionary(exercises.compactMap { exercise in exercise.id.map { ($0, exercise.key) } },
                   uniquingKeysWith: { first, _ in first })
    }

    static func bundled() throws -> Seed {
        guard let url = Bundle.main.url(forResource: "Seed", withExtension: "json") else {
            throw KitaeDatabaseError.missingSeed
        }
        return try JSONDecoder().decode(Seed.self, from: Data(contentsOf: url))
    }

    /// Adds the starter exercises and plans. Returns the id of the first plan. The library's
    /// other exercises come with `ExerciseLibrary.sync`.
    @discardableResult
    func insert(_ db: Database) throws -> Int64? {
        for exercise in exercises where exercise.id != nil {
            try exercise.insert(db)
        }
        var firstPlanID: Int64?
        for plan in plans {
            try db.execute(
                sql: """
                INSERT INTO `plans` (`name`, `description`, `difficulty`, `focus`, `equipment`, `time`)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                arguments: [plan.name, plan.description, plan.difficulty, plan.focus, plan.equipment, plan.time]
            )
            let planID = db.lastInsertedRowID
            firstPlanID = firstPlanID ?? planID
            for (dayPosition, day) in plan.days.enumerated() {
                try db.execute(
                    sql: "INSERT INTO `routines` (`planId`, `name`, `position`) VALUES (?, ?, ?)",
                    arguments: [planID, day.name, dayPosition]
                )
                let routineID = db.lastInsertedRowID
                for (exercisePosition, item) in day.exercises.enumerated() {
                    try db.execute(
                        sql: "INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`) VALUES (?, ?, ?)",
                        arguments: [routineID, item.exercise, exercisePosition]
                    )
                    let slotID = db.lastInsertedRowID
                    for (setPosition, set) in item.sets.enumerated() {
                        try db.execute(
                            sql: """
                            INSERT INTO `routine_sets` (`routineExerciseId`, `reps`, `weight`, `type`, `position`, `restSeconds`)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                            arguments: [slotID, set.reps, set.weight, set.type, setPosition, set.restSeconds]
                        )
                    }
                }
            }
        }
        return firstPlanID
    }
}
