// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// A workout as History lists it.
nonisolated struct WorkoutSummary: Identifiable, Hashable, Sendable {
    var id: Int64
    /// The day it was done, in days since 1970 where the phone was.
    var date: Int
    var planID: Int64?
    var dayID: Int64?
    /// The plan day's name, while the day still exists.
    var dayName: String?
    var isFinished: Bool
    /// Milliseconds since 1970. Workouts from older versions of Kitae for Android have none.
    var startedAt: Int64?
    var finishedAt: Int64?
    var setCount: Int
    var doneCount: Int

    /// How long it took, when both ends are known. Kitae for Android's `durationMillis`.
    var durationMilliseconds: Int64? {
        guard let startedAt, let finishedAt else { return nil }
        return max(finishedAt - startedAt, 0)
    }

    var minutes: Int? {
        durationMilliseconds.map(workoutMinutes)
    }

    /// Finished without doing a set.
    var isSkipped: Bool {
        isFinished && doneCount == 0
    }

    /// The day's name, or the weekday for a workout whose day is gone, like Kitae for Android.
    var title: String {
        dayName ?? HistoryDates.weekday(date)
    }
}

/// A workout's length in whole minutes, rounded, and at least one. Kitae for Android's
/// `workoutLength`.
nonisolated func workoutMinutes(_ milliseconds: Int64) -> Int {
    max(Int((milliseconds + 30_000) / 60_000), 1)
}

/// "48 min", or "1 h 49 min".
nonisolated func durationLabel(minutes: Int) -> String {
    minutes < 60 ? "\(minutes) min" : "\(minutes / 60) h \(minutes % 60) min"
}

/// "48 min · 15/15 sets", or just the sets when the workout's length isn't known, and its
/// active energy when Kitae can work it out: "48 min · 15/15 sets · ≈ 230 kcal".
nonisolated func workoutDetails(_ workout: WorkoutSummary, kilocalories: Double? = nil) -> String {
    var parts = ["\(workout.doneCount)/\(workout.setCount) sets"]
    if let minutes = workout.minutes {
        parts.insert(durationLabel(minutes: minutes), at: 0)
    }
    if let kilocalories {
        parts.append(kilocaloriesLabel(kilocalories))
    }
    return parts.joined(separator: " · ")
}

/// One month of History, newest first.
nonisolated struct HistoryMonth: Identifiable, Hashable, Sendable {
    var year: Int
    /// 1 for January.
    var month: Int
    var workouts: [WorkoutSummary]
    /// Workouts with a set done, apart from the one in progress.
    var doneCount: Int
    /// Their minutes, for the ones whose length is known.
    var minutes: Int

    var id: Int { year * 12 + month - 1 }

    /// "2 workouts · 1 h 49 min".
    var totals: String {
        guard doneCount > 0 else { return "" }
        let count = counted(doneCount, "workout", "workouts")
        return minutes > 0 ? "\(count) · \(durationLabel(minutes: minutes))" : count
    }
}

/// Splits workouts into months, keeping their order. `activeID` is the workout in progress,
/// which isn't counted yet.
nonisolated func historyMonths(_ workouts: [WorkoutSummary], activeID: Int64?) -> [HistoryMonth] {
    var months: [HistoryMonth] = []
    for workout in workouts {
        let (year, month) = HistoryDates.yearAndMonth(workout.date)
        if months.last?.year != year || months.last?.month != month {
            months.append(HistoryMonth(year: year, month: month, workouts: [], doneCount: 0, minutes: 0))
        }
        months[months.count - 1].workouts.append(workout)
        if workout.doneCount > 0, workout.id != activeID {
            months[months.count - 1].doneCount += 1
            months[months.count - 1].minutes += workout.minutes ?? 0
        }
    }
    return months
}

/// Workout days as History shows them. Days are counted on the Gregorian calendar, as
/// they're stored, and named in the phone's language.
nonisolated enum HistoryDates {
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .gmt
        return calendar
    }()

    private static func date(_ day: Int) -> Date {
        Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
    }

    private static func style(_ locale: Locale) -> Date.FormatStyle {
        Date.FormatStyle(date: .omitted, time: .omitted, locale: locale, calendar: calendar, timeZone: .gmt)
    }

    static func yearAndMonth(_ day: Int) -> (year: Int, month: Int) {
        let parts = calendar.dateComponents([.year, .month], from: date(day))
        return (parts.year ?? 1970, parts.month ?? 1)
    }

    /// "07".
    static func dayOfMonth(_ day: Int) -> String {
        let number = calendar.component(.day, from: date(day))
        return number < 10 ? "0\(number)" : "\(number)"
    }

    /// "WED".
    static func shortWeekday(_ day: Int, locale: Locale = .current) -> String {
        date(day).formatted(style(locale).weekday(.abbreviated)).uppercased(with: locale)
    }

    /// "Wednesday".
    static func weekday(_ day: Int, locale: Locale = .current) -> String {
        date(day).formatted(style(locale).weekday(.wide))
    }

    /// "October", or "September 2025" in another year than `today`'s.
    static func monthTitle(year: Int, month: Int, today: Int, locale: Locale = .current) -> String {
        let first = calendar.date(from: DateComponents(year: year, month: month, day: 1)) ?? date(0)
        let monthStyle = style(locale).month(.wide)
        return first.formatted(yearAndMonth(today).year == year ? monthStyle : monthStyle.year())
    }

    /// "Wed, Oct 7", with the year when it isn't `today`'s.
    static func short(_ day: Int, today: Int, locale: Locale = .current) -> String {
        let format = style(locale).weekday(.abbreviated).month(.abbreviated).day()
        return date(day).formatted(yearAndMonth(today).year == yearAndMonth(day).year ? format : format.year())
    }

    /// "Oct 7", for the ends of a chart.
    static func dayAndMonth(_ day: Int, locale: Locale = .current) -> String {
        date(day).formatted(style(locale).month(.abbreviated).day())
    }

    /// "Wednesday, October 7", with the year when it isn't `today`'s.
    static func long(_ day: Int, today: Int, locale: Locale = .current) -> String {
        let format = style(locale).weekday(.wide).month(.wide).day()
        return date(day).formatted(yearAndMonth(today).year == yearAndMonth(day).year ? format : format.year())
    }
}

/// Past workouts, read the way Kitae for Android's sessions list and session page read them
/// (its `SessionDao`).
nonisolated extension Sessions {
    /// The workouts History lists, newest first: the ones with a set done, and finished ones
    /// that kept their sets as skipped, like Kitae for Android. The workout in progress is
    /// listed even before a set is done.
    static func history(_ db: Database, activeID: Int64?) throws -> [WorkoutSummary] {
        try db.core.workouts(activeId: activeID?.kotlin).map { WorkoutSummary($0) }
    }

    /// Finished workouts with a set done, from `first` to `last`, newest first.
    static func finished(_ db: Database, from first: Int, to last: Int) throws -> [WorkoutSummary] {
        try db.core.finishedWorkouts(first: Int64(first), last: Int64(last)).map { WorkoutSummary($0) }
    }

    /// The done sets of the workouts from `first` to `last`, by workout, for their energy.
    static func doneSets(_ db: Database, from first: Int, to last: Int) throws -> [Int64: [DoneSet]] {
        let sets = try db.core.doneSets(first: Int64(first), last: Int64(last))
        return Dictionary(uniqueKeysWithValues: sets.map { ($0.key.int64Value, $0.value.map { DoneSet($0) }) })
    }

    static func summary(_ db: Database, id: Int64) throws -> WorkoutSummary? {
        try db.core.workoutSummary(id: id).map { WorkoutSummary($0) }
    }

    /// The last workout before this one of the same day that had a set done. For a workout
    /// without a day, the last one on the same weekday. Kitae for Android's `previousSessionId`.
    static func previousID(_ db: Database, before sessionID: Int64, dayID: Int64?, date: Int) throws -> Int64? {
        try db.core.previousWorkoutId(workoutId: sessionID, dayId: dayID?.kotlin, date: Int64(date))?.int64Value
    }
}
