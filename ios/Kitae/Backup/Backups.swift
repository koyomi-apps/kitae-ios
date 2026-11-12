// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import ZIPFoundation

nonisolated enum BackupError: Error, LocalizedError, Equatable {
    case notKitae
    case olderVersion
    case newerVersion
    case damaged
    case workoutInProgress
    case folderUnavailable

    var errorDescription: String? {
        switch self {
        case .notKitae:
            "This file isn't a Kitae backup."
        case .olderVersion:
            "This backup is from an older version of Kitae. Restore it in Kitae for Android, then back up again from there."
        case .newerVersion:
            "This backup is from a newer version of Kitae. Update Kitae, then try again."
        case .damaged:
            "This backup is damaged, so it can't be restored."
        case .workoutInProgress:
            "Finish or discard the workout in progress first."
        case .folderUnavailable:
            "Kitae can't reach the backup folder. Choose it again in Settings."
        }
    }
}

/// A backup taken apart, checked and ready to restore.
nonisolated struct BackupContents: Sendable {
    /// The backup's database, on its own in a work folder.
    let database: URL
    /// The backup's settings, if it has a settings file that can be read.
    let preferences: [String: PreferenceValue]?
}

/// Backups that Kitae for Android can restore, and restoring Android's. A backup is a zip like
/// Kitae for Android's: the database file `kenko_database`, and `settings.preferences_pb` with
/// the settings. Android's can also hold the database's write-ahead log, `kenko_database-wal`.
nonisolated enum Backups {
    static let databaseEntry = "kenko_database"
    static let logEntry = "kenko_database-wal"
    static let settingsEntry = "settings.preferences_pb"
    /// What's in Kitae just before a backup is restored over it.
    static let beforeRestoreName = "kitae_backup_before_restore.zip"
    /// Bigger files than this in a zip aren't Kitae's.
    private static let largestEntry: UInt64 = 1 << 30

    /// "kitae_backup_10_10.zip" for 10 October: named like Kitae for Android's backups, by day
    /// and month, so there's one a day and a day's backup replaces the one from a year before.
    static func fileName(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.day, .month], from: date)
        return "kitae_backup_\(twoDigits(parts.day ?? 1))_\(twoDigits(parts.month ?? 1)).zip"
    }

    private static func twoDigits(_ number: Int) -> String {
        number < 10 ? "0\(number)" : "\(number)"
    }

    // MARK: Backing up

    /// Backs `database` up with `preferences` into `folder` as `name`, replacing a file of
    /// that name.
    static func write(_ database: KitaeDatabase, preferences: [String: PreferenceValue], to folder: URL, named name: String) throws {
        let work = try workFolder()
        defer { try? FileManager.default.removeItem(at: work) }
        let zip = try makeArchive(database, preferences: preferences, in: work)
        try save(zip, into: folder, named: name)
    }

    /// The backup zip, made in `work`.
    static func makeArchive(_ database: KitaeDatabase, preferences: [String: PreferenceValue], in work: URL) throws -> URL {
        let databaseFile = work.appendingPathComponent(databaseEntry)
        let snapshot = try DatabaseQueue(path: databaseFile.path)
        // A copy of the database as it is now, while workouts can still be saved, in one file.
        try database.writer.backup(to: snapshot)
        try snapshot.writeWithoutTransaction { db in
            try db.execute(sql: "PRAGMA journal_mode = DELETE")
        }
        try snapshot.close()

        let settingsFile = work.appendingPathComponent(settingsEntry)
        try PreferencesFile.encode(preferences).write(to: settingsFile)

        let zip = work.appendingPathComponent("backup.zip")
        try archive([(databaseEntry, databaseFile), (settingsEntry, settingsFile)], to: zip)
        return zip
    }

    /// Zips `files` under their names.
    static func archive(_ files: [(name: String, url: URL)], to zip: URL) throws {
        let archive = try Archive(url: zip, accessMode: .create)
        for file in files {
            try archive.addEntry(with: file.name, fileURL: file.url, compressionMethod: .deflate)
        }
    }

    /// The names of the files in a zip.
    static func entryNames(_ zip: URL) throws -> [String] {
        try Archive(url: zip, accessMode: .read).filter { $0.type == .file }.map(\.path)
    }

    /// Writes `file` into `folder`, which may be one chosen in Files, coordinating with iCloud
    /// Drive and other apps.
    private static func save(_ file: URL, into folder: URL, named name: String) throws {
        let isScoped = folder.startAccessingSecurityScopedResource()
        defer { if isScoped { folder.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: file)
        let target = folder.appendingPathComponent(name)
        var coordinationError: NSError?
        var writeError: (any Error)?
        NSFileCoordinator().coordinate(writingItemAt: target, options: .forReplacing, error: &coordinationError) { url in
            do {
                try data.write(to: url, options: .atomic)
            } catch {
                writeError = error
            }
        }
        if let error = coordinationError ?? writeError { throw error }
    }

    // MARK: Restoring

    /// Replaces everything in `database` with the backup at `source`, which may be one picked in
    /// Files. Returns the backup's settings, if it has them. With `safetyFolder`, what's in
    /// `database` is backed up there first, with `currentPreferences`.
    static func restore(
        from source: URL,
        into database: KitaeDatabase,
        safetyFolder: URL? = nil,
        currentPreferences: [String: PreferenceValue] = [:]
    ) throws -> [String: PreferenceValue]? {
        let work = try workFolder()
        defer { try? FileManager.default.removeItem(at: work) }
        let zip = work.appendingPathComponent("restore.zip")
        try copyIn(source, to: zip)
        let contents = try open(zip, in: work)
        if let safetyFolder {
            try write(database, preferences: currentPreferences, to: safetyFolder, named: beforeRestoreName)
        }
        try replace(database, with: contents)
        return contents.preferences
    }

    /// Takes the zip apart in `work` and checks its database: Kitae's, version 8, and whole.
    static func open(_ zip: URL, in work: URL) throws -> BackupContents {
        let archive: Archive
        do {
            archive = try Archive(url: zip, accessMode: .read)
        } catch {
            throw BackupError.notKitae
        }
        let folder = work.appendingPathComponent("contents", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)

        // Only the files Kitae writes, by name, wherever they are in the zip. The database's
        // shared-memory file is left behind: SQLite makes a new one from the log.
        let wanted: Set = [databaseEntry, logEntry, settingsEntry]
        var found: Set<String> = []
        for entry in archive where entry.type == .file {
            let name = (entry.path as NSString).lastPathComponent
            guard wanted.contains(name), !found.contains(name) else { continue }
            guard entry.uncompressedSize <= largestEntry else { throw BackupError.notKitae }
            do {
                _ = try archive.extract(entry, to: folder.appendingPathComponent(name))
            } catch {
                throw BackupError.damaged
            }
            found.insert(name)
        }
        guard found.contains(databaseEntry) else { throw BackupError.notKitae }

        let databaseFile = folder.appendingPathComponent(databaseEntry)
        try check(databaseFile)

        var preferences: [String: PreferenceValue]?
        if found.contains(settingsEntry) {
            let settingsFile = folder.appendingPathComponent(settingsEntry)
            preferences = try? PreferencesFile.decode(Data(contentsOf: settingsFile))
        }
        return BackupContents(database: databaseFile, preferences: preferences)
    }

    /// Takes in the log Kitae for Android may have left next to its database, and checks the
    /// database is Kitae's, at a version this app can open (an older one is brought up to date
    /// once it's restored), and not damaged.
    private static func check(_ file: URL) throws {
        do {
            let queue = try DatabaseQueue(path: file.path)
            defer { try? queue.close() }
            try queue.writeWithoutTransaction { db in
                try db.execute(sql: "PRAGMA journal_mode = DELETE")
            }
            try queue.read { db in
                guard try db.tableExists("room_master_table") else { throw BackupError.notKitae }
                let version = try Int.fetchOne(db, sql: "PRAGMA user_version") ?? 0
                if version < Schema.oldestVersion { throw BackupError.olderVersion }
                if version > Schema.version { throw BackupError.newerVersion }
                let hash = try String.fetchOne(db, sql: "SELECT identity_hash FROM room_master_table WHERE id = 42")
                guard hash == Schema.identityHashes[version] else { throw BackupError.notKitae }
                guard try String.fetchOne(db, sql: "PRAGMA quick_check") == "ok" else { throw BackupError.damaged }
            }
        } catch let error as DatabaseError where error.resultCode == .SQLITE_NOTADB {
            throw BackupError.notKitae
        } catch let error as DatabaseError where error.resultCode == .SQLITE_CORRUPT {
            throw BackupError.damaged
        }
    }

    /// Copies the backup's database over `database`'s, while Kitae keeps it open.
    static func replace(_ database: KitaeDatabase, with contents: BackupContents) throws {
        let source = try DatabaseQueue(path: contents.database.path)
        defer { try? source.close() }
        try database.replaceContents(with: source)
    }

    /// Copies a file picked in Files, which may still have to come down from iCloud.
    private static func copyIn(_ source: URL, to destination: URL) throws {
        let isScoped = source.startAccessingSecurityScopedResource()
        defer { if isScoped { source.stopAccessingSecurityScopedResource() } }
        var coordinationError: NSError?
        var copyError: (any Error)?
        NSFileCoordinator().coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { url in
            do {
                try FileManager.default.copyItem(at: url, to: destination)
            } catch {
                copyError = error
            }
        }
        if let error = coordinationError ?? copyError { throw error }
    }

    /// A new, empty folder for taking a backup apart or putting one together.
    private static func workFolder() throws -> URL {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }
}
