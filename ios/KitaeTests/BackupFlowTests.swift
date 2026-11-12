// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// Backing up and restoring from the rows in Settings and the welcome.
@MainActor
final class BackupFlowTests {
    let folder: URL

    init() throws {
        folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("BackupFlowTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    deinit {
        try? FileManager.default.removeItem(at: folder)
    }

    private func model(_ database: KitaeDatabase, backupsTo name: String) throws -> AppModel {
        let own = folder.appendingPathComponent(name, isDirectory: true)
        try FileManager.default.createDirectory(at: own, withIntermediateDirectories: true)
        return AppModel(
            database: database,
            restStore: .none,
            settingsStore: .none,
            backups: BackupController(defaults: nil, ownFolder: own),
            liveActivities: false,
            restAlerts: false
        )
    }

    /// A backup from another phone, in pounds, with a workout done.
    private func backupFromAnotherPhone() async throws -> URL {
        let other = try model(try KitaeDatabase.followingStarterPlan(), backupsTo: "other")
        other.settings.backUpAfterWorkouts = false
        other.settings.unit = .pounds
        let day = try #require(other.selectedDay)
        other.startWorkout(day)
        let workout = try #require(other.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        other.finishWorkout(.keepSkipped, addNewSets: false)
        #expect(await other.backUp())
        return try #require(other.backups.ownFolder).appendingPathComponent(Backups.fileName(for: other.clock()))
    }

    @Test func aBackupRestoredOnFirstLaunchBringsEverythingAlong() async throws {
        let backup = try await backupFromAnotherPhone()
        // A new install, on the welcome.
        let model = try model(try KitaeDatabase.inMemory(), backupsTo: "own")
        let flow = BackupFlow()

        flow.chooseBackup(model)
        #expect(flow.isImporting)
        #expect(flow.importing == .backup)
        flow.isImporting = false
        flow.picked(.success(backup), model: model)
        // Asked about once Files has gone.
        for _ in 0..<40 where flow.restoring == nil {
            try await Task.sleep(for: .milliseconds(50))
        }
        #expect(flow.restoring == backup)

        await flow.restore(backup, model: model)
        #expect(flow.notice?.title == "Backup restored")
        #expect(flow.activity == nil)
        #expect(model.currentPlan?.name == "Push Pull Leg")
        #expect(model.settings.unit == .pounds)
    }

    @Test func noBackupIsPickedDuringAWorkout() throws {
        let model = try model(try KitaeDatabase.followingStarterPlan(), backupsTo: "own")
        model.startWorkout(try #require(model.selectedDay))
        let flow = BackupFlow()

        flow.chooseBackup(model)
        #expect(flow.isImporting == false)
        #expect(flow.notice?.title == "Can't restore a backup now")
    }

    @Test func aPickedFolderIsUsed() throws {
        let model = try model(try KitaeDatabase.inMemory(), backupsTo: "own")
        let chosen = folder.appendingPathComponent("Training", isDirectory: true)
        try FileManager.default.createDirectory(at: chosen, withIntermediateDirectories: true)
        let flow = BackupFlow()

        flow.chooseFolder()
        #expect(flow.isImporting)
        #expect(flow.importing == .folder)
        flow.picked(.success(chosen), model: model)
        #expect(model.backups.folderName == "Training")
        #expect(flow.notice == nil)

        flow.picked(.failure(CocoaError(.fileReadNoPermission)), model: model)
        #expect(flow.notice?.title == "Couldn't open that")
    }

    @Test func theDefaultRestOffersTheSetPickersLengths() {
        #expect(DefaultRestRow.choices(for: 90) == [0] + RestSection.choices)
        #expect(DefaultRestRow.choices(for: 0) == [0] + RestSection.choices)
        // A length from a backup that the pickers don't have is offered too, in its place.
        let withOdd = DefaultRestRow.choices(for: 100)
        #expect(withOdd.contains(100))
        #expect(withOdd == withOdd.sorted())
    }
}
