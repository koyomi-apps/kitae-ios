// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// The summary after a workout: when it opens, and what it counts.
@MainActor
struct WorkoutDoneTests {
    /// A model with a workout of the first day going and its first `done` sets done.
    private func workout(done: Int) throws -> (AppModel, Int64) {
        let model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        for set in workout.exercises.flatMap(\.sets).prefix(done) {
            workout.toggleDone(set.id)
        }
        return (model, workout.id)
    }

    @Test func theSummaryOpensOnceTheWorkoutHasClosed() throws {
        let (model, id) = try workout(done: 2)
        model.finishWorkout(.keepSkipped, addNewSets: false)
        // The workout's screen is still closing.
        #expect(model.finishedWorkout == nil)
        model.workoutClosed()
        #expect(model.finishedWorkout?.id == id)
    }

    @Test func settingsCanTurnItOff() throws {
        let (model, _) = try workout(done: 2)
        model.settings.showsWorkoutSummary = false
        model.finishWorkout(.keepSkipped, addNewSets: false)
        model.workoutClosed()
        #expect(model.finishedWorkout == nil)
    }

    @Test func noSummaryWithoutASetOrWhenAnotherDayStarts() throws {
        let (empty, _) = try workout(done: 0)
        empty.finishWorkout(.keepSkipped, addNewSets: false)
        empty.workoutClosed()
        #expect(empty.finishedWorkout == nil)

        let (switching, _) = try workout(done: 1)
        switching.finishWorkout(.keepSkipped, addNewSets: false, showsSummary: false)
        switching.workoutClosed()
        #expect(switching.finishedWorkout == nil)
    }

    @Test func aWorkoutsMusclesComeFromItsSetsButNotItsWarmUps() {
        let sets = [
            DoneSet(exerciseID: 21, libraryKey: "barbell-bench-press", target: "Chest", isIsometric: false, reps: 10, weightKg: 60, type: .standard),
            DoneSet(exerciseID: 21, libraryKey: "barbell-bench-press", target: "Chest", isIsometric: false, reps: 10, weightKg: 40, type: .warmup),
        ]
        let volume = MuscleVolume(doneSets: sets)
        #expect(volume.totalSets == 1)
        #expect(volume.sets[.chest] == 1 && volume.sets[.triceps] == 0.5)
    }

    @Test func theSettingIsSavedAndBackedUp() throws {
        let name = "WorkoutDoneTests-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let store = SettingsStore(defaults: defaults)
        var settings = AppSettings()
        #expect(settings.showsWorkoutSummary)
        settings.showsWorkoutSummary = false
        store.save(settings)
        #expect(store.load().showsWorkoutSummary == false)

        var restored = AppSettings()
        restored.apply(backup: settings.backupPreferences(planChosen: true))
        #expect(restored.showsWorkoutSummary == false)
    }
}
