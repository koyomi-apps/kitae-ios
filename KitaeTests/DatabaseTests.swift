// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// The database has to stay exactly like Kitae for Android's, so backups open in both apps.
/// `RoomSchema10.json` is Room's schema file for version 10, written as Room writes it once
/// Kitae for Android's exercise entity has `libraryKey` (version 9) and `usesWeight`.
/// `RoomSchema9.json` is version 9's, written the same way, and `RoomSchema8.json` version 8's,
/// copied from Kitae for Android; they make databases from before.
struct SchemaTests {
    struct RoomSchema: Decodable {
        struct Database: Decodable {
            var version: Int
            var identityHash: String
            var entities: [Entity]
            var setupQueries: [String]
        }

        struct Entity: Decodable {
            var tableName: String
            var createSql: String
            var indices: [Index]?
        }

        struct Index: Decodable {
            var name: String
            var createSql: String
        }

        var database: Database
    }

    private func roomSchema() throws -> RoomSchema.Database {
        let url = try #require(Bundle(for: TestBundle.self).url(forResource: "RoomSchema10", withExtension: "json"))
        return try JSONDecoder().decode(RoomSchema.self, from: Data(contentsOf: url)).database
    }

    /// SQLite keeps a statement without its IF NOT EXISTS.
    private func stored(_ createSql: String, table: String) -> String {
        createSql
            .replacingOccurrences(of: "${TABLE_NAME}", with: table)
            .replacingOccurrences(of: " IF NOT EXISTS", with: "")
    }

    @Test func tablesAndIndicesAreRooms() throws {
        let room = try roomSchema()
        let database = try KitaeDatabase.inMemory()
        let objects = try database.read { db in
            try Row.fetchAll(db, sql: "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")
        }
        var expected: [String: String] = [:]
        for entity in room.entities {
            expected[entity.tableName] = stored(entity.createSql, table: entity.tableName)
            for index in entity.indices ?? [] {
                expected[index.name] = stored(index.createSql, table: entity.tableName)
            }
        }
        expected["room_master_table"] = "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
        // Kitae's own tables, which Room on Android leaves alone.
        for statement in BodyTables.statements + ExerciseLibrary.statements {
            let name = try #require(statement.firstMatch(of: #/`(\w+)`/#)?.1)
            expected[String(name)] = statement.replacingOccurrences(of: " IF NOT EXISTS", with: "")
        }

        var found: [String: String] = [:]
        for object in objects {
            let name: String = object["name"]
            let sql: String? = object["sql"]
            found[name] = sql
        }
        #expect(found == expected)
    }

    @Test func versionAndIdentityAreRooms() throws {
        let room = try roomSchema()
        #expect(room.version == Schema.version)
        #expect(room.identityHash == Schema.identityHash)
        #expect(room.setupQueries.last?.contains(Schema.identityHash) == true)

        let database = try KitaeDatabase.inMemory()
        let (version, hash) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "PRAGMA user_version"),
                try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42")
            )
        }
        #expect(version == 10)
        #expect(hash == room.identityHash)
    }

    @Test func everySetTypeHasARating() throws {
        let database = try KitaeDatabase.inMemory()
        let rows = try database.read { db in
            try Row.fetchAll(db, sql: "SELECT type, modifier FROM set_type ORDER BY type")
        }
        let modifiers = Dictionary(uniqueKeysWithValues: rows.map { ($0["type"] as String, $0["modifier"] as Double) })
        #expect(Set(modifiers.keys) == Set(SetType.allCases.map(\.rawValue)))
        #expect(modifiers["Drop"] == Double(Float(1.35)))
        #expect(modifiers["Warmup"] == 0)
    }

    @Test func refusesADatabaseFromAnotherVersion() throws {
        let queue = try DatabaseQueue()
        try queue.write { db in try db.execute(sql: "PRAGMA user_version = 11") }
        #expect(throws: KitaeDatabaseError.self) { try KitaeDatabase(queue) }
    }

    @Test func opensItsOwnDatabaseAgain() throws {
        let queue = try DatabaseQueue()
        _ = try KitaeDatabase(queue)
        let again = try KitaeDatabase(queue)
        let exercises = try again.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM exercises") }
        #expect(exercises == 75)
    }
}

/// Finds the test bundle's resources.
private final class TestBundle {}

struct SeedTests {
    static let today = 20_736

    @Test func startsWithAndroidsExercises() throws {
        let database = try KitaeDatabase.inMemory()
        let (count, firstID, lastID, withSteps) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM exercises") ?? 0,
                try Int64.fetchOne(db, sql: "SELECT MIN(id) FROM exercises"),
                try Int64.fetchOne(db, sql: "SELECT MAX(id) FROM exercises"),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM exercises WHERE instructions IS NOT NULL") ?? 0
            )
        }
        #expect(count == 75)
        #expect(firstID == 1)
        #expect(lastID == 75)
        #expect(withSteps == 75)
    }

    @Test func noPlanIsFollowedAtFirst() throws {
        let database = try KitaeDatabase.inMemory()
        let (current, history, plans) = try database.read { db in
            (
                try Plans.currentID(db),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM plan_history"),
                try Plans.all(db)
            )
        }
        #expect(current == nil)
        #expect(history == 0)
        #expect(plans.map(\.name) == ["Push Pull Leg"])
        #expect(plans.first?.dayCount == 5)
        // Tricep Push Down is in two of the days.
        #expect(plans.first?.exerciseCount == 23)
        #expect(plans.first?.sizeLabel == "5 days · 23 exercises")
    }

    @Test func followsTheStarterPlanOnceChosen() throws {
        let database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        let (plan, history) = try database.read { db in
            (
                try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) },
                try Row.fetchAll(db, sql: "SELECT * FROM plan_history")
            )
        }
        let current = try #require(plan)
        #expect(current.name == "Push Pull Leg")
        #expect(current.days.map(\.name) == ["Push A", "Pull A", "Legs", "Push B", "Pull B"])
        #expect(current.days.reduce(0) { $0 + $1.exercises.count } == 24)
        #expect(current.days.reduce(0) { $0 + $1.setCount } == 72)
        #expect(current.days[0].exercises.map(\.exercise.name) == ["Incline Bench", "Chest Fly", "Leg Raises", "Overhead Extensions", "Tricep Push Down"])
        #expect(history.count == 1)
        let row = try #require(history.first)
        #expect(row["start"] as Int? == Self.today)
        #expect(row["end"] as Int? == nil)
    }

    @Test func switchingPlansEndsTheCurrentOne() throws {
        let database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        let rows = try database.write { db in
            try db.execute(sql: "INSERT INTO plans (name) VALUES ('Upper Lower')")
            try Plans.setCurrent(db, planID: db.lastInsertedRowID, today: Self.today + 3)
            return try Row.fetchAll(db, sql: "SELECT planId, start, `end` FROM plan_history ORDER BY id")
        }
        #expect(rows.count == 2)
        #expect(rows[0]["end"] as Int? == Self.today + 3)
        #expect(rows[1]["start"] as Int? == Self.today + 3)
        #expect(rows[1]["end"] as Int? == nil)
        let current = try database.read { db in try Plans.currentID(db) }
        #expect(current == rows[1]["planId"] as Int64?)
    }
}

struct EpochDayTests {
    let toronto = TimeZone(identifier: "America/Toronto")!

    @Test func countsDaysFrom1970() {
        #expect(EpochDay.of(Date(timeIntervalSince1970: 0), in: .gmt) == 0)
        #expect(EpochDay.of(Date(timeIntervalSince1970: -1), in: .gmt) == -1)
    }

    @Test func usesThePhonesTimeZone() {
        // 03:30 on 10 October in UTC is still 9 October in Toronto.
        let lateEvening = Date(timeIntervalSince1970: 1_791_603_000)
        #expect(EpochDay.of(lateEvening, in: .gmt) == 20_736)
        #expect(EpochDay.of(lateEvening, in: toronto) == 20_735)
    }

    @Test func turnsBackIntoLocalMidnight() {
        let midnight = EpochDay.date(20_735, in: toronto)
        #expect(EpochDay.of(midnight, in: toronto) == 20_735)
        #expect(EpochDay.of(midnight.addingTimeInterval(-1), in: toronto) == 20_734)
    }

    @Test func millisecondsRoundTrip() {
        let date = Date(timeIntervalSince1970: 1_791_603_000.25)
        #expect(date.epochMilliseconds == 1_791_603_000_250)
        #expect(Date(epochMilliseconds: date.epochMilliseconds) == date)
    }
}

struct WarmupOrderTests {
    @Test func warmupsMoveToTheFront() {
        let sets: [(slot: Int, isWarmup: Bool)] = [(0, false), (1, true), (2, false), (3, true)]
        let moves = warmupsFirstSlots(sets, slot: { $0.slot }, isWarmup: { $0.isWarmup })
        #expect(moves.map { $0.item.slot } == [1, 3, 0, 2])
        #expect(moves.map { $0.slot } == [0, 1, 2, 3])
    }

    @Test func nothingMovesWhenWarmupsAreFirst() {
        let sets: [(slot: Int, isWarmup: Bool)] = [(4, true), (7, false), (9, false)]
        #expect(warmupsFirstSlots(sets, slot: { $0.slot }, isWarmup: { $0.isWarmup }).isEmpty)
    }
}
