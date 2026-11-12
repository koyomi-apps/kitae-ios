// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

nonisolated enum KitaeDatabaseError: Error, LocalizedError {
    case missingSeed
    case unsupportedVersion(Int)
    case notKitae
    case missingDay(Int64)

    var errorDescription: String? {
        switch self {
        case .missingSeed:
            "Kitae's starter exercises are missing from the app."
        case .unsupportedVersion(let version):
            "This data is from a different version of Kitae (database version \(version)). Update Kitae and try again."
        case .notKitae:
            "This isn't Kitae's data."
        case .missingDay(let id):
            "Day \(id) isn't in any plan any more."
        }
    }
}

/// Where Kitae keeps plans, workouts and exercises: one SQLite file, laid out exactly like
/// Kitae for Android's (see `Schema`), plus Kitae's own tables for the body (see `BodyTables`),
/// notes on exercises (see `ExerciseNotes`) and its exercise library (see `ExerciseLibrary`),
/// which Kitae for Android leaves alone.
nonisolated final class KitaeDatabase: Sendable {
    let writer: any DatabaseWriter

    /// Opens the database at `writer`, setting it up first if it's new.
    init(_ writer: any DatabaseWriter) throws {
        self.writer = writer
        try writer.write { db in
            try Self.prepare(db)
        }
    }

    /// The app's database. It has Kitae for Android's file name, so backups line up.
    static func app() throws -> KitaeDatabase {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let path = folder.appendingPathComponent("kenko_database").path
        var configuration = Configuration()
        configuration.label = "Kitae"
        return try KitaeDatabase(DatabasePool(path: path, configuration: configuration))
    }

    /// A database that lives only in memory, for tests and debug screens.
    static func inMemory() throws -> KitaeDatabase {
        try KitaeDatabase(DatabaseQueue())
    }

    func read<T>(_ value: (Database) throws -> T) throws -> T {
        try writer.read(value)
    }

    /// Runs `updates` in one transaction: all of it is saved, or none.
    @discardableResult
    func write<T>(_ updates: (Database) throws -> T) throws -> T {
        try writer.write(updates)
    }

    /// Makes this a new database again, while it stays open: Kitae's tables with the starter
    /// exercises and plan, no plan followed, and nothing else.
    func reset() throws {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("reset-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let fresh = try DatabaseQueue(path: folder.appendingPathComponent("kenko_database").path)
        defer { try? fresh.close() }
        try fresh.write { db in try Self.prepare(db) }
        try replaceContents(with: fresh)
    }

    /// Copies `source` over this database, while Kitae keeps it open. SQLite can only copy
    /// into a database in write-ahead-log mode if the pages are the same size, so `source`'s
    /// are made to match first.
    func replaceContents(with source: DatabaseQueue) throws {
        let pageSize = try read { db in try Int.fetchOne(db, sql: "PRAGMA page_size") } ?? 4096
        try source.writeWithoutTransaction { db in
            guard try Int.fetchOne(db, sql: "PRAGMA page_size") != pageSize else { return }
            try db.execute(sql: "PRAGMA page_size = \(pageSize)")
            try db.execute(sql: "VACUUM")
        }
        try source.backup(to: writer)
        // A backup from an older version, or from Kitae for Android, is brought up to date, and
        // gets Kitae's own tables and any of Kitae's exercises it hasn't been given.
        try writer.write { db in try Self.prepare(db) }
    }

    /// Makes the tables in a new database and adds the starter exercises and plan. No plan is
    /// followed until people pick one, as in Kitae for Android. An existing database has to be
    /// version 8, which is brought up to 9, or 9. Either way, Kitae's own tables are made if
    /// they aren't there, and Kitae's exercises it hasn't been given are added.
    private static func prepare(_ db: Database) throws {
        let version = try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
        let seed = try Seed.bundled()
        if version == 0 {
            let tables = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'") ?? 0
            guard tables == 0 else { throw KitaeDatabaseError.notKitae }
            try Schema.create(db)
            try seed.insert(db)
        } else {
            guard let expected = Schema.identityHashes[version] else {
                throw KitaeDatabaseError.unsupportedVersion(version)
            }
            let hash = try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42")
            guard hash == expected else { throw KitaeDatabaseError.notKitae }
            if version == 8 {
                try Schema.migrate8(db, keys: seed.starterKeys)
            }
            if version <= 9 {
                try Schema.migrate9(db, weightless: seed.weightlessKeys)
            }
        }
        try BodyTables.create(db)
        try ExerciseNotes.create(db)
        try ExerciseLibrary.create(db, seed: seed)
        try ExerciseLibrary.sync(db, library: seed.exercises)
    }
}
