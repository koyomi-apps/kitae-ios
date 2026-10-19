// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Changing plans in the database, the way Kitae for Android's plan editor does.
struct PlanEditTests {
    static let today = 20_736

    let database: KitaeDatabase
    let plan: Plan

    init() throws {
        database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
    }

    /// The plan as it's saved now.
    private func saved() throws -> Plan {
        try #require(try database.read { db in try Plans.plan(db, id: plan.id) })
    }

    @Test func plansNeedANameOfTheirOwn() throws {
        try database.write { db in
            #expect(throws: NameProblem.empty) { try Plans.create(db, name: "   ") }
            #expect(throws: NameProblem.taken) { try Plans.create(db, name: "Push Pull Leg") }
            let id = try Plans.create(db, name: "  Upper Lower ")
            #expect(try String.fetchOne(db, sql: "SELECT name FROM plans WHERE id = ?", arguments: [id]) == "Upper Lower")
            // Keeping its own name is fine; taking another plan's isn't.
            try Plans.rename(db, planID: id, to: "Upper Lower")
            #expect(throws: NameProblem.taken) { try Plans.rename(db, planID: id, to: "Push Pull Leg") }
        }
    }

    @Test func newDaysGoAtTheEnd() throws {
        let id = try database.write { db in try Plans.addDay(db, planID: plan.id, name: " Arms ") }
        let days = try saved().days
        #expect(days.map(\.name) == ["Push A", "Pull A", "Legs", "Push B", "Pull B", "Arms"])
        #expect(days.last?.id == id)
        #expect(throws: NameProblem.empty) {
            try database.write { db in try Plans.addDay(db, planID: plan.id, name: "") }
        }
    }

    @Test func daysCanBeRenamedAndReordered() throws {
        try database.write { db in
            try Plans.renameDay(db, dayID: plan.days[2].id, to: "Leg Day")
            try Plans.orderDays(db, plan.days.map(\.id).reversed())
        }
        #expect(try saved().days.map(\.name) == ["Pull B", "Push B", "Leg Day", "Pull A", "Push A"])
    }

    @Test func deletingADayKeepsItsWorkouts() throws {
        let pushA = plan.days[0]
        let sessionID = try database.write { db in
            try Sessions.start(db, dayID: pushA.id, today: Self.today, now: 0)
        }
        try database.write { db in try Plans.deleteDay(db, dayID: pushA.id) }
        #expect(try saved().days.map(\.name) == ["Pull A", "Legs", "Push B", "Pull B"])

        let session = try #require(try database.read { db in
            try Row.fetchOne(db, sql: "SELECT * FROM sessions WHERE id = ?", arguments: [sessionID])
        })
        #expect(session["routineId"] as Int64? == nil)
        #expect(session["planId"] as Int64? == plan.id)
        let (sets, linked, plannedSets) = try database.read { db in
            (
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sets WHERE sessionId = ?", arguments: [sessionID]),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sets WHERE sessionId = ? AND routineSetId IS NOT NULL", arguments: [sessionID]),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM routine_sets")
            )
        }
        #expect(sets == 15)
        #expect(linked == 0)
        #expect(plannedSets == 72 - 15)
    }

    @Test func newExercisesStartFromTheLastWorkout() throws {
        let fallback = SetDraft(reps: 12, weightKg: 20)
        // Squats were never done: three sets like the fallback.
        let squats = plan.days[2].exercises[0].exercise.id
        let fresh = try database.read { db in try Plans.suggestedSets(db, exerciseID: squats, fallback: fallback) }
        #expect(fresh == Array(repeating: fallback, count: 3))

        // Two sets of Incline Bench done in a workout of Push A…
        let pushA = plan.days[0]
        let bench = pushA.exercises[0].exercise.id
        let sessionID = try database.write { db in try Sessions.start(db, dayID: pushA.id, today: Self.today, now: 0) }
        let sets = try #require(try database.read { db in
            try Sessions.exercises(db, sessionID: sessionID, dayID: pushA.id)
        }.first?.sets)
        try database.write { db in
            var first = sets[0]
            first.reps = 8
            first.weightKg = 25
            try Sessions.update(db, set: first)
            try Sessions.setDone(db, setID: sets[0].id, isDone: true)
            try Sessions.setDone(db, setID: sets[1].id, isDone: true)
            try Sessions.finish(db, sessionID: sessionID, mode: .discard, addNewSets: false, now: 1)
        }
        let suggested = try database.read { db in try Plans.suggestedSets(db, exerciseID: bench, fallback: fallback) }
        #expect(suggested.map(\.reps) == [8, 12])
        #expect(suggested.map(\.weightKg) == [25, 20])

        // …are what it starts with in Pull A.
        let pullA = plan.days[1]
        let slotID = try database.write { db in
            try Plans.addExercise(db, dayID: pullA.id, exerciseID: bench, sets: suggested)
        }
        let added = try #require(try saved().days[1].exercises.last)
        #expect(added.id == slotID)
        #expect(added.exercise.name == "Incline Bench")
        #expect(added.sets.map(\.reps) == [8, 12])
    }

    @Test func plannedSetsKeepWarmupsFirst() throws {
        let slot = plan.days[0].exercises[0]
        let warmup = try database.write { db in
            try Plans.addSet(db, slotID: slot.id, set: SetDraft(reps: 15, weightKg: 10, type: .warmup))
        }
        var sets = try saved().days[0].exercises[0].sets
        #expect(sets.first?.id == warmup)
        #expect(sets.map(\.type) == [.warmup, .standard, .standard, .standard])

        // Turning the last set into a warm-up moves it up as well.
        let last = try #require(sets.last)
        try database.write { db in
            try Plans.updateSet(db, setID: last.id, set: SetDraft(reps: 10, weightKg: 12.5, type: .warmup, restSeconds: 45))
        }
        sets = try saved().days[0].exercises[0].sets
        #expect(sets.map(\.type) == [.warmup, .warmup, .standard, .standard])
        #expect(sets[1].id == last.id)
        #expect(sets[1].reps == 10)
        #expect(sets[1].restSeconds == 45)

        try database.write { db in try Plans.deleteSet(db, setID: warmup) }
        #expect(try saved().days[0].exercises[0].sets.count == 3)
    }

    @Test func exercisesCanBeReorderedAndRemoved() throws {
        let ids = plan.days[0].exercises.map(\.id)
        try database.write { db in
            try Plans.orderExercises(db, [ids[4], ids[0], ids[1], ids[2], ids[3]])
            try Plans.removeExercise(db, slotID: ids[2])
        }
        let day = try saved().days[0]
        #expect(day.exercises.map(\.exercise.name) == ["Tricep Push Down", "Incline Bench", "Chest Fly", "Overhead Extensions"])
        let leftOver = try database.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM routine_sets WHERE routineExerciseId = ?", arguments: [ids[2]])
        }
        #expect(leftOver == 0)
    }

    @Test func deletingAPlanKeepsItsWorkouts() throws {
        let sessionID = try database.write { db in
            try Sessions.start(db, dayID: plan.days[0].id, today: Self.today, now: 0)
        }
        try database.write { db in try Plans.delete(db, planID: plan.id) }
        let (current, plans, setCount) = try database.read { db in
            (
                try Plans.currentID(db),
                try Plans.all(db),
                try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sets WHERE sessionId = ?", arguments: [sessionID])
            )
        }
        #expect(current == nil)
        #expect(plans.isEmpty)
        #expect(setCount == 15)
        let session = try #require(try database.read { db in
            try Row.fetchOne(db, sql: "SELECT * FROM sessions WHERE id = ?", arguments: [sessionID])
        })
        #expect(session["planId"] as Int64? == nil)
        #expect(session["routineId"] as Int64? == nil)
    }

    @Test func aPlanNeedsAnExercise() throws {
        let empty = try database.write { db in try Plans.create(db, name: "Empty") }
        let (emptyHasSome, starterHasSome) = try database.read { db in
            (try Plans.hasExercises(db, planID: empty), try Plans.hasExercises(db, planID: plan.id))
        }
        #expect(emptyHasSome == false)
        #expect(starterHasSome)
    }

    @Test func exercisesAreListedByName() throws {
        let all = try database.read { db in try Exercises.all(db) }
        #expect(try all.count == Seed.bundled().exercises.count)
        // SQLite's NOCASE only folds ASCII letters, like lowercased() does for these names.
        let names = all.map { $0.name.lowercased() }
        #expect(names == names.sorted())
    }
}

/// The plan editor on top of the database, and how its changes reach Today and the workout.
@MainActor
struct PlanEditorTests {
    let model: AppModel

    init() throws {
        model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
    }

    private func makeEditor(day index: Int? = 0) throws -> PlanEditor {
        let plan = try #require(model.currentPlan)
        return model.planEditor(for: PlanEditRoute(planID: plan.id, dayID: index.map { plan.days[$0].id }))
    }

    @Test func opensOnTheDayAskedFor() throws {
        #expect(try makeEditor(day: 2).selectedDay?.name == "Legs")
        #expect(try makeEditor(day: nil).selectedDay?.name == "Push A")
    }

    @Test func newDaysAreShownAndReachToday() throws {
        let editor = try makeEditor()
        #expect(editor.unusedDayNames.first == "Push")
        try editor.addDay(named: "Arms")
        #expect(editor.selectedDay?.name == "Arms")
        #expect(model.days.last?.name == "Arms")
        #expect(editor.unusedDayNames.contains("Arms") == false)
        #expect(throws: NameProblem.empty) { try editor.addDay(named: " ") }
        try editor.renameSelectedDay(to: "Arms & Abs")
        #expect(model.days.last?.name == "Arms & Abs")
    }

    @Test func deletingADayShowsTheOneInItsPlace() throws {
        let editor = try makeEditor(day: 4)
        editor.deleteSelectedDay()
        #expect(editor.selectedDay?.name == "Push B")
        editor.selectedDayID = editor.days[0].id
        editor.deleteSelectedDay()
        #expect(editor.selectedDay?.name == "Pull A")
        #expect(model.days.map(\.name) == ["Pull A", "Legs", "Push B"])
    }

    @Test func daysMoveEarlierAndLater() throws {
        let editor = try makeEditor()
        #expect(editor.canMoveSelectedDay(by: -1) == false)
        editor.moveSelectedDay(by: 1)
        #expect(editor.days.map(\.name) == ["Pull A", "Push A", "Legs", "Push B", "Pull B"])
        #expect(editor.selectedDay?.name == "Push A")
        #expect(model.days.first?.name == "Pull A")
    }

    @Test func addedExercisesComeWithSets() throws {
        let editor = try makeEditor()
        let squats = try #require(model.days[2].exercises.first?.exercise)
        let slotID = try #require(editor.addExercise(squats))
        let added = try #require(editor.exercise(slotID))
        #expect(added.sets.count == 3)
        #expect(added.sets.allSatisfy { $0.reps == 12 && $0.weightKg == 20 && $0.type == .standard })
        #expect(model.selectedDay?.exercises.last?.exercise.name == "Squats")
    }

    @Test func addedSetsCopyTheLastOne() throws {
        let editor = try makeEditor()
        let item = try #require(editor.selectedDay?.exercises.first)
        let last = try #require(item.sets.last)
        editor.updateSet(last.id) {
            $0.reps = 6
            $0.weightKg = 30
            $0.restSeconds = 150
        }
        editor.addSet(to: item.id)
        let sets = try #require(editor.exercise(item.id)?.sets)
        #expect(sets.count == 4)
        #expect(sets.last?.reps == 6)
        #expect(sets.last?.weightKg == 30)
        #expect(sets.last?.restSeconds == 150)
    }

    @Test func aSetAddedAfterAWarmupIsStandard() throws {
        let editor = try makeEditor()
        let item = try #require(editor.selectedDay?.exercises.first)
        editor.deleteSet(item.sets[1].id)
        editor.deleteSet(item.sets[2].id)
        editor.updateSet(item.sets[0].id) { $0.type = .warmup }
        editor.addSet(to: item.id)
        #expect(editor.exercise(item.id)?.sets.map(\.type) == [.warmup, .standard])
    }

    @Test func exercisesMoveAndGoAway() throws {
        let editor = try makeEditor()
        editor.moveExercises(from: [0], to: 2)
        #expect(editor.selectedDay?.exercises.map(\.exercise.name) == ["Chest Fly", "Incline Bench", "Leg Raises", "Overhead Extensions", "Tricep Push Down"])
        let last = try #require(editor.selectedDay?.exercises.last)
        editor.removeExercise(last.id)
        #expect(editor.selectedDay?.exercises.count == 4)
        #expect(model.selectedDay?.exercises.count == 4)
    }

    @Test func aPlansNameIsItsOwn() throws {
        let other = try model.createPlan(named: "Upper Lower")
        let editor = model.planEditor(for: PlanEditRoute(planID: other))
        #expect(editor.days.isEmpty)
        #expect(editor.selectedDay == nil)
        #expect(editor.hasExercises == false)
        #expect(throws: NameProblem.taken) { try editor.rename(to: "Push Pull Leg") }
        try editor.rename(to: "Upper/Lower")
        #expect(model.plans.map(\.name) == ["Push Pull Leg", "Upper/Lower"])
        #expect(throws: NameProblem.taken) { try model.createPlan(named: "Upper/Lower") }
    }

    @Test func theWorkoutsDayAndPlanStay() throws {
        model.startWorkout(try #require(model.selectedDay))
        let editor = try makeEditor()
        #expect(editor.canDeleteSelectedDay == false)
        #expect(editor.canDeletePlan == false)
        #expect(model.canDelete(editor.planID) == false)
        editor.deleteSelectedDay()
        editor.delete()
        model.deletePlan(editor.planID)
        #expect(editor.days.count == 5)
        #expect(model.currentPlan != nil)
        editor.selectedDayID = editor.days[1].id
        #expect(editor.canDeleteSelectedDay)
    }

    @Test func editsDuringAWorkoutShowInIt() throws {
        model.startWorkout(try #require(model.selectedDay))
        let workout = try #require(model.workout)
        let editor = try makeEditor()
        let squats = try #require(model.days[2].exercises.first?.exercise)
        editor.addExercise(squats)
        // The plan changes for next time; this workout lists the new exercise without sets.
        #expect(workout.exercises.last?.exercise.name == "Squats")
        #expect(workout.exercises.last?.sets.isEmpty == true)
        #expect(workout.setCount == 15)
        editor.moveExercises(from: [4], to: 0)
        #expect(workout.exercises.first?.exercise.name == "Tricep Push Down")
    }

    @Test func deletingTheCurrentPlanLeavesNoPlan() throws {
        let editor = try makeEditor()
        editor.delete()
        #expect(editor.plan == nil)
        #expect(model.currentPlan == nil)
        #expect(model.plans.isEmpty)
        model.askForPlanIfNeeded()
        #expect(model.isChoosingPlan)
    }
}
