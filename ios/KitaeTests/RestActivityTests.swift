// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// The rest timer outside Kitae: what the Lock Screen shows, its buttons, and keeping a
/// rest while Kitae is closed.
@MainActor
struct RestActivityTests {
    let store = RestTimerStore(defaults: UserDefaults(suiteName: "RestActivityTests-\(UUID().uuidString)"))
    let model: AppModel

    init() throws {
        model = AppModel.testing(try KitaeDatabase.followingStarterPlan(), restStore: store)
    }

    private func startResting() throws -> Workout {
        model.startWorkout(try #require(model.selectedDay))
        let workout = try #require(model.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        return workout
    }

    @Test func lockScreenShowsTheRestAndWhatsNext() throws {
        let workout = try startResting()
        let state = try #require(LiveRestActivity.state(for: workout, formatter: WeightFormatter(decimals: .point)))
        #expect(state.endsAt == workout.rest.endsAt)
        #expect(state.total == 90)
        #expect(state.doneSets == 1)
        #expect(state.setCount == 15)
        #expect(state.nextExercise == "Incline Bench")
        #expect(state.nextSet == "12 × 20.0 kg")
    }

    @Test func nothingShowsWithoutARest() throws {
        let workout = try startResting()
        workout.skipRest()
        #expect(LiveRestActivity.state(for: workout, formatter: WeightFormatter()) == nil)
    }

    @Test func setsAreDescribedForTheLockScreen() {
        let point = WeightFormatter(decimals: .point)
        let set = WorkoutSet(id: 1, type: .standard, reps: 10, weightKg: 17.5)
        let bench = Exercise(id: 1, name: "Bench Press", target: "Chest")
        let plank = Exercise(id: 2, name: "Plank", target: "Core", isIsometric: true)
        #expect(LiveRestActivity.describe(set, of: bench, formatter: point) == "10 × 17.5 kg")
        #expect(LiveRestActivity.describe(WorkoutSet(id: 2, type: .standard, reps: 8, weightKg: 0), of: bench, formatter: point) == "8 reps")
        #expect(LiveRestActivity.describe(WorkoutSet(id: 3, type: .standard, reps: 45, weightKg: 0), of: plank, formatter: point) == "45s")
    }

    @Test func lockScreenButtonsReachTheWorkout() async throws {
        let workout = try startResting()
        await model.perform(.pause)
        #expect(workout.rest.isPaused)
        let paused = workout.rest.remaining(at: .now)
        await model.perform(.add(seconds: 15))
        #expect(abs(workout.rest.remaining(at: .now) - (paused + 15)) < 0.01)
        await model.perform(.resume)
        #expect(workout.rest.isRunning)
        await model.perform(.add(seconds: -1_000))
        #expect(workout.rest.isActive == false)

        workout.startRest(seconds: 60)
        await model.perform(.skip)
        #expect(workout.rest.isActive == false)
    }

    @Test func aRestCarriesOnAfterKitaeCloses() throws {
        let workout = try startResting()
        let endsAt = try #require(workout.rest.endsAt)
        let reopened = AppModel.testing(model.database, restStore: store)
        let restored = try #require(reopened.workout)
        #expect(restored.rest.endsAt == endsAt)

        workout.pauseRest()
        let pausedAgain = AppModel.testing(model.database, restStore: store)
        #expect(pausedAgain.workout?.rest.isPaused == true)

        workout.skipRest()
        let afterSkip = AppModel.testing(model.database, restStore: store)
        #expect(afterSkip.workout?.rest.isActive == false)
    }

    @Test func aRestThatRanOutIsntKept() {
        var rest = RestTimer()
        let start = Date(timeIntervalSince1970: 1_000_000)
        rest.start(seconds: 60, at: start)
        store.save(rest, workoutID: 7, workoutStartedAt: start)
        #expect(store.load(workoutID: 7, workoutStartedAt: start, now: start.addingTimeInterval(30)) == rest)
        #expect(store.load(workoutID: 7, workoutStartedAt: start, now: start.addingTimeInterval(61)) == nil)
        #expect(store.load(workoutID: 8, workoutStartedAt: start, now: start) == nil)
        #expect(store.load(workoutID: 7, workoutStartedAt: start.addingTimeInterval(600), now: start) == nil)
    }

    @Test func clockRoundsUpLikeACountdown() {
        #expect(restClock(72) == "1:12")
        #expect(restClock(59.2) == "1:00")
        #expect(restClock(0) == "0:00")
        #expect(restClock(-3) == "0:00")
        #expect(restClock(600) == "10:00")
    }
}
