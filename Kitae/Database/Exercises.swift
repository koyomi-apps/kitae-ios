// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

/// The sets of one exercise done in one workout. Kitae for Android's `ExerciseSession`.
nonisolated struct ExerciseSession: Identifiable, Hashable, Sendable {
    /// The workout's id.
    var id: Int64
    var date: Int
    /// The plan day's name, while the day still exists.
    var dayName: String?
    var sets: [SetDraft]

    /// Warm-ups only count when nothing else was done, as on Android.
    var workSets: [SetDraft] {
        let work = sets.filter { $0.type != .warmup }
        return work.isEmpty ? sets : work
    }

    var heaviest: SetDraft? { heaviestSet(workSets) }

    /// On an assisted machine: the set with the least help.
    var leastHelp: SetDraft? { leastHelpSet(workSets) }

    var mostReps: Int { workSets.map(\.reps).max() ?? 0 }

    /// The set to show for it: the heaviest, the one with the least help on an assisted
    /// machine, or the one with the most reps when the exercise doesn't use weight.
    func best(usesWeight: Bool, assisted: Bool = false) -> SetDraft? {
        guard usesWeight else { return workSets.max { $0.reps < $1.reps } }
        return assisted ? leastHelp : heaviest
    }
}

/// The heaviest set, and of the heaviest, the one with the most reps.
nonisolated func heaviestSet(_ sets: [SetDraft]) -> SetDraft? {
    sets.max { ($0.weightKg, $0.reps) < ($1.weightKg, $1.reps) }
}

/// On an assisted machine, whose weight is the help it gives: the set with the least help,
/// and of those, the one with the most reps.
nonisolated func leastHelpSet(_ sets: [SetDraft]) -> SetDraft? {
    sets.max { (-$0.weightKg, $0.reps) < (-$1.weightKg, $1.reps) }
}

/// How an exercise went over its last workouts, for the chart on its page: the heaviest set
/// each time, the least help on an assisted machine, or the most reps or longest hold when it's
/// done without weight. Kitae for Android's progress chart.
nonisolated struct ExerciseProgress: Hashable, Sendable {
    enum Measure: Hashable, Sendable {
        case weight
        /// An assisted machine's help, which goes down as people get stronger.
        case help
        case reps
        case seconds

        /// Kilograms, of weight or of help.
        var isWeight: Bool { self == .weight || self == .help }
    }

    struct Point: Identifiable, Hashable, Sendable {
        /// The workout's id.
        var id: Int64
        var date: Int
        /// Kilograms, reps or seconds.
        var value: Double
    }

    /// How many workouts the chart shows, like Android's.
    static let maxPoints = 12

    var measure: Measure
    /// Oldest first.
    var points: [Point]

    /// `history` is newest first, as `Exercises.history` reads it.
    init(history: [ExerciseSession], isIsometric: Bool, usesWeight: Bool = true, assisted: Bool = false) {
        let recent = history.prefix(Self.maxPoints).reversed()
        let byWeight = usesWeight && recent.contains { ($0.heaviest?.weightKg ?? 0) > 0 }
        let measure: Measure = byWeight ? (assisted ? .help : .weight) : isIsometric ? .seconds : .reps
        self.measure = measure
        points = recent.map { session in
            let value: Double
            switch measure {
            case .weight: value = session.heaviest?.weightKg ?? 0
            case .help: value = session.leastHelp?.weightKg ?? 0
            case .reps, .seconds: value = Double(session.mostReps)
            }
            return Point(id: session.id, date: session.date, value: value)
        }
    }

    /// The last value less the first; less help shows as a drop.
    var change: Double {
        (points.last?.value ?? 0) - (points.first?.value ?? 0)
    }
}

/// An exercise's best sets ever.
nonisolated struct ExerciseRecords: Hashable, Sendable {
    /// The heaviest set, or on an assisted machine the one with the least help, with the most
    /// reps among those.
    var best: SetDraft?
    /// The set with the most reps, or the longest hold, and the heaviest of those (on an
    /// assisted machine, the one with the least help).
    var mostReps: SetDraft?
    var workoutCount: Int
    /// The day of the workout with the best set, the latest if it was done more than once.
    var bestDate: Int?

    init(history: [ExerciseSession], assisted: Bool = false) {
        let sets = history.flatMap(\.workSets)
        best = assisted ? leastHelpSet(sets) : heaviestSet(sets)
        bestDate = best.flatMap { best in history.first { $0.workSets.contains(best) }?.date }
        let load = { (set: SetDraft) in assisted ? -set.weightKg : set.weightKg }
        mostReps = sets.max { ($0.reps, load($0)) < ($1.reps, load($1)) }
        workoutCount = history.count
    }
}

/// The exercises Kitae knows, its own and people's, read and written the way Kitae for
/// Android does (its `ExerciseDao`).
nonisolated enum Exercises {
    static func all(_ db: Database) throws -> [Exercise] {
        try Row.fetchAll(db, sql: "SELECT * FROM `exercises` ORDER BY `name` COLLATE NOCASE ASC, `id` ASC")
            .map(Exercise.init(row:))
    }

    static func exercise(_ db: Database, id: Int64) throws -> Exercise? {
        try Row.fetchOne(db, sql: "SELECT * FROM `exercises` WHERE `id` = ?", arguments: [id])
            .map(Exercise.init(row:))
    }

    /// The name, trimmed, if no other exercise has it. Case doesn't make a name different.
    static func usableName(_ db: Database, _ name: String, excluding id: Int64? = nil) throws -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw NameProblem.empty }
        let taken = try Bool.fetchOne(db, sql: """
            SELECT EXISTS
            (SELECT 1
            FROM `exercises`
            WHERE `name` = ? COLLATE NOCASE
            AND `id` IS NOT ?)
            """, arguments: [trimmed, id]) ?? false
        guard !taken else { throw NameProblem.exerciseTaken }
        return trimmed
    }

    /// Adds an exercise. Throws a `NameProblem` if the name can't be used.
    @discardableResult
    static func create(_ db: Database, _ draft: ExerciseDraft) throws -> Int64 {
        let name = try usableName(db, draft.name)
        try db.execute(sql: """
            INSERT INTO `exercises` (`name`, `target`, `reference`, `isIsometric`, `usesWeight`, `restSeconds`, `instructions`)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, arguments: [
                name, draft.muscle.rawValue, savedReference(draft.reference), draft.isIsometric, draft.usesWeight,
                draft.restSeconds, savedInstructions(draft.instructions),
            ])
        return db.lastInsertedRowID
    }

    /// Saves an exercise's details. Throws a `NameProblem` if the name can't be used.
    static func update(_ db: Database, id: Int64, _ draft: ExerciseDraft) throws {
        let name = try usableName(db, draft.name, excluding: id)
        try db.execute(sql: """
            UPDATE `exercises`
            SET `name` = ?, `target` = ?, `reference` = ?, `isIsometric` = ?, `usesWeight` = ?, `restSeconds` = ?, `instructions` = ?
            WHERE `id` = ?
            """, arguments: [
                name, draft.muscle.rawValue, savedReference(draft.reference), draft.isIsometric, draft.usesWeight,
                draft.restSeconds, savedInstructions(draft.instructions), id,
            ])
    }

    /// Deletes an exercise. Like on Android, it goes from every plan, and its sets go from
    /// past workouts.
    static func delete(_ db: Database, id: Int64) throws {
        try db.execute(sql: "DELETE FROM `exercises` WHERE `id` = ?", arguments: [id])
    }

    /// The sets of an exercise done in each workout, newest first. Kitae for Android's
    /// `exerciseHistory`.
    static func history(_ db: Database, exerciseID: Int64) throws -> [ExerciseSession] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT `sets`.`sessionId`, `sessions`.`date`, `routines`.`name` AS `routineName`,
            `sets`.`reps`, `sets`.`weight`, `sets`.`type`, `sets`.`restSeconds`
            FROM `sets`
            INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
            LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
            WHERE `sets`.`exerciseId` = ?
            AND `sets`.`isCompleted` = 1
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` ASC, `sets`.`id` ASC
            """, arguments: [exerciseID])
        var sessions: [ExerciseSession] = []
        for row in rows {
            let sessionID: Int64 = row["sessionId"]
            if sessions.last?.id != sessionID {
                sessions.append(ExerciseSession(id: sessionID, date: row["date"], dayName: row["routineName"], sets: []))
            }
            sessions[sessions.count - 1].sets.append(SetDraft(row: row))
        }
        return sessions
    }

    /// For each exercise, the sets done in the newest workout that had any.
    static func lastSessions(_ db: Database) throws -> [Int64: ExerciseSession] {
        let rows = try Row.fetchCursor(db, sql: """
            SELECT `sets`.`exerciseId`, `sets`.`sessionId`, `sessions`.`date`,
            `sets`.`reps`, `sets`.`weight`, `sets`.`type`, `sets`.`restSeconds`
            FROM `sets`
            INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
            WHERE `sets`.`isCompleted` = 1
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` ASC, `sets`.`id` ASC
            """)
        var last: [Int64: ExerciseSession] = [:]
        while let row = try rows.next() {
            let exerciseID: Int64 = row["exerciseId"]
            let sessionID: Int64 = row["sessionId"]
            if let session = last[exerciseID] {
                guard session.id == sessionID else { continue }
                last[exerciseID]?.sets.append(SetDraft(row: row))
            } else {
                last[exerciseID] = ExerciseSession(id: sessionID, date: row["date"], sets: [SetDraft(row: row)])
            }
        }
        return last
    }

    /// The exercises done most recently, at most `limit` of them: the newest workout's in the
    /// order they were done, then the ones before it.
    static func recentIDs(_ db: Database, limit: Int) throws -> [Int64] {
        let rows = try Int64.fetchCursor(db, sql: """
            SELECT `sets`.`exerciseId`
            FROM `sets`
            INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
            WHERE `sets`.`isCompleted` = 1
            ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` ASC, `sets`.`id` ASC
            """)
        var ids: [Int64] = []
        while ids.count < limit, let id = try rows.next() {
            if !ids.contains(id) {
                ids.append(id)
            }
        }
        return ids
    }

    /// A reference as it's saved: trimmed, with https:// in front when it has no scheme, or
    /// nil when it's empty.
    static func savedReference(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return trimmed.contains("://") ? trimmed : "https://\(trimmed)"
    }

    static func savedInstructions(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
