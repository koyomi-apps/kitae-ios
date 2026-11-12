// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

extension KitaeDatabase {
    /// A new database in memory that follows the starter plan from `today`, as if it was
    /// chosen on first launch.
    static func followingStarterPlan(today: Int = 20_736) throws -> KitaeDatabase {
        let database = try KitaeDatabase.inMemory()
        try database.write { db in
            guard let planID = try Int64.fetchOne(db, sql: "SELECT id FROM plans ORDER BY id LIMIT 1") else {
                throw KitaeDatabaseError.missingSeed
            }
            try Plans.setCurrent(db, planID: planID, today: today)
        }
        return database
    }
}

extension KitaeDatabase {
    /// Makes `queue` a database as Kitae made it before library keys: Room's version 8 tables,
    /// from its schema file, with the starter exercises.
    static func makeVersion8(_ queue: DatabaseQueue) throws {
        try makeRoomVersion(8, queue)
    }

    /// Makes `queue` a database as Kitae made it before exercises said whether they use
    /// weight: Room's version 9 tables, with the starter exercises and their library keys.
    static func makeVersion9(_ queue: DatabaseQueue) throws {
        try makeRoomVersion(9, queue)
    }

    private static func makeRoomVersion(_ version: Int, _ queue: DatabaseQueue) throws {
        struct RoomSchema: Decodable {
            struct Database: Decodable {
                var entities: [Entity]
                var setupQueries: [String]
            }
            struct Entity: Decodable {
                var tableName: String
                var createSql: String
                var indices: [Index]?
            }
            struct Index: Decodable {
                var createSql: String
            }
            var database: Database
        }
        let url = try #require(Bundle(for: TestSupportBundle.self).url(forResource: "RoomSchema\(version)", withExtension: "json"))
        let room = try JSONDecoder().decode(RoomSchema.self, from: Data(contentsOf: url)).database
        let seed = try Seed.bundled()
        try queue.write { db in
            for entity in room.entities {
                try db.execute(sql: entity.createSql.replacingOccurrences(of: "${TABLE_NAME}", with: entity.tableName))
                for index in entity.indices ?? [] {
                    try db.execute(sql: index.createSql.replacingOccurrences(of: "${TABLE_NAME}", with: entity.tableName))
                }
            }
            for query in room.setupQueries {
                try db.execute(sql: query)
            }
            for (type, modifier) in Schema.setTypeModifiers {
                try db.execute(sql: "INSERT INTO `set_type` (`type`, `modifier`) VALUES (?, ?)", arguments: [type.rawValue, modifier])
            }
            for exercise in seed.exercises {
                guard let id = exercise.id else { continue }
                try db.execute(
                    sql: "INSERT INTO `exercises` (`id`, `name`, `target`, `isIsometric`, `instructions`) VALUES (?, ?, ?, ?, ?)",
                    arguments: [id, exercise.name, exercise.target, exercise.isIsometric, exercise.instructions]
                )
                if version >= 9 {
                    try db.execute(sql: "UPDATE `exercises` SET `libraryKey` = ? WHERE `id` = ?", arguments: [exercise.key, id])
                }
            }
            try db.execute(sql: "PRAGMA user_version = \(version)")
        }
    }
}

/// Finds the test bundle's resources.
private final class TestSupportBundle {}

extension AppModel {
    /// A model on `database` that keeps nothing outside it: no settings, backups, Lock Screen
    /// timer or alerts.
    static func testing(_ database: KitaeDatabase, restStore: RestTimerStore = .none) -> AppModel {
        AppModel(
            database: database,
            restStore: restStore,
            settingsStore: .none,
            backups: .none,
            liveActivities: false,
            restAlerts: false
        )
    }
}

extension Data {
    /// Bytes written out in hexadecimal, like "0a0f".
    init?(hex: String) {
        var bytes: [UInt8] = []
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2, limitedBy: hex.endIndex) ?? hex.endIndex
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }
}
