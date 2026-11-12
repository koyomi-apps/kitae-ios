// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Observation

/// Where backups go and how the last one went. They go into Kitae's own folder, which Files
/// shows as On My iPhone › Kitae, until another folder is chosen in Settings.
@Observable
final class BackupController {
    /// When the last backup was made on this iPhone.
    private(set) var lastBackup: Date?
    /// Why the last backup didn't work, until one does.
    private(set) var problem: String?
    /// Whether a backup is being made or restored.
    private(set) var isWorking = false
    /// The folder chosen in Settings. nil while backups go into Kitae's own.
    private(set) var chosenFolderName: String?

    /// Kitae's own folder. Without one, nothing is backed up.
    let ownFolder: URL?
    private let defaults: UserDefaults?

    private enum Key {
        static let lastBackup = "last_backup"
        static let folder = "backup_folder"
        static let folderName = "backup_folder_name"
    }

    init(defaults: UserDefaults?, ownFolder: URL?) {
        self.defaults = defaults
        self.ownFolder = ownFolder
        lastBackup = defaults?.object(forKey: Key.lastBackup) as? Date
        if defaults?.data(forKey: Key.folder) != nil {
            chosenFolderName = defaults?.string(forKey: Key.folderName) ?? "Chosen folder"
        }
    }

    /// Backups in Kitae's Documents folder, which Files shows as On My iPhone › Kitae.
    static var standard: BackupController {
        let documents = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return BackupController(defaults: .standard, ownFolder: documents)
    }

    /// Keeps no backups, for tests.
    static var none: BackupController { BackupController(defaults: nil, ownFolder: nil) }

    /// "On My iPhone", or the name of the folder chosen in Settings.
    var folderName: String { chosenFolderName ?? "On My iPhone" }

    // MARK: The folder

    /// Backs up into `url` from now on: a folder picked in Files, which Kitae can keep using.
    /// Picking Kitae's own folder goes back to it.
    func choose(_ url: URL) throws {
        let isScoped = url.startAccessingSecurityScopedResource()
        defer { if isScoped { url.stopAccessingSecurityScopedResource() } }
        if let ownFolder, Self.isSameFolder(url, ownFolder) {
            useOwnFolder()
            return
        }
        let bookmark = try url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil)
        let name = (try? url.resourceValues(forKeys: [.localizedNameKey]).localizedName) ?? url.lastPathComponent
        defaults?.set(bookmark, forKey: Key.folder)
        defaults?.set(name, forKey: Key.folderName)
        chosenFolderName = name
        problem = nil
    }

    /// Goes back to backing up into Kitae's own folder.
    func useOwnFolder() {
        defaults?.removeObject(forKey: Key.folder)
        defaults?.removeObject(forKey: Key.folderName)
        chosenFolderName = nil
        problem = nil
    }

    /// Forgets the chosen folder and the last backup, as on a new install. The backups
    /// themselves stay where they are.
    func forget() {
        useOwnFolder()
        defaults?.removeObject(forKey: Key.lastBackup)
        lastBackup = nil
    }

    /// The folder to back up into now, or nil if nothing is backed up.
    func folder() throws -> URL? {
        guard let ownFolder else { return nil }
        guard let bookmark = defaults?.data(forKey: Key.folder) else { return ownFolder }
        var isStale = false
        guard let url = try? URL(resolvingBookmarkData: bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &isStale) else {
            throw BackupError.folderUnavailable
        }
        if isStale {
            // Saved again, so it keeps working after the folder is moved or renamed.
            let isScoped = url.startAccessingSecurityScopedResource()
            defer { if isScoped { url.stopAccessingSecurityScopedResource() } }
            if let fresh = try? url.bookmarkData(options: .minimalBookmark, includingResourceValuesForKeys: nil, relativeTo: nil) {
                defaults?.set(fresh, forKey: Key.folder)
            }
        }
        return url
    }

    private static func isSameFolder(_ one: URL, _ other: URL) -> Bool {
        one.standardizedFileURL.resolvingSymlinksInPath().path == other.standardizedFileURL.resolvingSymlinksInPath().path
    }

    // MARK: Backing up and restoring

    /// Backs `database` up with `preferences`, named for `date`. Returns whether it worked; if
    /// not, `problem` says why.
    @discardableResult
    func backUp(_ database: KitaeDatabase, preferences: [String: PreferenceValue], at date: Date = .now) async -> Bool {
        guard !isWorking else { return false }
        let target: URL
        do {
            guard let folder = try folder() else { return false }
            target = folder
        } catch {
            problem = error.localizedDescription
            return false
        }
        isWorking = true
        defer { isWorking = false }
        let name = Backups.fileName(for: date)
        do {
            try await Task.detached(priority: .utility) {
                try Backups.write(database, preferences: preferences, to: target, named: name)
            }.value
            lastBackup = date
            defaults?.set(date, forKey: Key.lastBackup)
            problem = nil
            return true
        } catch {
            problem = error.localizedDescription
            return false
        }
    }

    /// Replaces everything in `database` with the backup at `url`. What's there now is backed
    /// up into Kitae's own folder first, with `currentPreferences`. Returns the backup's
    /// settings, if it has them.
    func restore(_ url: URL, into database: KitaeDatabase, currentPreferences: [String: PreferenceValue]) async throws -> [String: PreferenceValue]? {
        guard !isWorking else { throw CancellationError() }
        isWorking = true
        defer { isWorking = false }
        let safetyFolder = ownFolder
        return try await Task.detached(priority: .userInitiated) {
            try Backups.restore(from: url, into: database, safetyFolder: safetyFolder, currentPreferences: currentPreferences)
        }.value
    }
}
