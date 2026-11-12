// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Starting, changing and finishing workouts in the database, checked against what
/// Kitae for Android stores.
struct SessionTests {
    static let today = 20_736
    static let now: Int64 = 1_791_640_000_000

    let database: KitaeDatabase
    let plan: Plan

    init() throws {
        database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
    }

    var pushA: TrainingDay { plan.days[0] }
    var pullA: TrainingDay { plan.days[1] }

    private func start(_ day: TrainingDay, on date: Int = today) throws -> Int64 {
        try database.write { db in try Sessions.start(db, dayID: day.id, today: date, now: Self.now) }
    }

    private func sets(_ sessionID: Int64) throws -> [Row] {
        try database.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM sets WHERE sessionId = ? ORDER BY `order`, id", arguments: [sessionID])
        }
    }

    private func exercises(_ sessionID: Int64, _ day: TrainingDay) throws -> [WorkoutExercise] {
        try database.read { db in try Sessions.exercises(db, sessionID: sessionID, dayID: day.id) }
    }

    private func plannedSets(_ day: TrainingDay) throws -> [DayExercise] {
        try database.read { db in try Plans.dayExercises(db, dayID: day.id) }
    }

    @Test func startCopiesTheDaysSets() throws {
        let id = try start(pushA)
        let session = try #require(try database.read { db in try Row.fetchOne(db, sql: "SELECT * FROM sessions WHERE id = ?", arguments: [id]) })
        #expect(session["date"] as Int == Self.today)
        #expect(session["planId"] as Int64 == plan.id)
        #expect(session["routineId"] as Int64 == pushA.id)
        #expect(session["isFinished"] as Bool == false)
        #expect(session["startedAt"] as Int64? == Self.now)
        #expect(session["finishedAt"] as Int64? == nil)

        let rows = try sets(id)
        let planned = pushA.exercises.flatMap(\.sets)
        #expect(rows.count == planned.count)
        #expect(rows.map { $0["order"] as Int } == Array(0..<planned.count))
        #expect(rows.map { $0["routineSetId"] as Int64 } == planned.map(\.id))
        #expect(rows.allSatisfy { $0["isCompleted"] as Bool == false && $0["rir"] as Int == 2 })
    }

    @Test func exercisesWithoutWeightGetNone() throws {
        // A day planned before leg raises stopped using weight, with Kitae for Android's 20 kg.
        try database.write { db in
            try db.execute(sql: """
                UPDATE routine_sets SET weight = 20
                WHERE routineExerciseId IN (SELECT id FROM routine_exercises WHERE exerciseId = 20)
                """)
        }
        let id = try start(pushA)
        let legRaises = try #require(try exercises(id, pushA).first { $0.exercise.libraryKey == "lying-leg-raises" })
        #expect(!legRaises.exercise.usesWeight)
        #expect(!legRaises.sets.isEmpty && legRaises.sets.allSatisfy { $0.weightKg == 0 })

        // Nor do sets added during the workout or to the plan.
        try database.write { db in
            _ = try Sessions.addSet(db, sessionID: id, exerciseID: 20, set: SetDraft(reps: 12, weightKg: 20))
        }
        #expect(try sets(id).filter { $0["exerciseId"] as Int64 == 20 }.allSatisfy { $0["weight"] as Double == 0 })
        let slot = try #require(pushA.exercises.first { $0.exercise.id == 20 })
        let planned = try database.write { db in try Plans.addSet(db, slotID: slot.id, set: SetDraft(reps: 10, weightKg: 20)) }
        #expect(try database.read { db in try Double.fetchOne(db, sql: "SELECT weight FROM routine_sets WHERE id = ?", arguments: [planned]) } == 0)
        // Exercises that use weight keep theirs.
        let bench = try #require(try exercises(id, pushA).first { $0.exercise.usesWeight })
        #expect(bench.sets.allSatisfy { $0.weightKg == 20 })
    }

    @Test func startingTheSameDayAgainCarriesOn() throws {
        let first = try start(pushA)
        #expect(try start(pushA) == first)
        #expect(try start(pushA, on: Self.today + 1) != first)
    }

    @Test func aWorkoutFromYesterdayIsStillGoing() throws {
        let id = try start(pushA, on: Self.today - 1)
        let active = try database.read { db in try Sessions.active(db, today: Self.today) }
        #expect(active?.id == id)
        #expect(active?.dayName == "Push A")
        let tooOld = try database.read { db in try Sessions.active(db, today: Self.today + 1) }
        #expect(tooOld == nil)
    }

    @Test func workoutListsTheDaysExercisesInOrder() throws {
        let id = try start(pushA)
        let list = try exercises(id, pushA)
        #expect(list.map(\.exercise.name) == pushA.exercises.map(\.exercise.name))
        #expect(list.allSatisfy { $0.sets.count == 3 && $0.sets.allSatisfy { !$0.isDone && !$0.isAdded } })
    }

    @Test func addedWarmupsGoFirst() throws {
        let id = try start(pushA)
        let exerciseID = pushA.exercises[0].exercise.id
        let warmup = try database.write { db in
            try Sessions.addSet(db, sessionID: id, exerciseID: exerciseID, set: SetDraft(reps: 15, weightKg: 10, type: .warmup))
        }
        let first = try #require(try exercises(id, pushA).first)
        #expect(first.sets.map(\.id).first == warmup)
        #expect(first.sets.map(\.type) == [.warmup, .standard, .standard, .standard])
        #expect(first.sets.first?.isAdded == true)
    }

    @Test func changingASetToAWarmupMovesIt() throws {
        let id = try start(pushA)
        var last = try #require(try exercises(id, pushA).first?.sets.last)
        last.type = .warmup
        try database.write { db in try Sessions.update(db, set: last) }
        let sets = try #require(try exercises(id, pushA).first?.sets)
        #expect(sets.first?.id == last.id)
        #expect(sets.map(\.type) == [.warmup, .standard, .standard])
    }

    @Test func finishingTakesWhatWasDoneIntoThePlan() throws {
        let id = try start(pushA)
        var set = try #require(try exercises(id, pushA).first?.sets.first)
        set.reps = 8
        set.weightKg = 22.5
        set.restSeconds = 120
        try database.write { db in
            try Sessions.update(db, set: set)
            try Sessions.setDone(db, setID: set.id, isDone: true)
            try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: Self.now + 60_000)
        }
        let planned = try #require(try plannedSets(pushA).first?.sets.first)
        #expect(planned.reps == 8)
        #expect(planned.weightKg == 22.5)
        #expect(planned.restSeconds == 120)

        let session = try #require(try database.read { db in try Row.fetchOne(db, sql: "SELECT * FROM sessions WHERE id = ?", arguments: [id]) })
        #expect(session["isFinished"] as Bool == true)
        #expect(session["finishedAt"] as Int64? == Self.now + 60_000)
        // Keep as skipped: the sets that weren't done stay, not done.
        let rows = try sets(id)
        #expect(rows.count == 15)
        #expect(rows.filter { $0["isCompleted"] as Bool }.count == 1)
    }

    @Test func removeFromPlanDropsWhatWasntDone() throws {
        let id = try start(pushA)
        let list = try exercises(id, pushA)
        try database.write { db in
            // Everything of the first exercise done, nothing of the others.
            for set in list[0].sets {
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            try Sessions.finish(db, sessionID: id, mode: .removeFromPlan, addNewSets: false, now: Self.now)
        }
        let day = try plannedSets(pushA)
        #expect(day.map(\.exercise.name) == ["Incline Bench"])
        #expect(try sets(id).count == 3)
    }

    @Test func discardModeRemovesAWorkoutWithNothingDone() throws {
        let id = try start(pushA)
        try database.write { db in
            try Sessions.finish(db, sessionID: id, mode: .discard, addNewSets: false, now: Self.now)
        }
        let session = try database.read { db in try Sessions.session(db, id: id) }
        #expect(session == nil)
        #expect(try plannedSets(pushA).flatMap(\.sets).count == 15)
    }

    @Test func addedSetsCanJoinTheDay() throws {
        let id = try start(pushA)
        let firstExercise = pushA.exercises[0].exercise.id
        // Face Pulls, from Pull A, isn't in Push A yet.
        let facePulls = try #require(pullA.exercises.first { $0.exercise.name == "Face Pulls" }?.exercise.id)
        try database.write { db in
            let extra = try Sessions.addSet(db, sessionID: id, exerciseID: firstExercise, set: SetDraft(reps: 6, weightKg: 25, type: .failure))
            let other = try Sessions.addSet(db, sessionID: id, exerciseID: facePulls, set: SetDraft(reps: 15, weightKg: 12.5, restSeconds: 60))
            try Sessions.setDone(db, setID: extra, isDone: true)
            try Sessions.setDone(db, setID: other, isDone: true)
            try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: true, now: Self.now)
        }
        let day = try plannedSets(pushA)
        #expect(day[0].sets.count == 4)
        #expect(day[0].sets.last?.type == .failure)
        #expect(day[0].sets.last?.reps == 6)
        #expect(day.last?.exercise.name == "Face Pulls")
        #expect(day.last?.sets.map(\.restSeconds) == [60])
        // The added sets now point at the plan's new sets.
        let unlinked = try database.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sets WHERE sessionId = ? AND isCompleted = 1 AND routineSetId IS NULL", arguments: [id])
        }
        #expect(unlinked == 0)
    }

    @Test func onlyThisWorkoutLeavesTheDayAlone() throws {
        let id = try start(pushA)
        try database.write { db in
            let extra = try Sessions.addSet(db, sessionID: id, exerciseID: pushA.exercises[0].exercise.id, set: SetDraft(reps: 6, weightKg: 25))
            try Sessions.setDone(db, setID: extra, isDone: true)
            try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: Self.now)
        }
        #expect(try plannedSets(pushA)[0].sets.count == 3)
    }

    @Test func discardRemovesTheWorkoutButNotThePlan() throws {
        let id = try start(pushA)
        let setID = try #require(try sets(id).first?["id"] as Int64?)
        try database.write { db in
            try Sessions.setDone(db, setID: setID, isDone: true)
            try Sessions.discard(db, sessionID: id)
        }
        #expect(try sets(id).isEmpty)
        #expect(try database.read { db in try Sessions.session(db, id: id) } == nil)
        #expect(try plannedSets(pushA).flatMap(\.sets).count == 15)
    }

    @Test func upNextIsTheDayAfterTheLastOneDone() throws {
        #expect(Plans.dayAfter(nil, in: plan.days)?.name == "Push A")
        #expect(Plans.dayAfter(plan.days[1].id, in: plan.days)?.name == "Legs")
        #expect(Plans.dayAfter(plan.days[4].id, in: plan.days)?.name == "Push A")

        let id = try start(pullA)
        // A workout with nothing done doesn't count.
        #expect(try database.read { db in try Sessions.lastDoneDayID(db) } == nil)
        let setID = try #require(try sets(id).first?["id"] as Int64?)
        try database.write { db in try Sessions.setDone(db, setID: setID, isDone: true) }
        #expect(try database.read { db in try Sessions.lastDoneDayID(db) } == pullA.id)
    }

    @Test func newSetsCopyThePlanWhenTheWorkoutHasNone() throws {
        let exercise = pushA.exercises[1].exercise.id
        let like = try database.read { db in try Sessions.lastKnownSet(db, exerciseID: exercise, dayID: pushA.id) }
        #expect(like?.reps == 12)
        #expect(like?.weightKg == 20)
        let unknown = try database.read { db in try Sessions.lastKnownSet(db, exerciseID: 75, dayID: pushA.id) }
        #expect(unknown == nil)
    }
}

/// The app's model on top of the database: what Today shows and what Finish does.
@MainActor
struct AppModelTests {
    let model: AppModel

    init() throws {
        model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
    }

    @Test func todayOffersTheFirstDayAtFirst() {
        #expect(model.currentPlan?.name == "Push Pull Leg")
        #expect(model.selectedDay?.name == "Push A")
        #expect(model.hasDoneSets == false)
        #expect(model.workout == nil)
    }

    @Test func aNewDatabaseAsksForAPlan() throws {
        let fresh = AppModel.testing(try KitaeDatabase.inMemory())
        #expect(fresh.currentPlan == nil)
        #expect(fresh.selectedDay == nil)
        #expect(fresh.plans.map(\.name) == ["Push Pull Leg"])
        #expect(fresh.isChoosingPlan == false)
        fresh.askForPlanIfNeeded()
        #expect(fresh.isChoosingPlan)
        fresh.selectPlan(fresh.plans[0].id)
        #expect(fresh.isChoosingPlan == false)
        #expect(fresh.selectedDay?.name == "Push A")

        // With a plan, nothing opens by itself.
        model.askForPlanIfNeeded()
        #expect(model.isChoosingPlan == false)
    }

    @Test func finishingMovesOnToTheNextDay() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        #expect(model.isWorkoutPresented)
        #expect(workout.dayName == "Push A")
        #expect(workout.setCount == 15)

        let first = try #require(workout.exercises.first?.sets.first)
        workout.toggleDone(first.id)
        #expect(workout.doneCount == 1)
        #expect(workout.rest.isActive)

        model.finishWorkout(.keepSkipped, addNewSets: false)
        #expect(model.workout == nil)
        #expect(model.isWorkoutPresented == false)
        #expect(model.hasDoneSets)
        #expect(model.selectedDay?.name == "Pull A")
    }

    @Test func aWorkoutInProgressComesBackAfterARestart() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        workout.update(try #require(workout.exercises.first?.sets.first?.id)) { $0.weightKg = 18.75 }

        let reopened = AppModel.testing(model.database)
        let restored = try #require(reopened.workout)
        #expect(restored.id == workout.id)
        #expect(restored.exercises.first?.sets.first?.weightKg == 18.75)
        #expect(reopened.selectedDay?.id == day.id)
    }

    @Test func addingASetCopiesTheLastOne() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        let exercise = try #require(workout.exercises.first)
        workout.update(try #require(exercise.sets.last?.id)) { $0.reps = 7 }
        workout.addSet(to: exercise.id)
        let sets = try #require(workout.exercises.first?.sets)
        #expect(sets.count == 4)
        #expect(sets.last?.reps == 7)
        #expect(sets.last?.isAdded == true)
        #expect(workout.addedDoneCount == 0)
        workout.toggleDone(try #require(sets.last?.id))
        #expect(workout.addedDoneCount == 1)
    }

    @Test func theLastSetDoesntStartARest() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        let all = workout.exercises.flatMap(\.sets)
        for set in all.dropLast() {
            workout.toggleDone(set.id)
        }
        workout.skipRest()
        workout.toggleDone(try #require(all.last?.id))
        #expect(workout.notDoneCount == 0)
        #expect(workout.rest.isActive == false)
    }

    @Test func discardingKeepsTheSameDayUpNext() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))
        model.discardWorkout()
        #expect(model.workout == nil)
        #expect(model.hasDoneSets == false)
        #expect(model.selectedDay?.id == day.id)
    }

    @Test func pickingAnotherPlanStartsFromItsFirstDay() throws {
        let id = try model.database.write { db in
            try db.execute(sql: "INSERT INTO plans (name) VALUES ('Upper Lower')")
            let planID = db.lastInsertedRowID
            try db.execute(sql: "INSERT INTO routines (planId, name, position) VALUES (?, 'Upper', 0)", arguments: [planID])
            return planID
        }
        model.selectPlan(id)
        #expect(model.currentPlan?.name == "Upper Lower")
        #expect(model.selectedDay?.name == "Upper")
        #expect(model.plans.map(\.name) == ["Push Pull Leg", "Upper Lower"])
    }
}
