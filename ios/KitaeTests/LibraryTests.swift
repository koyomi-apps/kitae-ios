// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// Kitae's own exercises: their library keys, and how databases from before them get them. How
/// the exercises a later version brings arrive is checked in the shared module (`LibraryTest`).
struct LibraryTests {
    private func keys(_ database: KitaeDatabase) throws -> [Int64: String] {
        try database.read { db in
            let rows = try Row.fetchAll(db, sql: "SELECT id, libraryKey FROM exercises WHERE libraryKey IS NOT NULL")
            return Dictionary(uniqueKeysWithValues: rows.map { ($0["id"] as Int64, $0["libraryKey"] as String) })
        }
    }

    private func count(_ database: KitaeDatabase, _ sql: String) throws -> Int {
        try database.read { db in try Int.fetchOne(db, sql: sql) ?? 0 }
    }

    @Test func kitaesExercisesHaveTheirKeys() throws {
        let database = try KitaeDatabase.temporary()
        let seed = try Seed.bundled()
        let all = Set(seed.exercises.map(\.key))
        let starters = seed.starterKeys
        #expect(starters.count == 75 && all.count == seed.exercises.count)
        // The starters have Kitae for Android's ids, and the library's later exercises the next ones.
        let keys = try keys(database)
        #expect(keys.filter { $0.key <= 75 } == starters)
        #expect(Set(keys.values) == all && keys.count == all.count)
        #expect(try database.read { db in try Exercises.exercise(db, id: 21) }?.libraryKey == "barbell-bench-press")
        let given = try database.read { db in try String.fetchAll(db, sql: "SELECT key FROM exercise_library") }
        #expect(Set(given) == all)
        // Sit-ups and the other exercises done with the body alone don't use weight, and the
        // starter plan doesn't give their sets any.
        let weightless = try database.read { db in try String.fetchAll(db, sql: "SELECT libraryKey FROM exercises WHERE usesWeight = 0") }
        #expect(Set(weightless) == seed.weightlessKeys && weightless.contains("sit-ups"))
        #expect(try count(database, """
            SELECT COUNT(*) FROM routine_sets
            JOIN routine_exercises ON routine_exercises.id = routine_sets.routineExerciseId
            JOIN exercises ON exercises.id = routine_exercises.exerciseId
            WHERE exercises.usesWeight = 0 AND routine_sets.weight > 0
            """) == 0)
        // Each of them has its animation, muscle map and energy.
        #expect(Set(ExerciseMotions.byKey.keys) == all)
        #expect(Set(ExerciseMuscles.byKey.keys) == all)
        #expect(Set(ExerciseEnergy.starters.keys) == all)
    }

    @Test func aVersion8DatabaseIsBroughtUpToDate() throws {
        let file = try KitaeDatabase.newFile()
        try KitaeDatabase.makeVersion8(at: file)
        // Leg Press was deleted, Curls renamed, and someone made an exercise of their own.
        try KitaeDatabase.execute([
            "DELETE FROM exercises WHERE id = 12",
            "UPDATE exercises SET name = 'Dumbbell Curls' WHERE id = 1",
            "INSERT INTO exercises (name, target, isIsometric) VALUES ('Example Press', 'Shoulders', 0)",
        ], in: file)
        let database = try KitaeDatabase.file(file)
        let seed = try Seed.bundled()
        let (version, hash, index) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "PRAGMA user_version"),
                try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_exercises_libraryKey'")
            )
        }
        #expect(version == Schema.version && hash == Schema.identityHash && index == 1)
        let keys = try keys(database)
        // Leg Press stays deleted, and the library's later exercises arrive.
        #expect(keys[1] == "dumbbell-curls" && keys[12] == nil && keys.count == seed.exercises.count - 1)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises WHERE usesWeight = 0") == seed.weightlessKeys.count)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises") == seed.exercises.count)
        #expect(try count(database, "SELECT COUNT(*) FROM exercise_library") == seed.exercises.count)
        // The exercise people made stays theirs.
        #expect(try count(database, "SELECT COUNT(*) FROM exercises WHERE name = 'Example Press' AND libraryKey IS NULL") == 1)
        // Opening it again changes nothing.
        try database.close()
        #expect(try count(KitaeDatabase.file(file), "SELECT COUNT(*) FROM exercises") == seed.exercises.count)
    }

    @Test func aVersion9DatabaseLearnsWhichExercisesUseWeight() throws {
        let file = try KitaeDatabase.newFile()
        try KitaeDatabase.makeVersion9(at: file)
        // Leg raises done with the 20 kg of Kitae for Android's starter plan, and someone's own
        // exercise.
        try KitaeDatabase.execute([
            "INSERT INTO sessions (date, isFinished) VALUES (20000, 1)",
            """
            INSERT INTO sets (reps, weight, type, `order`, sessionId, exerciseId, rir)
            VALUES (12, 20, 'Standard', 0, last_insert_rowid(), 20, 2)
            """,
            "INSERT INTO exercises (name, target, isIsometric) VALUES ('Weighted Dips', 'Triceps', 0)",
        ], in: file)
        let database = try KitaeDatabase.file(file)
        let (version, hash) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "PRAGMA user_version"),
                try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42")
            )
        }
        #expect(version == Schema.version && hash == Schema.identityHash)
        let weightless = try database.read { db in try String.fetchAll(db, sql: "SELECT libraryKey FROM exercises WHERE usesWeight = 0") }
        #expect(try Set(weightless) == Seed.bundled().weightlessKeys)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises WHERE name = 'Weighted Dips' AND usesWeight = 1") == 1)
        // The set keeps its 20 kg, but leg raises' energy leaves it out.
        #expect(try count(database, "SELECT COUNT(*) FROM sets WHERE exerciseId = 20 AND weight = 20") == 1)
        let done = try database.read { db in try Sessions.doneSets(db, from: 20000, to: 20000) }
        #expect(done.values.flatMap { $0 }.map(\.weightKg) == [0])
    }

    @Test func aVersion8BackupIsBroughtUpToDate() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("library-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appendingPathComponent("kenko_database")
        try KitaeDatabase.makeVersion8(at: file)
        let zip = folder.appendingPathComponent("backup.zip")
        try Backups.archive([("kenko_database", file)], to: zip)

        let target = try KitaeDatabase.temporary()
        _ = try Backups.restore(from: zip, into: target)
        #expect(try count(target, "PRAGMA user_version") == Schema.version)
        #expect(try keys(target).filter { $0.key <= 75 } == Seed.bundled().starterKeys)
    }
}
