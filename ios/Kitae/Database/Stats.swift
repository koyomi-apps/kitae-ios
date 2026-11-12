// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

/// The totals on the You tab.
nonisolated struct TrainingTotals: Equatable, Sendable {
    /// Sets done, in every workout.
    var lifts = 0
    /// Days with a set done.
    var days = 0
    var exercises = 0
}

/// The last 16 weeks a day at a time, as the You tab's grid shows them: a column for each
/// week, starting on the phone's first day of the week, with this week last.
nonisolated struct ActivityWeeks: Equatable, Sendable {
    static let count = 16

    let today: Int
    /// 1 for Sunday to 7 for Saturday, like `Calendar.firstWeekday`.
    let firstWeekday: Int
    /// The start of the oldest week.
    let first: Int
    /// Days with a set done.
    var trained: Set<Int>

    init(today: Int, firstWeekday: Int, trained: Set<Int> = []) {
        let intoWeek = (weekday(ofEpochDay: today) - firstWeekday + 7) % 7
        self.today = today
        self.firstWeekday = firstWeekday
        first = today - intoWeek - (Self.count - 1) * 7
        self.trained = trained
    }

    /// The end of this week.
    var last: Int { first + Self.count * 7 - 1 }

    /// The day in `week`, 0 being the oldest, and `row`, 0 being the first day of the week.
    func day(week: Int, row: Int) -> Int {
        first + week * 7 + row
    }

    /// Days with a set done, up to today.
    var trainedCount: Int {
        trained.filter { $0 >= first && $0 <= today }.count
    }

    /// The rows' weekday letters, like M T W T F S S when weeks start on Monday.
    func rowLetters(calendar: Calendar = .current) -> [String] {
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        guard symbols.count == 7 else { return Array(repeating: "", count: 7) }
        return (0..<7).map { symbols[(firstWeekday - 1 + $0) % 7] }
    }
}

/// The weekday of a day counted like `EpochDay`, from 1 for Sunday to 7 for Saturday as
/// `Calendar` numbers them. 1 January 1970 was a Thursday.
nonisolated func weekday(ofEpochDay day: Int) -> Int {
    ((day % 7) + 7 + 4) % 7 + 1
}

/// What the You tab counts, the way Kitae for Android's profile counts it (`SetsDao`'s
/// `totalSetCount`, `SessionDao`'s `daysTrained` and `ExerciseDao`'s `number`).
nonisolated enum Stats {
    static func totals(_ db: Database) throws -> TrainingTotals {
        TrainingTotals(
            lifts: try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM `sets` WHERE `isCompleted` = 1") ?? 0,
            days: try Int.fetchOne(db, sql: """
                SELECT COUNT(DISTINCT `date`)
                FROM `sessions`
                WHERE EXISTS
                (SELECT 1 FROM `sets`
                WHERE `sets`.`sessionId` = `sessions`.`id`
                AND `sets`.`isCompleted` = 1)
                """) ?? 0,
            exercises: try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM `exercises`") ?? 0
        )
    }

    /// The days from `first` to `last` with a set done.
    static func trainedDays(_ db: Database, from first: Int, to last: Int) throws -> Set<Int> {
        Set(try Int.fetchAll(db, sql: """
            SELECT DISTINCT `date`
            FROM `sessions`
            WHERE `date` BETWEEN ? AND ?
            AND EXISTS
            (SELECT 1 FROM `sets`
            WHERE `sets`.`sessionId` = `sessions`.`id`
            AND `sets`.`isCompleted` = 1)
            """, arguments: [first, last]))
    }

    /// The last 16 weeks, with the days trained in them.
    static func activity(_ db: Database, today: Int, firstWeekday: Int) throws -> ActivityWeeks {
        var weeks = ActivityWeeks(today: today, firstWeekday: firstWeekday)
        weeks.trained = try trainedDays(db, from: weeks.first, to: weeks.last)
        return weeks
    }
}

/// An exercise and how many sets of it were done.
nonisolated struct ExerciseSetCount: Hashable, Sendable {
    var exercise: Exercise
    var sets: Int
}

nonisolated extension Stats {
    /// Each exercise done from `first` to `last`, with its done sets, warm-ups left out. Sets
    /// done in the workout in progress count straight away.
    static func exerciseSets(_ db: Database, from first: Int, to last: Int) throws -> [ExerciseSetCount] {
        try Row.fetchAll(db, sql: """
            SELECT `exercises`.*, COUNT(*) AS `doneSets`
            FROM `sets`
            JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
            JOIN `exercises` ON `exercises`.`id` = `sets`.`exerciseId`
            WHERE `sets`.`isCompleted` = 1
            AND `sets`.`type` != ?
            AND `sessions`.`date` BETWEEN ? AND ?
            GROUP BY `exercises`.`id`
            """, arguments: [SetType.warmup.rawValue, first, last])
            .map { ExerciseSetCount(exercise: Exercise(row: $0), sets: $0["doneSets"]) }
    }
}
