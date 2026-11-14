// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import KitaeCore

/// Plans and their days, read and written the way Kitae for Android does
/// (its `PlanDao`, `PlanHistoryDao` and `RoutineDao`). A day is a routine in the database.
nonisolated enum Plans {
    /// Every plan with how many days and exercises it has, oldest first.
    static func all(_ db: Database) throws -> [PlanSummary] {
        try db.core.plans().map { PlanSummary($0) }
    }

    /// The plan being followed: the one whose plan history row hasn't ended.
    static func currentID(_ db: Database) throws -> Int64? {
        try db.core.currentPlanId()?.int64Value
    }

    /// Ends the current plan today and starts following `planID`.
    static func setCurrent(_ db: Database, planID: Int64, today: Int) throws {
        try db.core.setCurrentPlan(planId: planID, today: Int64(today))
    }

    /// A plan with its days in order, each with its exercises and planned sets.
    static func plan(_ db: Database, id: Int64) throws -> Plan? {
        try db.core.plan(id: id).map { Plan($0) }
    }

    static func dayExercises(_ db: Database, dayID: Int64) throws -> [DayExercise] {
        try db.core.dayExercises(dayId: dayID).map { DayExercise($0) }
    }

    /// The day after the one done last, going back to the first after the last day.
    /// Kitae for Android's `nextAfter`.
    static func dayAfter(_ lastDayID: Int64?, in days: [TrainingDay]) -> TrainingDay? {
        guard !days.isEmpty else { return nil }
        guard let index = days.firstIndex(where: { $0.id == lastDayID }) else { return days.first }
        return days[(index + 1) % days.count]
    }

    // MARK: Taking a workout back into the plan

}
