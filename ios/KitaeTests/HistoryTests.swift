// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// Past workouts as History lists them, checked against Kitae for Android's sessions list.
struct HistoryTests {
    /// Saturday 10 October 2026.
    static let today = 20_736
    static let minute: Int64 = 60_000

    let database: KitaeDatabase
    let plan: Plan

    init() throws {
        database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
    }

    /// Midday on `date`, in milliseconds.
    private func noon(_ date: Int) -> Int64 {
        Int64(date) * 86_400_000 + 12 * 60 * Self.minute
    }

    /// A workout of `day` on `date` with its first `done` sets done, finished after `minutes`
    /// in `mode`, or left going.
    @discardableResult
    private func workout(_ day: TrainingDay, on date: Int, done: Int, minutes: Int64 = 45, finish mode: FinishMode? = .keepSkipped) throws -> Int64 {
        let start = noon(date)
        return try database.write { db in
            let id = try Sessions.start(db, dayID: day.id, today: date, now: start)
            let sets = try Sessions.exercises(db, sessionID: id, dayID: day.id).flatMap(\.sets)
            for set in sets.prefix(done) {
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            if let mode {
                try Sessions.finish(db, sessionID: id, mode: mode, addNewSets: false, now: start + minutes * Self.minute)
            }
            return id
        }
    }

    private func history(activeID: Int64? = nil) throws -> [WorkoutSummary] {
        try database.read { db in try Sessions.history(db, activeID: activeID) }
    }

    @Test func listsDoneAndSkippedWorkoutsNewestFirst() throws {
        let done = try workout(plan.days[0], on: Self.today - 3, done: 2, minutes: 48)
        let skipped = try workout(plan.days[1], on: Self.today - 2, done: 0)
        // Discarding a workout with nothing done removes it.
        try workout(plan.days[2], on: Self.today - 1, done: 0, finish: .discard)
        // A workout left unfinished days ago with nothing done isn't listed, like on Android.
        try workout(plan.days[2], on: Self.today - 5, done: 0, finish: nil)
        let active = try workout(plan.days[3], on: Self.today, done: 0, finish: nil)

        #expect(try history().map(\.id) == [skipped, done])
        let list = try history(activeID: active)
        #expect(list.map(\.id) == [active, skipped, done])

        let first = list[2]
        #expect(first.title == "Push A")
        #expect(first.date == Self.today - 3)
        #expect(first.doneCount == 2)
        #expect(first.setCount == 15)
        #expect(first.minutes == 48)
        #expect(first.isSkipped == false)
        #expect(workoutDetails(first) == "48 min · 2/15 sets")

        let second = list[1]
        #expect(second.isSkipped)
        #expect(second.title == "Pull A")
        #expect(list[0].isFinished == false)
    }

    @Test func lengthsRoundLikeAndroid() {
        #expect(workoutMinutes(0) == 1)
        #expect(workoutMinutes(89_999) == 1)
        #expect(workoutMinutes(90_000) == 2)
        #expect(workoutMinutes(48 * 60_000 + 29_000) == 48)
        #expect(durationLabel(minutes: 48) == "48 min")
        #expect(durationLabel(minutes: 109) == "1 h 49 min")
        #expect(durationLabel(minutes: 60) == "1 h 0 min")

        let unknown = WorkoutSummary(id: 1, date: Self.today, isFinished: true, startedAt: nil, finishedAt: nil, setCount: 12, doneCount: 12)
        #expect(unknown.minutes == nil)
        #expect(workoutDetails(unknown) == "12/12 sets")
    }

    @Test func monthsCountTheWorkoutsDone() {
        func summary(_ id: Int64, _ date: Int, done: Int, minutes: Int64?, finished: Bool = true) -> WorkoutSummary {
            let start: Int64 = 1_000_000
            return WorkoutSummary(
                id: id, date: date, isFinished: finished,
                startedAt: minutes == nil ? nil : start, finishedAt: minutes.map { start + $0 * Self.minute },
                setCount: 15, doneCount: done
            )
        }
        let workouts = [
            summary(5, 20_733, done: 6, minutes: nil, finished: false),
            summary(4, 20_731, done: 15, minutes: 48),
            summary(3, 20_729, done: 0, minutes: 50),
            summary(2, 20_728, done: 14, minutes: 61),
            summary(1, 20_726, done: 16, minutes: 55),
        ]
        let months = historyMonths(workouts, activeID: 5)
        #expect(months.map(\.month) == [10, 9])
        #expect(months.map(\.year) == [2026, 2026])
        #expect(months[0].workouts.map(\.id) == [5, 4, 3, 2])
        #expect(months[0].totals == "2 workouts · 1 h 49 min")
        #expect(months[1].totals == "1 workout · 55 min")
        #expect(historyMonths([summary(9, 20_700, done: 3, minutes: nil)], activeID: nil).first?.totals == "1 workout")
    }

    @Test func datesReadLikeTheDesign() {
        let english = Locale(identifier: "en_US")
        // Wednesday 7 October 2026.
        let day = Self.today - 3
        #expect(HistoryDates.dayOfMonth(day) == "07")
        #expect(HistoryDates.dayOfMonth(Self.today + 10) == "20")
        #expect(HistoryDates.shortWeekday(day, locale: english) == "WED")
        #expect(HistoryDates.weekday(day, locale: english) == "Wednesday")
        #expect(HistoryDates.monthTitle(year: 2026, month: 10, today: Self.today, locale: english) == "October")
        #expect(HistoryDates.monthTitle(year: 2025, month: 9, today: Self.today, locale: english) == "September 2025")
        #expect(HistoryDates.long(day, today: Self.today, locale: english) == "Wednesday, October 7")
        #expect(HistoryDates.long(day - 365, today: Self.today, locale: english) == "Tuesday, October 7, 2025")
    }

    @Test func previousWorkoutIsOfTheSameDay() throws {
        let pushA = plan.days[0]
        let older = try workout(pushA, on: Self.today - 10, done: 1)
        // A skipped workout doesn't count as the one before.
        try workout(pushA, on: Self.today - 6, done: 0)
        let newer = try workout(pushA, on: Self.today - 3, done: 3)
        try workout(plan.days[1], on: Self.today - 2, done: 3)

        let previous = try database.read { db in
            (
                try Sessions.previousID(db, before: newer, dayID: pushA.id, date: Self.today - 3),
                try Sessions.previousID(db, before: older, dayID: pushA.id, date: Self.today - 10)
            )
        }
        #expect(previous.0 == older)
        #expect(previous.1 == nil)
    }

    @Test func workoutsWithoutADayFollowTheWeekday() throws {
        func dayless(on date: Int) throws -> Int64 {
            try database.write { db in
                try db.execute(sql: "INSERT INTO sessions (date, isFinished) VALUES (?, 1)", arguments: [date])
                let id = db.lastInsertedRowID
                try db.execute(sql: """
                    INSERT INTO sets (reps, weight, type, `order`, sessionId, exerciseId, rir, isCompleted)
                    VALUES (10, 20, 'Standard', 0, ?, 1, 2, 1)
                    """, arguments: [id])
                return id
            }
        }
        let twoWeeksAgo = try dayless(on: Self.today - 14)
        try dayless(on: Self.today - 8)
        let lastWeek = try dayless(on: Self.today - 7)
        let previous = try database.read { db in
            try Sessions.previousID(db, before: lastWeek, dayID: nil, date: Self.today - 7)
        }
        #expect(previous == twoWeeksAgo)

        let summary = try #require(try database.read { db in try Sessions.summary(db, id: lastWeek) })
        #expect(summary.dayName == nil)
        #expect(summary.title == HistoryDates.weekday(Self.today - 7))
        #expect(summary.minutes == nil)
    }
}

/// Deleting past workouts from History.
@MainActor
struct HistoryModelTests {
    let model: AppModel

    init() throws {
        model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
    }

    @Test func deletingAWorkoutKeepsThePlan() throws {
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        let before = model.historyRevision
        workout.toggleDone(try #require(workout.exercises.first?.sets.first?.id))

        // The workout in progress isn't deleted from History.
        model.deleteWorkout(workout.id)
        #expect(model.workout?.id == workout.id)

        model.finishWorkout(.keepSkipped, addNewSets: false)
        #expect(model.historyRevision > before)
        let listed = try model.database.read { db in try Sessions.history(db, activeID: nil) }
        #expect(listed.map(\.id) == [workout.id])

        model.deleteWorkout(workout.id)
        #expect(try model.database.read { db in try Sessions.history(db, activeID: nil) }.isEmpty)
        #expect(model.currentPlan?.days.first?.setCount == 15)
        #expect(model.hasDoneSets == false)
    }
}
