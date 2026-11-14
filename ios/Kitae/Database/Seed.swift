// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// What a new database starts with: Kitae for Android's exercises, with the same ids, and its
/// starter plan, and the exercises of Kitae's library (`ExerciseLibrary.kt`). `Seed.json` is
/// made from Kitae for Android's starter database by `scripts/make_seed.py`; the library's
/// later exercises, which have no id, are written in it by hand.
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
        /// False for exercises done with the body alone, like sit-ups: their sets are reps (or
        /// seconds) without weight.
        var usesWeight: Bool
        var reference: String?
        var restSeconds: Int?
        var instructions: String?

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

    /// The library keys of the exercises that don't use weight.
    var weightlessKeys: Set<String> {
        Set(exercises.filter { !$0.usesWeight }.map(\.key))
    }

    /// The text of `Seed.json`, which the shared module reads as each database opens.
    static func bundledText() throws -> String {
        guard let url = Bundle.main.url(forResource: "Seed", withExtension: "json") else {
            throw KitaeDatabaseError.missingSeed
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    static func bundled() throws -> Seed {
        guard let url = Bundle.main.url(forResource: "Seed", withExtension: "json") else {
            throw KitaeDatabaseError.missingSeed
        }
        return try JSONDecoder().decode(Seed.self, from: Data(contentsOf: url))
    }

}
