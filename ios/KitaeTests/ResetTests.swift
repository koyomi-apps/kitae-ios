// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Reset Kitae: back to how it was when it was installed, like Kitae for Android's Reset app.
@MainActor
final class ResetTests {
    let folder: URL

    init() throws {
        folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("ResetTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: folder)
    }

    private func sessionCount(_ database: KitaeDatabase) throws -> Int {
        try database.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM `sessions`") } ?? -1
    }

    /// What a new install has: the starter plan and exercises, and no plan followed.
    private func expectNewInstall(_ database: KitaeDatabase) throws {
        let new = try KitaeDatabase.inMemory()
        #expect(try database.read { db in try Plans.all(db) } == new.read { db in try Plans.all(db) })
        #expect(try database.read { db in try Exercises.all(db) } == new.read { db in try Exercises.all(db) })
        #expect(try database.read { db in try Plans.currentID(db) } == nil)
        #expect(try sessionCount(database) == 0)
        #expect(try database.read { db in try Int.fetchOne(db, sql: "PRAGMA user_version") } == Schema.version)
    }

    @Test func resettingStartsOverLikeANewInstall() throws {
        let name = "KitaeTests.reset.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let own = folder.appendingPathComponent("own")
        let chosen = folder.appendingPathComponent("Chosen")
        try FileManager.default.createDirectory(at: own, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: chosen, withIntermediateDirectories: true)

        let model = AppModel(
            database: try KitaeDatabase.followingStarterPlan(),
            restStore: .none,
            settingsStore: SettingsStore(defaults: defaults),
            backups: BackupController(defaults: defaults, ownFolder: own),
            liveActivities: false,
            restAlerts: false
        )
        // A workout done, an exercise of one's own, settings changed and a folder chosen.
        model.settings.backUpAfterWorkouts = false
        model.settings.unit = .pounds
        model.settings.theme = .dark
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        model.finishWorkout(.keepSkipped, addNewSets: false)
        try model.saveExercise(ExerciseDraft(name: "Sled Push", muscle: .quads), id: nil)
        try model.backups.choose(chosen)
        let backup = chosen.appendingPathComponent("kitae_backup_10_10.zip")
        try Data("a backup".utf8).write(to: backup)

        try model.reset()

        try expectNewInstall(model.database)
        #expect(model.currentPlan == nil)
        #expect(model.hasDoneSets == false)
        #expect(model.settings == AppSettings())
        #expect(SettingsStore(defaults: defaults).load() == AppSettings())
        #expect(model.backups.folderName == "On My iPhone")
        #expect(model.backups.lastBackup == nil)
        #expect(model.generation == 1)
        // Backups aren't Kitae's to delete.
        #expect(FileManager.default.fileExists(atPath: backup.path))
    }

    @Test func aWorkoutInProgressGoesWithTheReset() throws {
        let model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        #expect(workout.rest.isActive)

        try model.reset()
        #expect(model.workout == nil)
        #expect(model.isWorkoutPresented == false)
        #expect(workout.rest.isActive == false)
        try expectNewInstall(model.database)
    }

    @Test func theAppsOpenDatabaseResetsInPlace() throws {
        let file = folder.appendingPathComponent("kenko_database")
        let pool = try DatabasePool(path: file.path)
        let database = try KitaeDatabase(pool)
        try database.write { db in
            let planID = try #require(try Int64.fetchOne(db, sql: "SELECT id FROM plans ORDER BY id LIMIT 1"))
            try Plans.setCurrent(db, planID: planID, today: 20_730)
            let day = try #require(try Plans.plan(db, id: planID)?.days.first)
            _ = try Sessions.start(db, dayID: day.id, today: 20_736, now: 1_791_633_600_000)
        }
        #expect(try sessionCount(database) == 1)

        try database.reset()
        try expectNewInstall(database)
        try pool.close()

        // And it opens again as Kitae's database.
        try expectNewInstall(try KitaeDatabase(DatabasePool(path: file.path)))
    }
}
