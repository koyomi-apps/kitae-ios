// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Kitae for Android's settings file, checked against files made with protobuf's own library
/// from DataStore's `preferences.proto`.
struct PreferencesFileTests {
    /// Settings like Kitae for Android's, with every setting it keeps.
    static let androidFile = Data(hex: """
        0a1b0a0f6261636b75705f696e74657276616c12082a065765656b6c790a580a0a6261636b75705f75726912\
        4a2a48636f6e74656e743a2f2f636f6d2e616e64726f69642e65787465726e616c73746f726167652e646f63\
        756d656e74732f747265652f7072696d617279253341446f63756d656e74730a190a0d636f6c6f725f70616c\
        6574746512082a06536572656e650a220a186c6173745f6261636b75705f74696d655f7365636f6e64731206\
        2080e9b5d6060a1f0a156c6173745f7365745f74696d655f7365636f6e647312062080c6b0d6060a150a0f6f\
        6e626f617264696e675f646f6e65120208010a180a12726573745f74696d65725f7365636f6e647312021878\
        0a100a057468656d6512072a054c696768740a170a0b7765696768745f756e697412082a06506f756e6473
        """)!

    static let androidSettings: [String: PreferenceValue] = [
        "backup_interval": .string("Weekly"),
        "backup_uri": .string("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
        "color_palette": .string("Serene"),
        "last_backup_time_seconds": .long(1_791_849_600),
        "last_set_time_seconds": .long(1_791_763_200),
        "onboarding_done": .bool(true),
        "rest_timer_seconds": .int(120),
        "theme": .string("Light"),
        "weight_unit": .string("Pounds"),
    ]

    @Test func writesWhatDataStoreWrites() {
        #expect(PreferencesFile.encode(["theme": .string("Dark")]) == Data(hex: "0a0f0a057468656d6512062a044461726b"))
        #expect(PreferencesFile.encode(Self.androidSettings) == Self.androidFile)
    }

    @Test func readsAndroidsSettings() throws {
        #expect(try PreferencesFile.decode(Self.androidFile) == Self.androidSettings)
    }

    @Test func readsAndWritesEveryKindOfValue() throws {
        let file = try #require(Data(hex: """
            0a0e0a016412093900000000000002c00a0a0a01661205150000c03f0a120a036e6567120b18f9ffffffff\
            ffffffff010a0b0a037261771204420200ff0a0f0a03736574120832060a01610a0162
            """))
        let settings: [String: PreferenceValue] = [
            "d": .double(-2.25),
            "f": .float(1.5),
            "neg": .int(-7),
            "raw": .bytes(Data([0x00, 0xFF])),
            "set": .stringSet(["a", "b"]),
        ]
        #expect(try PreferencesFile.decode(file) == settings)
        #expect(PreferencesFile.encode(settings) == file)
    }

    @Test func emptyFilesHaveNoSettingsAndBrokenOnesAreRefused() throws {
        #expect(try PreferencesFile.decode(Data()).isEmpty)
        #expect(throws: PreferencesFileError.self) {
            try PreferencesFile.decode(Data([0x0A, 0x05, 0x0A]))
        }
        #expect(throws: PreferencesFileError.self) {
            try PreferencesFile.decode(Data([0x0A, 0x02, 0x0A, 0xFF]))
        }
    }
}

/// The settings a backup carries, under Kitae for Android's names.
struct BackupSettingsTests {
    @Test func backupsCarryTheSettingsAndroidReads() {
        var settings = AppSettings(unit: .pounds, decimals: .comma, defaultRestSeconds: 120, restAlerts: false, theme: .dark)
        settings.androidPalette = "Serene"
        #expect(settings.backupPreferences(planChosen: true) == [
            "weight_unit": .string("Pounds"),
            "rest_timer_seconds": .int(120),
            "theme": .string("Dark"),
            "decimals": .string("comma"),
            "rest_alerts": .bool(false),
            "workout_summary": .bool(true),
            "onboarding_done": .bool(true),
            "color_palette": .string("Serene"),
        ])
        // Without a plan, Kitae for Android starts with its first launch.
        let fresh = AppSettings().backupPreferences(planChosen: false)
        #expect(fresh["onboarding_done"] == nil)
        #expect(fresh["color_palette"] == nil)
    }

    @Test func androidsSettingsAreTakenIn() {
        var settings = AppSettings(decimals: .comma, restAlerts: false)
        settings.apply(backup: PreferencesFileTests.androidSettings)
        #expect(settings.unit == .pounds)
        #expect(settings.defaultRestSeconds == 120)
        #expect(settings.theme == .light)
        #expect(settings.androidPalette == "Serene")
        // Kitae for Android doesn't have these, so they stay as they were.
        #expect(settings.decimals == .comma)
        #expect(settings.restAlerts == false)
        #expect(settings.backUpAfterWorkouts)
    }

    @Test func settingsThatCantBeReadAreLeftAlone() {
        var settings = AppSettings()
        settings.apply(backup: [
            "weight_unit": .string("Stones"),
            "rest_timer_seconds": .long(60),
            "theme": .string("Sepia"),
            "color_palette": .string("Neon"),
            "decimals": .int(1),
            "rest_alerts": .string("yes"),
        ])
        #expect(settings == AppSettings())
    }
}

/// Backups Kitae for Android can restore, and restoring its backups.
final class BackupTests {
    /// Saturday 10 October 2026.
    static let today = 20_736

    let folder: URL

    init() throws {
        folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("BackupTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: folder)
    }

    private func subfolder(_ name: String) throws -> URL {
        let url = folder.appendingPathComponent(name, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    /// Two finished workouts with sets done, on the plan's first two days.
    private func addWorkouts(to database: KitaeDatabase) throws {
        let plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
        for (daysAgo, day) in [(3, plan.days[0]), (1, plan.days[1])] {
            let date = Self.today - daysAgo
            let start = Int64(date) * 86_400_000 + 12 * 3_600_000
            try database.write { db in
                let id = try Sessions.start(db, dayID: day.id, today: date, now: start)
                for set in try Sessions.exercises(db, sessionID: id, dayID: day.id).flatMap(\.sets).prefix(2) {
                    try Sessions.setDone(db, setID: set.id, isDone: true)
                }
                try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: start + 2_700_000)
            }
        }
    }

    private func history(_ database: KitaeDatabase) throws -> [WorkoutSummary] {
        try database.read { db in try Sessions.history(db, activeID: nil) }
    }

    /// A zip like Kitae for Android's, holding `files`, written out as they are.
    private func zip(_ files: [(name: String, contents: Data)]) throws -> URL {
        let work = try subfolder("zip-\(UUID().uuidString)")
        var entries: [(name: String, url: URL)] = []
        for file in files {
            let url = work.appendingPathComponent(file.name)
            try file.contents.write(to: url)
            entries.append((file.name, url))
        }
        let zip = work.appendingPathComponent("backup.zip")
        try Backups.archive(entries, to: zip)
        return zip
    }

    /// A backup whose database is Kitae's, but marked as `version`.
    private func backup(atVersion version: Int) throws -> URL {
        let work = try subfolder("version-\(version)")
        let file = work.appendingPathComponent("kenko_database")
        let queue = try DatabaseQueue(path: file.path)
        _ = try KitaeDatabase(queue)
        try queue.writeWithoutTransaction { db in
            try db.execute(sql: "PRAGMA user_version = \(version)")
        }
        try queue.close()
        let zip = work.appendingPathComponent("backup.zip")
        try Backups.archive([("kenko_database", file)], to: zip)
        return zip
    }

    @Test func backupsAreNamedByDayAndMonthLikeAndroids() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .gmt
        let october = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 10, hour: 18)))
        let march = try #require(calendar.date(from: DateComponents(year: 2027, month: 3, day: 5)))
        #expect(Backups.fileName(for: october, calendar: calendar) == "kitae_backup_10_10.zip")
        #expect(Backups.fileName(for: march, calendar: calendar) == "kitae_backup_05_03.zip")
    }

    @Test func aBackupHoldsWhatKitaeForAndroidRestores() throws {
        let source = try KitaeDatabase.followingStarterPlan(today: Self.today)
        try addWorkouts(to: source)
        let preferences = AppSettings(unit: .pounds).backupPreferences(planChosen: true)
        let out = try subfolder("out")
        try Backups.write(source, preferences: preferences, to: out, named: "kitae_backup_10_10.zip")
        let zip = out.appendingPathComponent("kitae_backup_10_10.zip")

        #expect(try Backups.entryNames(zip) == ["kenko_database", "settings.preferences_pb"])
        let contents = try Backups.open(zip, in: try subfolder("open"))
        #expect(contents.preferences == preferences)
        // The database is one file, without a log beside it, as Android replaces its own files.
        let neighbours = try FileManager.default.contentsOfDirectory(atPath: contents.database.deletingLastPathComponent().path)
        #expect(neighbours.sorted() == ["kenko_database", "settings.preferences_pb"])
    }

    @Test func aBackupRestoresIntoAnotherDatabase() throws {
        let source = try KitaeDatabase.followingStarterPlan(today: Self.today)
        try addWorkouts(to: source)
        let out = try subfolder("out")
        try Backups.write(source, preferences: ["theme": .string("Dark")], to: out, named: "backup.zip")

        // A new database, with no plan chosen and nothing done.
        let target = try KitaeDatabase.inMemory()
        let preferences = try Backups.restore(from: out.appendingPathComponent("backup.zip"), into: target)
        #expect(preferences == ["theme": .string("Dark")])
        #expect(try history(target) == history(source))
        #expect(try target.read { db in try Plans.currentID(db) } == source.read { db in try Plans.currentID(db) })
        #expect(try history(target).count == 2)
    }

    @Test func aBackupRestoresIntoTheAppsOpenDatabase() throws {
        let source = try KitaeDatabase.followingStarterPlan(today: Self.today)
        try addWorkouts(to: source)
        let out = try subfolder("out")
        try Backups.write(source, preferences: [:], to: out, named: "backup.zip")

        // Like the app's: a file in write-ahead-log mode, kept open while it's replaced.
        let file = try subfolder("app").appendingPathComponent("kenko_database")
        let pool = try DatabasePool(path: file.path)
        let app = try KitaeDatabase(pool)
        _ = try Backups.restore(from: out.appendingPathComponent("backup.zip"), into: app)
        #expect(try history(app) == history(source))
        try pool.close()

        // And it opens again as Kitae's database.
        let reopened = try KitaeDatabase(DatabasePool(path: file.path))
        #expect(try history(reopened) == history(source))
    }

    @Test func androidBackupsWithALogComeBackWhole() throws {
        // Kitae for Android's database while it's open, with its latest changes still in the log.
        let android = try subfolder("android")
        let pool = try DatabasePool(path: android.appendingPathComponent("kenko_database").path)
        let database = try KitaeDatabase(pool)
        try database.write { db in
            let planID = try #require(try Int64.fetchOne(db, sql: "SELECT id FROM plans ORDER BY id LIMIT 1"))
            try Plans.setCurrent(db, planID: planID, today: Self.today - 5)
        }
        try addWorkouts(to: database)
        let expected = try history(database)
        var files: [(name: String, contents: Data)] = []
        for name in ["kenko_database", "kenko_database-wal", "kenko_database-shm"] {
            files.append((name, try Data(contentsOf: android.appendingPathComponent(name))))
        }
        #expect(files[1].contents.isEmpty == false)
        files.append(("settings.preferences_pb", PreferencesFileTests.androidFile))
        let zip = try zip(files)
        try pool.close()

        let target = try KitaeDatabase.inMemory()
        let preferences = try Backups.restore(from: zip, into: target)
        #expect(try history(target) == expected)
        #expect(preferences == PreferencesFileTests.androidSettings)
    }

    @Test func filesThatArentKitaeBackupsAreRefused() throws {
        let target = try KitaeDatabase.followingStarterPlan(today: Self.today)
        try addWorkouts(to: target)
        let before = try history(target)

        let text = folder.appendingPathComponent("notes.zip")
        try Data("Push day: bench, press, dips".utf8).write(to: text)
        #expect(throws: BackupError.notKitae) { try Backups.restore(from: text, into: target) }

        let settingsOnly = try zip([("settings.preferences_pb", PreferencesFileTests.androidFile)])
        #expect(throws: BackupError.notKitae) { try Backups.restore(from: settingsOnly, into: target) }

        let notADatabase = try zip([("kenko_database", Data(String(repeating: "not a database ", count: 40).utf8))])
        #expect(throws: BackupError.notKitae) { try Backups.restore(from: notADatabase, into: target) }

        let otherWork = try subfolder("other")
        let otherFile = otherWork.appendingPathComponent("kenko_database")
        let other = try DatabaseQueue(path: otherFile.path)
        try other.write { db in try db.execute(sql: "CREATE TABLE notes (text TEXT)") }
        try other.close()
        let otherZip = try zip([("kenko_database", try Data(contentsOf: otherFile))])
        #expect(throws: BackupError.notKitae) { try Backups.restore(from: otherZip, into: target) }

        // Nothing was replaced.
        #expect(try history(target) == before)
    }

    @Test func backupsFromOtherVersionsAreRefused() throws {
        let target = try KitaeDatabase.inMemory()
        #expect(throws: BackupError.olderVersion) { try Backups.restore(from: try backup(atVersion: 7), into: target) }
        #expect(throws: BackupError.newerVersion) { try Backups.restore(from: try backup(atVersion: Schema.version + 1), into: target) }
        // The version this app has goes in. (Version 8's is in LibraryTests.)
        #expect(try Backups.restore(from: try backup(atVersion: Schema.version), into: target) == nil)
    }

    @Test func whatWasThereIsSavedBeforeRestoring() throws {
        let current = try KitaeDatabase.followingStarterPlan(today: Self.today)
        try addWorkouts(to: current)
        let before = try history(current)
        let safety = try subfolder("safety")

        let fresh = try backup(atVersion: Schema.version)
        _ = try Backups.restore(from: fresh, into: current, safetyFolder: safety, currentPreferences: ["theme": .string("Dark")])
        #expect(try history(current).isEmpty)

        let copy = safety.appendingPathComponent(Backups.beforeRestoreName)
        let elsewhere = try KitaeDatabase.inMemory()
        #expect(try Backups.restore(from: copy, into: elsewhere) == ["theme": .string("Dark")])
        #expect(try history(elsewhere) == before)
    }

    @Test func kitaesFolderShowsInFiles() {
        #expect(Bundle.main.object(forInfoDictionaryKey: "UIFileSharingEnabled") as? Bool == true)
        #expect(Bundle.main.object(forInfoDictionaryKey: "LSSupportsOpeningDocumentsInPlace") as? Bool == true)
    }
}

/// Backing up and restoring from the app: after workouts, from Settings, and the folder.
@MainActor
final class BackupModelTests {
    let folder: URL

    init() throws {
        folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("BackupModelTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: folder)
    }

    private func model(_ database: KitaeDatabase, backupsTo own: URL) -> AppModel {
        AppModel(
            database: database,
            restStore: .none,
            settingsStore: .none,
            backups: BackupController(defaults: nil, ownFolder: own),
            liveActivities: false,
            restAlerts: false
        )
    }

    private func finishAWorkout(_ model: AppModel) throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        model.finishWorkout(.keepSkipped, addNewSets: false)
    }

    @Test func finishingAWorkoutBacksUp() async throws {
        let model = model(try KitaeDatabase.followingStarterPlan(), backupsTo: folder)
        try finishAWorkout(model)
        await model.backupAfterWorkout?.value
        let name = Backups.fileName(for: model.clock())
        #expect(FileManager.default.fileExists(atPath: folder.appendingPathComponent(name).path))
        #expect(model.backups.lastBackup != nil)
        #expect(model.backups.problem == nil)
    }

    @Test func turningItOffMakesNoBackups() async throws {
        let model = model(try KitaeDatabase.followingStarterPlan(), backupsTo: folder)
        model.settings.backUpAfterWorkouts = false
        try finishAWorkout(model)
        await model.backupAfterWorkout?.value
        #expect(model.backupAfterWorkout == nil)
        #expect(try FileManager.default.contentsOfDirectory(atPath: folder.path).isEmpty)
    }

    @Test func restoringReplacesTheDataAndTakesTheSettings() async throws {
        // A backup from another phone, in pounds, with workouts done.
        let other = model(try KitaeDatabase.followingStarterPlan(), backupsTo: folder.appendingPathComponent("other"))
        try FileManager.default.createDirectory(at: other.backups.ownFolder!, withIntermediateDirectories: true)
        other.settings.backUpAfterWorkouts = false
        other.settings.unit = .pounds
        try finishAWorkout(other)
        #expect(await other.backUp())
        let backup = other.backups.ownFolder!.appendingPathComponent(Backups.fileName(for: other.clock()))

        // A new install, before a plan is chosen.
        let own = folder.appendingPathComponent("own")
        try FileManager.default.createDirectory(at: own, withIntermediateDirectories: true)
        let model = model(try KitaeDatabase.inMemory(), backupsTo: own)
        #expect(model.currentPlan == nil)

        try await model.restoreBackup(from: backup)
        #expect(model.currentPlan?.name == "Push Pull Leg")
        #expect(model.hasDoneSets)
        #expect(model.settings.unit == .pounds)
        #expect(model.selectedDay?.name == "Pull A")
        #expect(FileManager.default.fileExists(atPath: own.appendingPathComponent(Backups.beforeRestoreName).path))
    }

    @Test func nothingIsRestoredDuringAWorkout() async throws {
        let model = model(try KitaeDatabase.followingStarterPlan(), backupsTo: folder)
        model.settings.backUpAfterWorkouts = false
        #expect(await model.backUp())
        let backup = folder.appendingPathComponent(Backups.fileName(for: model.clock()))

        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        await #expect(throws: BackupError.workoutInProgress) {
            try await model.restoreBackup(from: backup)
        }
        #expect(model.workout != nil)
    }

    @Test func aChosenFolderIsKeptUntilKitaesOwnIsPicked() throws {
        let name = "KitaeTests.backups.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let own = folder.appendingPathComponent("own")
        let chosen = folder.appendingPathComponent("Training")
        try FileManager.default.createDirectory(at: own, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: chosen, withIntermediateDirectories: true)

        let backups = BackupController(defaults: defaults, ownFolder: own)
        #expect(backups.folderName == "On My iPhone")
        #expect(try backups.folder() == own)

        try backups.choose(chosen)
        #expect(backups.folderName == "Training")
        let reopened = BackupController(defaults: defaults, ownFolder: own)
        #expect(reopened.folderName == "Training")
        #expect(try reopened.folder()?.standardizedFileURL.path == chosen.standardizedFileURL.path)

        try reopened.choose(own)
        #expect(reopened.folderName == "On My iPhone")
        #expect(try BackupController(defaults: defaults, ownFolder: own).folder() == own)
    }
}
