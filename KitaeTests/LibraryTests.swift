// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Kitae's own exercises: their library keys, how databases from before them get them, and how
/// the exercises a later version brings arrive.
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

    private let landminePress = Seed.Exercise(
        id: nil, key: "landmine-press", name: "Landmine Press", target: "Shoulders", isIsometric: false,
        instructions: "Hold the end of the bar at your shoulder.\nPress it up and forward."
    )

    @Test func theStarterExercisesHaveTheirKeys() throws {
        let database = try KitaeDatabase.inMemory()
        let starters = try Seed.bundled().starterKeys
        #expect(starters.count == 75)
        #expect(try keys(database) == starters)
        #expect(try database.read { db in try Exercises.exercise(db, id: 21) }?.libraryKey == "barbell-bench-press")
        let given = try database.read { db in try String.fetchAll(db, sql: "SELECT key FROM exercise_library") }
        #expect(Set(given) == Set(starters.values))
        // Every animation, muscle map and energy belongs to one of them.
        let all = Set(starters.values)
        #expect(Set(ExerciseMotions.byKey.keys) == all)
        #expect(Set(ExerciseMuscles.byKey.keys) == all)
        #expect(Set(ExerciseEnergy.starters.keys) == all)
    }

    @Test func aVersion8DatabaseIsBroughtUpToDate() throws {
        let queue = try DatabaseQueue()
        try KitaeDatabase.makeVersion8(queue)
        try queue.write { db in
            // Leg Press was deleted, Curls renamed, and someone made an exercise of their own.
            try db.execute(sql: "DELETE FROM exercises WHERE id = 12")
            try db.execute(sql: "UPDATE exercises SET name = 'Dumbbell Curls' WHERE id = 1")
            try db.execute(sql: "INSERT INTO exercises (name, target, isIsometric) VALUES ('Landmine Press', 'Shoulders', 0)")
        }
        let database = try KitaeDatabase(queue)
        let (version, hash, index) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "PRAGMA user_version"),
                try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_exercises_libraryKey'")
            )
        }
        #expect(version == Schema.version && hash == Schema.identityHash && index == 1)
        let keys = try keys(database)
        #expect(keys[1] == "dumbbell-curls" && keys[12] == nil && keys.count == 74)
        // Leg Press stays deleted, and the exercise people made stays theirs.
        #expect(try count(database, "SELECT COUNT(*) FROM exercises") == 75)
        #expect(try count(database, "SELECT COUNT(*) FROM exercise_library") == 75)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises WHERE name = 'Landmine Press' AND libraryKey IS NULL") == 1)
        // Opening it again changes nothing.
        _ = try KitaeDatabase(queue)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises") == 75)
    }

    @Test func exercisesALaterVersionBringsArriveOnce() throws {
        let database = try KitaeDatabase.inMemory()
        #expect(try database.write { db in try ExerciseLibrary.sync(db, library: [landminePress]) } == 1)
        let added = try #require(try database.read { db in
            try Row.fetchOne(db, sql: "SELECT * FROM exercises WHERE libraryKey = 'landmine-press'").map(Exercise.init(row:))
        })
        #expect(added.id > 75 && added.name == "Landmine Press" && added.steps.count == 2)
        // Once deleted, it doesn't come back.
        try database.write { db in try Exercises.delete(db, id: added.id) }
        #expect(try database.write { db in try ExerciseLibrary.sync(db, library: [landminePress]) } == 0)
        #expect(try count(database, "SELECT COUNT(*) FROM exercises WHERE name = 'Landmine Press'") == 0)
    }

    @Test func anExerciseWithItsNameBecomesKitaes() throws {
        let database = try KitaeDatabase.inMemory()
        let id = try database.write { db in
            try Exercises.create(db, ExerciseDraft(name: "landmine press", muscle: .shoulders))
        }
        #expect(try database.write { db in try ExerciseLibrary.sync(db, library: [landminePress]) } == 0)
        let exercise = try #require(try database.read { db in try Exercises.exercise(db, id: id) })
        #expect(exercise.libraryKey == "landmine-press" && exercise.name == "landmine press")
        #expect(try count(database, "SELECT COUNT(*) FROM exercises") == 76)
    }

    @Test func aVersion8BackupIsBroughtUpToDate() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("library-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appendingPathComponent("kenko_database")
        let old = try DatabaseQueue(path: file.path)
        try KitaeDatabase.makeVersion8(old)
        try old.close()
        let zip = folder.appendingPathComponent("backup.zip")
        try Backups.archive([("kenko_database", file)], to: zip)

        let target = try KitaeDatabase.inMemory()
        _ = try Backups.restore(from: zip, into: target)
        #expect(try count(target, "PRAGMA user_version") == Schema.version)
        #expect(try keys(target) == Seed.bundled().starterKeys)
    }
}
