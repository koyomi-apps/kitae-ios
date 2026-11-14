// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore
import Testing
@testable import Kitae

extension KitaeDatabase {
    /// A new database that follows the starter plan from `today`, as if it was chosen on first
    /// launch.
    static func followingStarterPlan(today: Int = 20_736) throws -> KitaeDatabase {
        let database = try KitaeDatabase.temporary()
        try database.write { db in
            guard let planID = try Int64.fetchOne(db, sql: "SELECT id FROM plans ORDER BY id LIMIT 1") else {
                throw KitaeDatabaseError.missingSeed
            }
            try Plans.setCurrent(db, planID: planID, today: today)
        }
        return database
    }

    /// Where a test can make a database file of its own: a new temporary folder.
    static func newFile() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("TestDatabases-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("kenko_database")
    }

    /// Makes the file at `url` a database as Kitae made it before library keys: Room's version 8
    /// tables, from its schema file, with the starter exercises.
    static func makeVersion8(at url: URL) throws {
        try makeRoomVersion(8, at: url)
    }

    /// Makes the file at `url` a database as Kitae made it before exercises said whether they use
    /// weight: Room's version 9 tables, with the starter exercises and their library keys.
    static func makeVersion9(at url: URL) throws {
        try makeRoomVersion(9, at: url)
    }

    /// Makes the file at `url` a database as Kitae for Android makes it: Room's version 10 tables
    /// with the starter exercises, and none of Kitae's own tables.
    static func makeVersion10(at url: URL) throws {
        try makeRoomVersion(10, at: url)
    }

    private static func makeRoomVersion(_ version: Int, at url: URL) throws {
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
        let schema = try #require(Bundle(for: TestSupportBundle.self).url(forResource: "RoomSchema\(version)", withExtension: "json"))
        let room = try JSONDecoder().decode(RoomSchema.self, from: Data(contentsOf: schema)).database
        let seed = try Seed.bundled()
        var statements = ["BEGIN"]
        for entity in room.entities {
            statements.append(entity.createSql.replacingOccurrences(of: "${TABLE_NAME}", with: entity.tableName))
            for index in entity.indices ?? [] {
                statements.append(index.createSql.replacingOccurrences(of: "${TABLE_NAME}", with: entity.tableName))
            }
        }
        statements += room.setupQueries
        for (type, modifier) in Schema.setTypeModifiers {
            statements.append("INSERT INTO `set_type` (`type`, `modifier`) VALUES (\(literal(type.rawValue)), \(modifier))")
        }
        for exercise in seed.exercises {
            guard let id = exercise.id else { continue }
            statements.append("""
                INSERT INTO `exercises` (`id`, `name`, `target`, `isIsometric`, `instructions`)
                VALUES (\(id), \(literal(exercise.name)), \(literal(exercise.target)), \(exercise.isIsometric ? 1 : 0), \(literal(exercise.instructions)))
                """)
            if version >= 9 {
                statements.append("UPDATE `exercises` SET `libraryKey` = \(literal(exercise.key)) WHERE `id` = \(id)")
            }
        }
        statements += ["COMMIT", "PRAGMA user_version = \(version)"]
        try IPhoneDatabase.Companion.shared.executeIn(file: url.path, statements: statements)
    }

    /// Runs `statements` on the SQLite file at `url` as they are, made if it isn't there.
    static func execute(_ statements: [String], in url: URL) throws {
        try IPhoneDatabase.Companion.shared.executeIn(file: url.path, statements: statements)
    }
}

/// `text` as an SQL literal.
func literal(_ text: String?) -> String {
    guard let text else { return "NULL" }
    return "'" + text.replacingOccurrences(of: "'", with: "''") + "'"
}

/// Room's schema for Kitae's database, as the tests check it (`KitaeDatabase.kt` has it).
enum Schema {
    static let version = 10
    /// The oldest version Kitae can bring up to date.
    static let oldestVersion = 8
    static let identityHash = "a8dd8c0661148678f11cce771546f37e"
    static let identityHashes = [
        8: "ed136697240555e41abaa05a57ab6667",
        9: "5d1b3eb2bd3f9bcf00e2d5627becb694",
        10: identityHash,
    ]
    /// How much each set type counted towards Kitae for Android's performance rating, as 32-bit
    /// floats, stored as they land in the database.
    static let setTypeModifiers: [(type: SetType, modifier: Double)] = [
        (.standard, Double(Float(1.0))),
        (.drop, Double(Float(1.35))),
        (.restPause, Double(Float(1.2))),
        (.warmup, Double(Float(0))),
        (.failure, Double(Float(1.15))),
    ]
}

// What the tests used of GRDB to look at and change what's stored, on the shared module's
// database. Values go in as text, which SQLite turns into each column's type, and come out as
// text, which these turn into the type asked for.

/// A value the tests bind into SQL or read out of it.
protocol SQLValue {
    init?(sqlText: String?)
    var sqlText: String? { get }
}

extension String: SQLValue {
    init?(sqlText: String?) {
        guard let sqlText else { return nil }
        self = sqlText
    }

    var sqlText: String? { self }
}

extension Int64: SQLValue {
    init?(sqlText: String?) {
        guard let sqlText else { return nil }
        if let value = Int64(sqlText) {
            self = value
        } else if let value = Double(sqlText) {
            self = Int64(value)
        } else {
            return nil
        }
    }

    var sqlText: String? { String(self) }
}

extension Int: SQLValue {
    init?(sqlText: String?) {
        guard let value = Int64(sqlText: sqlText) else { return nil }
        self = Int(value)
    }

    var sqlText: String? { String(self) }
}

extension Double: SQLValue {
    init?(sqlText: String?) {
        guard let sqlText, let value = Double(sqlText) else { return nil }
        self = value
    }

    var sqlText: String? { String(self) }
}

extension Bool: SQLValue {
    init?(sqlText: String?) {
        guard let value = Double(sqlText: sqlText) else { return nil }
        self = value != 0
    }

    var sqlText: String? { self ? "1" : "0" }
}

extension Optional: SQLValue where Wrapped: SQLValue {
    init?(sqlText: String?) {
        self = sqlText.flatMap { Wrapped(sqlText: $0) }
    }

    var sqlText: String? { self?.sqlText }
}

/// A row read with SQL.
struct Row {
    var columns: [String]
    var values: [String?]

    subscript<Value: SQLValue>(_ column: String) -> Value {
        guard let index = columns.firstIndex(of: column) else { fatalError("There's no column \(column).") }
        guard let value = Value(sqlText: values[index]) else { fatalError("\(column) isn't a \(Value.self).") }
        return value
    }

    static func fetchAll(_ db: Database, sql: String, arguments: [any SQLValue] = []) throws -> [Row] {
        try db.rows(sql, arguments)
    }

    static func fetchOne(_ db: Database, sql: String, arguments: [any SQLValue] = []) throws -> Row? {
        try fetchAll(db, sql: sql, arguments: arguments).first
    }
}

extension SQLValue {
    static func fetchOne(_ db: Database, sql: String, arguments: [any SQLValue] = []) throws -> Self? {
        try db.rows(sql, arguments).first.flatMap { Self(sqlText: $0.values.first ?? nil) }
    }

    static func fetchAll(_ db: Database, sql: String, arguments: [any SQLValue] = []) throws -> [Self] {
        try db.rows(sql, arguments).compactMap { Self(sqlText: $0.values.first ?? nil) }
    }
}

extension Database {
    func execute(sql: String, arguments: [any SQLValue] = []) throws {
        try core.execute(sql: sql, arguments: Self.json(arguments))
    }

    var lastInsertedRowID: Int64 {
        (try? core.lastInsertRowId()) ?? -1
    }

    func tableExists(_ name: String) throws -> Bool {
        try Int.fetchOne(self, sql: "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", arguments: [name]) == 1
    }

    fileprivate func rows(_ sql: String, _ arguments: [any SQLValue]) throws -> [Row] {
        let text = try core.query(sql: sql, arguments: Self.json(arguments))
        let table = try #require(try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [[Any]])
        let columns = table.first?.compactMap { $0 as? String } ?? []
        return table.dropFirst().map { row in Row(columns: columns, values: row.map { $0 as? String }) }
    }

    private static func json(_ arguments: [any SQLValue]) throws -> String {
        let values: [Any] = arguments.map { $0.sqlText ?? NSNull() }
        return String(decoding: try JSONSerialization.data(withJSONObject: values), as: UTF8.self)
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
