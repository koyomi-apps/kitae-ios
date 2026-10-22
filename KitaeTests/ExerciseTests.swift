// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Exercises: their details, making and changing them, and how they've gone.
struct ExerciseTests {
    static let today = 20_736

    let database: KitaeDatabase
    let plan: Plan

    init() throws {
        database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
    }

    /// Does a workout of `day` on `date` with only `bench`'s first sets changed to `sets`
    /// and done, then finishes it.
    @discardableResult
    private func workout(on date: Int, bench sets: [SetDraft]) throws -> Int64 {
        let day = plan.days[0]
        return try database.write { db in
            let id = try Sessions.start(db, dayID: day.id, today: date, now: Int64(date) * 86_400_000)
            let planned = try #require(try Sessions.exercises(db, sessionID: id, dayID: day.id).first?.sets)
            for (set, draft) in zip(planned, sets) {
                var changed = set
                changed.reps = draft.reps
                changed.weightKg = draft.weightKg
                changed.type = draft.type
                try Sessions.update(db, set: changed)
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: Int64(date) * 86_400_000 + 3_600_000)
            return id
        }
    }

    private var benchID: Int64 { plan.days[0].exercises[0].exercise.id }

    @Test func stepsLeaveOutTypedNumbers() {
        let exercise = Exercise(id: 1, name: "Bench", target: "Chest", instructions: "1. Lie back\n2) Press it up\n\n   Keep your feet down  \n10. Rack it")
        #expect(exercise.steps == ["Lie back", "Press it up", "Keep your feet down", "Rack it"])
        #expect(Exercise(id: 2, name: "Plank", target: "Core").steps.isEmpty)
    }

    @Test func referencesHaveToBeWebAddresses() {
        #expect(Exercises.savedReference("  youtu.be/abc ") == "https://youtu.be/abc")
        #expect(Exercises.savedReference("http://example.com") == "http://example.com")
        #expect(Exercises.savedReference("   ") == nil)
        func url(_ reference: String) -> URL? {
            Exercise(id: 1, name: "", target: "", reference: Exercises.savedReference(reference)).referenceURL
        }
        #expect(url("youtu.be/abc")?.absoluteString == "https://youtu.be/abc")
        #expect(url("not a link") == nil)
        #expect(url("ftp://example.com") == nil)
        #expect(url("localhost") == nil)
    }

    @Test func newExercisesNeedANameOfTheirOwn() throws {
        try database.write { db in
            var draft = ExerciseDraft(name: "  Cable Fly ", muscle: .chest)
            draft.isIsometric = false
            draft.restSeconds = 60
            draft.instructions = "Pull the handles together.\n"
            draft.reference = "example.com/fly"
            let id = try Exercises.create(db, draft)
            let saved = try #require(try Exercises.exercise(db, id: id))
            #expect(saved.name == "Cable Fly")
            #expect(saved.muscle == .chest)
            #expect(saved.usesWeight)
            #expect(saved.restSeconds == 60)
            #expect(saved.instructions == "Pull the handles together.")
            #expect(saved.reference == "https://example.com/fly")

            #expect(throws: NameProblem.exerciseTaken) { try Exercises.create(db, ExerciseDraft(name: "cable fly")) }
            #expect(throws: NameProblem.empty) { try Exercises.create(db, ExerciseDraft(name: "  ")) }
            #expect(throws: NameProblem.exerciseTaken) {
                try Exercises.update(db, id: id, ExerciseDraft(name: "Bench Press"))
            }

            var renamed = ExerciseDraft(saved)
            renamed.name = "Cable Crossover Fly"
            renamed.muscle = .shoulders
            renamed.isIsometric = true
            renamed.usesWeight = false
            renamed.reference = ""
            try Exercises.update(db, id: id, renamed)
            let updated = try #require(try Exercises.exercise(db, id: id))
            #expect(updated.name == "Cable Crossover Fly")
            #expect(updated.target == "Shoulders")
            #expect(updated.isIsometric)
            #expect(!updated.usesWeight && !ExerciseDraft(updated).usesWeight)
            #expect(updated.reference == nil)
            #expect(try Exercises.all(db).count == Seed.bundled().exercises.count + 1)
        }
    }

    @Test func deletingAnExerciseTakesItOutOfPlansAndWorkouts() throws {
        let id = try workout(on: Self.today - 2, bench: [SetDraft(reps: 10, weightKg: 22.5)])
        try database.write { db in try Exercises.delete(db, id: benchID) }
        let (day, sets, exercises) = try database.read { db in
            (
                try Plans.dayExercises(db, dayID: plan.days[0].id),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sets WHERE sessionId = ? AND exerciseId = ?", arguments: [id, benchID]),
                try Exercises.all(db)
            )
        }
        #expect(day.map(\.exercise.name) == ["Chest Fly", "Leg Raises", "Overhead Extensions", "Tricep Push Down"])
        #expect(sets == 0)
        #expect(try exercises.count == Seed.bundled().exercises.count - 1)
    }

    @Test func historyIsNewestFirstAndLeavesOutSetsNotDone() throws {
        let older = try workout(on: Self.today - 7, bench: [SetDraft(reps: 12, weightKg: 20), SetDraft(reps: 10, weightKg: 20)])
        let newer = try workout(on: Self.today - 3, bench: [
            SetDraft(reps: 15, weightKg: 10, type: .warmup),
            SetDraft(reps: 10, weightKg: 22.5),
            SetDraft(reps: 8, weightKg: 22.5),
        ])
        let history = try database.read { db in try Exercises.history(db, exerciseID: benchID) }
        #expect(history.map(\.id) == [newer, older])
        #expect(history.first?.dayName == "Push A")
        #expect(history.first?.sets.map(\.reps) == [15, 10, 8])
        // The third set of the older workout wasn't done.
        #expect(history.last?.sets.count == 2)

        let newest = try #require(history.first)
        #expect(newest.heaviest == SetDraft(reps: 10, weightKg: 22.5))
        #expect(newest.mostReps == 10)

        let last = try database.read { db in try Exercises.lastSessions(db) }
        #expect(last[benchID]?.id == newer)
        #expect(last[benchID]?.sets.count == 3)
        #expect(last[plan.days[1].exercises[0].exercise.id] == nil)
    }

    @Test func recentExercisesAreTheNewestDone() throws {
        try workout(on: Self.today - 7, bench: [SetDraft(reps: 10, weightKg: 20)])
        let day = plan.days[1]
        let date = Self.today - 3
        let (done, skipped) = try database.write { db -> (Int64, Int64) in
            let id = try Sessions.start(db, dayID: day.id, today: date, now: Int64(date) * 86_400_000)
            let exercises = try Sessions.exercises(db, sessionID: id, dayID: day.id)
            let set = try #require(exercises[1].sets.first)
            try Sessions.setDone(db, setID: set.id, isDone: true)
            try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: Int64(date) * 86_400_000 + 3_600_000)
            return (exercises[1].id, exercises[0].id)
        }
        let recent = try database.read { db in try Exercises.recentIDs(db, limit: 5) }
        // The day's first exercise had no set done, so it isn't recent.
        #expect(recent == [done, benchID])
        #expect(!recent.contains(skipped))
        #expect(try database.read { db in try Exercises.recentIDs(db, limit: 1) } == [done])
    }

    @Test func aWorkoutsNewBestsAreReadFromItsSets() throws {
        let first = try workout(on: Self.today - 7, bench: [SetDraft(reps: 10, weightKg: 20)])
        let second = try workout(on: Self.today - 3, bench: [SetDraft(reps: 10, weightKg: 22.5)])
        let (none, bests) = try database.read { db in
            (try Exercises.newBests(db, workoutID: first), try Exercises.newBests(db, workoutID: second))
        }
        #expect(none.isEmpty)
        #expect(bests.map(\.exercise.id) == [benchID])
        #expect(bests.map(\.record) == [.heaviest])
        #expect(bests.first?.set.weightKg == 22.5 && bests.first?.previous.weightKg == 20)
    }

    @Test func warmupsCountOnlyWhenNothingElseWasDone() {
        let warmups = ExerciseSession(id: 1, date: 0, sets: [
            SetDraft(reps: 15, weightKg: 10, type: .warmup),
            SetDraft(reps: 12, weightKg: 12.5, type: .warmup),
        ])
        #expect(warmups.heaviest?.weightKg == 12.5)
        #expect(warmups.mostReps == 15)
    }

    @Test func progressFollowsTheHeaviestSetOrTheReps() {
        let sessions = (0..<14).map { index in
            ExerciseSession(id: Int64(index), date: 20_000 + index, sets: [SetDraft(reps: 8 + index % 3, weightKg: 20 + Double(index) * 2.5)])
        }.reversed()
        let progress = ExerciseProgress(history: Array(sessions), isIsometric: false)
        #expect(progress.measure == .weight)
        #expect(progress.points.count == 12)
        #expect(progress.points.first?.id == 2)
        #expect(progress.points.last?.value == 52.5)
        #expect(progress.change == 27.5)

        let bodyweight = [
            ExerciseSession(id: 2, date: 2, sets: [SetDraft(reps: 12, weightKg: 0)]),
            ExerciseSession(id: 1, date: 1, sets: [SetDraft(reps: 9, weightKg: 0)]),
        ]
        let reps = ExerciseProgress(history: bodyweight, isIsometric: false)
        #expect(reps.measure == .reps)
        #expect(reps.points.map(\.value) == [9, 12])
        #expect(ExerciseProgress(history: bodyweight, isIsometric: true).measure == .seconds)

        // An exercise that doesn't use weight follows its reps, even with weight from before.
        let weightless = ExerciseProgress(history: Array(sessions), isIsometric: false, usesWeight: false)
        #expect(weightless.measure == .reps)
        #expect(weightless.points.last?.value == 9)
        #expect(sessions.first?.best(usesWeight: false) == SetDraft(reps: 9, weightKg: 52.5))

        let records = ExerciseRecords(history: Array(sessions))
        #expect(records.best?.weightKg == 52.5)
        #expect(records.mostReps == SetDraft(reps: 10, weightKg: 47.5))
        #expect(records.workoutCount == 14)
    }

    @Test func lessHelpIsProgressOnAnAssistedMachine() {
        // Assisted dips with 30, then 25, then 20 kg of help.
        let sessions = [
            ExerciseSession(id: 3, date: 3, sets: [SetDraft(reps: 8, weightKg: 20), SetDraft(reps: 10, weightKg: 25)]),
            ExerciseSession(id: 2, date: 2, sets: [SetDraft(reps: 8, weightKg: 25)]),
            ExerciseSession(id: 1, date: 1, sets: [SetDraft(reps: 8, weightKg: 30)]),
        ]
        #expect(sessions.first?.best(usesWeight: true, assisted: true) == SetDraft(reps: 8, weightKg: 20))
        let progress = ExerciseProgress(history: sessions, isIsometric: false, assisted: true)
        #expect(progress.measure == .help)
        #expect(progress.points.map(\.value) == [30, 25, 20])
        #expect(progress.change == -10)
        let records = ExerciseRecords(history: sessions, assisted: true)
        #expect(records.best == SetDraft(reps: 8, weightKg: 20))
        #expect(records.bestDate == 3)
        #expect(records.mostReps == SetDraft(reps: 10, weightKg: 25))
        // Kitae's assisted dips and pull-ups are assisted; its dips and people's own exercises aren't.
        #expect(Exercise(id: 1, name: "Assisted Dips", target: "Triceps", libraryKey: "assisted-dips").isAssisted)
        #expect(Exercise(id: 4, name: "Assisted Pull-ups", target: "Lats", libraryKey: "assisted-pull-ups").isAssisted)
        #expect(!Exercise(id: 2, name: "Dips", target: "Triceps", libraryKey: "dips").isAssisted)
        #expect(!Exercise(id: 3, name: "Assisted Dips", target: "Triceps").isAssisted)
    }

    @Test func chartAxesUseRoundSteps() {
        let weights = ChartScale(values: [15, 17.5, 20, 22.5])
        #expect(weights.ticks == [15, 17.5, 20, 22.5])
        let flat = ChartScale(values: [20, 20])
        #expect(flat.ticks == [15, 20, 25])
        let reps = ChartScale(values: [8, 10, 12], minimumStep: 1)
        #expect(reps.ticks == [8, 10, 12])
        let small = ChartScale(values: [1, 1], minimumStep: 1)
        #expect(small.lower == 0)
        #expect(small.ticks.allSatisfy { $0 == $0.rounded() })
    }

    @Test func groupsFollowTheSearchAndMuscle() throws {
        let all = try database.read { db in try Exercises.all(db) }
        let presses = exerciseGroups(all, muscle: nil, search: " press")
        #expect(presses.allSatisfy { group in group.exercises.allSatisfy { $0.name.localizedCaseInsensitiveContains("press") } })
        #expect(presses.first?.muscle == .chest)
        let chest = exerciseGroups(all, muscle: .chest, search: "")
        #expect(chest.map(\.muscle) == [.chest])
        let other = exerciseGroups([Exercise(id: 1, name: "Rowing", target: "Cardio")], muscle: nil, search: "")
        #expect(other.map(\.title) == ["Other"])
    }

    @Test func heaviestSetsShowTheirWeightFirst() {
        let formatter = WeightFormatter(decimals: .point)
        #expect(formatter.weightFirst(reps: 10, kilograms: 22.5) == "22.5 kg × 10")
        #expect(formatter.weightFirst(reps: 45, kilograms: 10, isIsometric: true) == "10.0 kg × 45s")
        #expect(formatter.weightFirst(reps: 12, kilograms: 0) == "12 reps")
        #expect(formatter.weightFirst(reps: 45, kilograms: 0, isIsometric: true) == "45s")
        let english = Locale(identifier: "en_US")
        #expect(HistoryDates.short(Self.today - 3, today: Self.today, locale: english) == "Wed, Oct 7")
        #expect(HistoryDates.dayAndMonth(Self.today - 3, locale: english) == "Oct 7")
    }
}

/// Changing exercises from the app, and keeping the workout in progress whole.
@MainActor
struct ExerciseModelTests {
    let model: AppModel

    init() throws {
        model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
    }

    @Test func exercisesOfTheWorkoutStay() throws {
        model.startWorkout(try #require(model.selectedDay))
        let workout = try #require(model.workout)
        let bench = try #require(workout.exercises.first?.exercise)
        #expect(model.canDeleteExercise(bench.id) == false)
        model.deleteExercise(bench.id)
        #expect(model.workout?.exercises.first?.exercise.id == bench.id)

        let squats = try #require(model.currentPlan?.days[2].exercises.first?.exercise)
        #expect(model.canDeleteExercise(squats.id))
    }

    @Test func renamingAnExerciseReachesThePlanAndTheWorkout() throws {
        model.startWorkout(try #require(model.selectedDay))
        let bench = try #require(model.workout?.exercises.first?.exercise)
        var draft = ExerciseDraft(bench)
        draft.name = "Incline Barbell Bench"
        let before = model.historyRevision
        try model.saveExercise(draft, id: bench.id)
        #expect(model.workout?.exercises.first?.exercise.name == "Incline Barbell Bench")
        #expect(model.currentPlan?.days.first?.exercises.first?.exercise.name == "Incline Barbell Bench")
        #expect(model.historyRevision > before)
        #expect(throws: NameProblem.exerciseTaken) { try model.saveExercise(ExerciseDraft(name: "Squats"), id: nil) }
    }

    @Test func deletingAnExerciseLeavesItsDays() throws {
        let squats = try #require(model.currentPlan?.days[2].exercises.first?.exercise)
        model.deleteExercise(squats.id)
        #expect(model.currentPlan?.days[2].exercises.map(\.exercise.name) == ["Stiff Legged Deadlift", "Lunges", "Shoulder Press", "Lateral Raises"])
        #expect(model.plans.first?.exerciseCount == 22)
    }
}
