// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

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
/// Kitae for Android's, plus Kitae's own tables for the body, notes on exercises and its
/// exercise library, which Kitae for Android leaves alone. The shared Kotlin module opens it
/// with Room and prepares it as it opens (`KitaeDatabase.kt`): a new one gets the starter
/// exercises and plan, one from version 8 or 9 is brought up to 10, and each gets Kitae's own
/// tables and the library's exercises it hasn't been given.
nonisolated final class KitaeDatabase: @unchecked Sendable {
    /// The shared module's database, which can be used from any thread.
    let core: IPhoneDatabase

    private init(_ core: IPhoneDatabase) throws {
        self.core = core
        try core.open()
    }

    /// Closes the file when nothing uses the database any more, as tests make many.
    deinit {
        try? core.close()
    }

    /// The app's database. It has Kitae for Android's file name, so backups line up.
    static func app() throws -> KitaeDatabase {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return try file(folder.appendingPathComponent("kenko_database"))
    }

    /// The database in the file at `url`, made if it isn't there.
    static func file(_ url: URL) throws -> KitaeDatabase {
        try KitaeDatabase(IPhoneDatabase(path: url.path, seed: Seed.bundledText()))
    }

    /// A new database in a temporary file of its own, for tests and debug screens.
    static func temporary() throws -> KitaeDatabase {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("Databases", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return try file(folder.appendingPathComponent("kenko_database"))
    }

    func read<T>(_ value: (Database) throws -> T) throws -> T {
        try value(Database(core: core))
    }

    /// Runs `updates`. The shared module saves each change in it whole.
    @discardableResult
    func write<T>(_ updates: (Database) throws -> T) throws -> T {
        try updates(Database(core: core))
    }

    /// Makes this a new database again: Kitae's tables with the starter exercises and plan, no
    /// plan followed, and nothing else.
    func reset() throws {
        try core.reset()
    }

    /// Runs `statements` on the SQLite file at `url` as they are, made if it isn't there: for
    /// tests, which make databases as older versions of Kitae and Kitae for Android left them.
    static func execute(_ statements: [String], in url: URL) throws {
        try IPhoneDatabase.Companion.shared.executeIn(file: url.path, statements: statements)
    }

    /// Closes the database, for tests that open its file again.
    func close() throws {
        try core.close()
    }

    /// Puts the checked database file at `file` in place of this one. A backup from an older
    /// version, or from Kitae for Android, is brought up to date as it opens, and gets Kitae's
    /// own tables and any of Kitae's exercises it hasn't been given.
    func replaceContents(with file: URL) throws {
        try core.install(source: file.path)
    }
}
