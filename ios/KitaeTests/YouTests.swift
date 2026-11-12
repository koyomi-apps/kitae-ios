// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB
import Testing
@testable import Kitae

/// The You tab's grid of the last 16 weeks.
struct ActivityWeeksTests {
    /// Saturday 10 October 2026.
    static let today = 20_736

    @Test func weekdaysOfEpochDays() {
        #expect(weekday(ofEpochDay: 0) == 5) // Thursday 1 January 1970
        #expect(weekday(ofEpochDay: 3) == 1) // Sunday
        #expect(weekday(ofEpochDay: -1) == 4) // Wednesday 31 December 1969
        #expect(weekday(ofEpochDay: Self.today) == 7)
        #expect(weekday(ofEpochDay: Self.today + 2) == 2)
    }

    @Test func weeksStartingOnMondayEndWithThisWeek() {
        let weeks = ActivityWeeks(today: Self.today, firstWeekday: 2)
        // Monday 22 June to Sunday 11 October, as in the design.
        #expect(weeks.first == Self.today - 110)
        #expect(weekday(ofEpochDay: weeks.first) == 2)
        #expect(weeks.last == Self.today + 1)
        #expect(weeks.last - weeks.first + 1 == ActivityWeeks.count * 7)
        #expect(weeks.day(week: 15, row: 5) == Self.today)
        #expect(weeks.day(week: 0, row: 0) == weeks.first)
    }

    @Test func weeksStartingOnSundayEndToday() {
        let weeks = ActivityWeeks(today: Self.today, firstWeekday: 1)
        #expect(weeks.first == Self.today - 111)
        #expect(weekday(ofEpochDay: weeks.first) == 1)
        #expect(weeks.last == Self.today)
        #expect(weeks.day(week: 15, row: 6) == Self.today)
    }

    @Test func rowLettersFollowTheFirstDayOfTheWeek() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US")
        #expect(ActivityWeeks(today: Self.today, firstWeekday: 2).rowLetters(calendar: calendar) == ["M", "T", "W", "T", "F", "S", "S"])
        #expect(ActivityWeeks(today: Self.today, firstWeekday: 1).rowLetters(calendar: calendar) == ["S", "M", "T", "W", "T", "F", "S"])
    }

    @Test func onlyDaysInTheGridUpToTodayAreCounted() {
        let weeks = ActivityWeeks(
            today: Self.today,
            firstWeekday: 2,
            trained: [Self.today - 200, Self.today - 110, Self.today - 3, Self.today, Self.today + 1]
        )
        #expect(weeks.trainedCount == 3)
    }
}

/// The You tab's totals, counted like Kitae for Android's profile.
struct StatsTests {
    static let today = ActivityWeeksTests.today

    let database: KitaeDatabase
    let plan: Plan

    init() throws {
        database = try KitaeDatabase.followingStarterPlan(today: Self.today)
        plan = try #require(try database.read { db in try Plans.currentID(db).flatMap { try Plans.plan(db, id: $0) } })
    }

    /// A workout of `day` on `date` with its first `done` sets done, finished unless `finish`
    /// is false.
    private func workout(_ day: TrainingDay, on date: Int, done: Int, finish: Bool = true) throws {
        let start = Int64(date) * 86_400_000 + 12 * 3_600_000
        try database.write { db in
            let id = try Sessions.start(db, dayID: day.id, today: date, now: start)
            let sets = try Sessions.exercises(db, sessionID: id, dayID: day.id).flatMap(\.sets)
            for set in sets.prefix(done) {
                try Sessions.setDone(db, setID: set.id, isDone: true)
            }
            if finish {
                try Sessions.finish(db, sessionID: id, mode: .keepSkipped, addNewSets: false, now: start + 2_700_000)
            }
        }
    }

    @Test func aNewDatabaseHasOnlyExercises() throws {
        let totals = try database.read { db in try Stats.totals(db) }
        #expect(try totals == TrainingTotals(lifts: 0, days: 0, exercises: Seed.bundled().exercises.count))
        let weeks = try database.read { db in try Stats.activity(db, today: Self.today, firstWeekday: 2) }
        #expect(weeks.trained.isEmpty)
    }

    @Test func countsDoneSetsAndTheDaysTheyWereDone() throws {
        // Long ago, before the grid.
        try workout(plan.days[0], on: Self.today - 120, done: 3)
        // Two workouts on the same day count as one day.
        try workout(plan.days[0], on: Self.today - 3, done: 2)
        try workout(plan.days[1], on: Self.today - 3, done: 1)
        // A workout with nothing done isn't a day trained.
        try workout(plan.days[2], on: Self.today - 1, done: 0)
        // Sets done in the workout in progress count straight away.
        try workout(plan.days[3], on: Self.today, done: 1, finish: false)

        let totals = try database.read { db in try Stats.totals(db) }
        #expect(totals.lifts == 7)
        #expect(totals.days == 3)
        #expect(try totals.exercises == Seed.bundled().exercises.count)

        let weeks = try database.read { db in try Stats.activity(db, today: Self.today, firstWeekday: 2) }
        #expect(weeks.trained == [Self.today - 3, Self.today])
        #expect(weeks.trainedCount == 2)
    }
}

/// Settings, as they're kept and as the app follows them.
@MainActor
struct SettingsTests {
    /// UserDefaults of their own for each test, removed afterwards.
    private func withDefaults(_ body: (UserDefaults) throws -> Void) throws {
        let name = "KitaeTests.settings.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        try body(defaults)
    }

    @Test func nothingSavedGivesTheDefaults() throws {
        try withDefaults { defaults in
            let settings = SettingsStore(defaults: defaults).load()
            #expect(settings == AppSettings())
            #expect(settings.unit == .kilograms)
            #expect(settings.decimals == .automatic)
            #expect(settings.defaultRestSeconds == 90)
            #expect(settings.restAlerts)
            #expect(settings.theme == .system)
        }
    }

    @Test func savedSettingsComeBack() throws {
        try withDefaults { defaults in
            let changed = AppSettings(unit: .pounds, decimals: .comma, defaultRestSeconds: 0, restAlerts: false, theme: .dark)
            SettingsStore(defaults: defaults).save(changed)
            #expect(SettingsStore(defaults: defaults).load() == changed)
        }
    }

    @Test func settingsAndroidHasAreStoredLikeAndroids() throws {
        try withDefaults { defaults in
            SettingsStore(defaults: defaults).save(AppSettings(unit: .pounds, defaultRestSeconds: 120, theme: .light))
            #expect(defaults.string(forKey: "weight_unit") == "Pounds")
            #expect(defaults.integer(forKey: "rest_timer_seconds") == 120)
            #expect(defaults.string(forKey: "theme") == "Light")

            defaults.set("Kilograms", forKey: "weight_unit")
            defaults.set("Dark", forKey: "theme")
            let loaded = SettingsStore(defaults: defaults).load()
            #expect(loaded.unit == .kilograms)
            #expect(loaded.theme == .dark)
        }
    }

    @Test func unreadableSettingsAreLeftAsTheyCome() throws {
        try withDefaults { defaults in
            defaults.set("Stones", forKey: "weight_unit")
            defaults.set("sometimes", forKey: "decimals")
            defaults.set(-30, forKey: "rest_timer_seconds")
            defaults.set("Sepia", forKey: "theme")
            #expect(SettingsStore(defaults: defaults).load() == AppSettings())
        }
    }

    @Test func theAppSavesAndFollowsSettings() throws {
        try withDefaults { defaults in
            let model = AppModel(
                database: try KitaeDatabase.followingStarterPlan(),
                restStore: .none,
                settingsStore: SettingsStore(defaults: defaults),
                backups: .none,
                liveActivities: false,
                restAlerts: false
            )
            model.settings.unit = .pounds
            model.settings.decimals = .comma
            #expect(model.weightFormatter.unit == .pounds)
            #expect(model.weightFormatter.string(kilograms: WeightUnit.pounds.toKilograms(22.5)) == "22,5")
            #expect(SettingsStore(defaults: defaults).load().unit == .pounds)

            let reopened = AppModel(
                database: model.database,
                restStore: .none,
                settingsStore: SettingsStore(defaults: defaults),
                backups: .none,
                liveActivities: false,
                restAlerts: false
            )
            #expect(reopened.settings.unit == .pounds)
            #expect(reopened.settings.decimals == .comma)
        }
    }

    @Test func theWorkoutFollowsTheDefaultRest() throws {
        let model = AppModel.testing(try KitaeDatabase.followingStarterPlan())
        let day = try #require(model.selectedDay)
        model.startWorkout(day)
        let workout = try #require(model.workout)
        let sets = workout.exercises.flatMap(\.sets)

        // Off: ticking a set starts no rest.
        model.settings.defaultRestSeconds = 0
        workout.toggleDone(sets[0].id)
        #expect(workout.rest.isActive == false)

        model.settings.defaultRestSeconds = 120
        workout.toggleDone(sets[1].id)
        #expect(workout.rest.isActive)
        #expect(workout.rest.total == 120)

        // New sets weigh 45 lb when there's nothing to copy.
        model.settings.unit = .pounds
        #expect(workout.defaultWeightKg == WeightUnit.pounds.defaultWeightKg)
    }

    @Test func restsAreNamedForThePickers() {
        #expect(restSetting(seconds: 0) == "off")
        #expect(restSetting(seconds: 90) == "1:30")
        #expect(DecimalStyle.allCases.map(\.title) == ["Auto", "22.5", "22,5"])
    }
}
