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
        #expect(settings.showsWorkoutSummary && settings.showsNewBests)
        settings.showsWorkoutSummary = false
        settings.showsNewBests = false
        store.save(settings)
        #expect(store.load().showsWorkoutSummary == false)
        #expect(store.load().showsNewBests == false)

        var restored = AppSettings()
        restored.apply(backup: settings.backupPreferences(planChosen: true))
        #expect(restored.showsWorkoutSummary == false)
        #expect(restored.showsNewBests == false)
    }

    @Test func newBestsBeatEveryWorkoutBefore() {
        let bench = Exercise(id: 1, name: "Bench Press", target: "Chest")
        let history = [
            ExerciseSession(id: 4, date: 40, sets: [SetDraft(reps: 5, weightKg: 90)]),
            ExerciseSession(id: 3, date: 30, sets: [
                SetDraft(reps: 15, weightKg: 40, type: .warmup), SetDraft(reps: 6, weightKg: 100), SetDraft(reps: 9, weightKg: 80),
            ]),
            ExerciseSession(id: 2, date: 20, sets: [SetDraft(reps: 5, weightKg: 100), SetDraft(reps: 8, weightKg: 80)]),
            ExerciseSession(id: 1, date: 10, sets: [SetDraft(reps: 5, weightKg: 95), SetDraft(reps: 8, weightKg: 80)]),
        ]
        func records(_ id: Int64) -> [NewBest.Record] {
            NewBest.find(exercise: bench, history: history, workout: id).map(\.record)
        }
        // The first time there's nothing to beat, and a heavier set with the same reps counts once.
        #expect(records(1).isEmpty)
        #expect(records(2) == [.heaviest])
        let third = NewBest.find(exercise: bench, history: history, workout: 3)
        #expect(third.map(\.record) == [.heaviest, .mostReps])
        #expect(third[0].set == SetDraft(reps: 6, weightKg: 100) && third[0].previous == SetDraft(reps: 5, weightKg: 100))
        // The warm-up's 15 reps don't count.
        #expect(third[1].set == SetDraft(reps: 9, weightKg: 80) && third[1].previous == SetDraft(reps: 8, weightKg: 80))
        #expect(records(4).isEmpty)
    }

    @Test func newBestsWithoutWeightOrWithHelp() {
        // A weight left from before sit-ups stopped using weight doesn't count.
        let situps = Exercise(id: 2, name: "Sit-ups", target: "Core", usesWeight: false)
        let reps = [
            ExerciseSession(id: 2, date: 20, sets: [SetDraft(reps: 21, weightKg: 0)]),
            ExerciseSession(id: 1, date: 10, sets: [SetDraft(reps: 20, weightKg: 10)]),
        ]
        #expect(NewBest.find(exercise: situps, history: reps, workout: 2).map(\.record) == [.mostReps])
        let plank = Exercise(id: 3, name: "Plank", target: "Core", isIsometric: true, usesWeight: false)
        let holds = [
            ExerciseSession(id: 2, date: 20, sets: [SetDraft(reps: 75, weightKg: 0)]),
            ExerciseSession(id: 1, date: 10, sets: [SetDraft(reps: 60, weightKg: 0)]),
        ]
        #expect(NewBest.find(exercise: plank, history: holds, workout: 2).map(\.record) == [.longestHold])
        let assisted = Exercise(id: 4, name: "Assisted Pull-ups", target: "Lats", libraryKey: "assisted-pull-ups")
        let help = [
            ExerciseSession(id: 2, date: 20, sets: [SetDraft(reps: 8, weightKg: 25)]),
            ExerciseSession(id: 1, date: 10, sets: [SetDraft(reps: 8, weightKg: 30)]),
        ]
        let bests = NewBest.find(exercise: assisted, history: help, workout: 2)
        #expect(bests.map(\.record) == [.leastHelp])
        #expect(bests.first?.previous.weightKg == 30)
    }
}
