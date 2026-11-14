// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// What the database's functions take, as GRDB's `Database` was: the shared Kotlin module's
/// database (`IPhoneDatabase.kt`), whose calls wait for their answer.
nonisolated struct Database {
    let core: IPhoneDatabase
}

nonisolated extension Database {
    // For tests, which check what's stored with SQL, as they did with GRDB.

    /// Runs `sql` as it is, with `arguments`, a JSON array of texts and nulls, bound as text.
    func execute(sql: String, argumentsJSON arguments: String) throws {
        try core.execute(sql: sql, arguments: arguments)
    }

    /// The rows `sql` gives: a JSON array whose first row is the columns' names.
    func query(sql: String, argumentsJSON arguments: String) throws -> String {
        try core.query(sql: sql, arguments: arguments)
    }

    /// The id of the row inserted last.
    var lastInsertedRowID: Int64 {
        (try? core.lastInsertRowId()) ?? -1
    }
}

/// Runs a call into the shared module, turning its "that name can't be used" into `NameProblem`.
nonisolated func naming<T>(_ call: () throws -> T) throws -> T {
    do {
        return try call()
    } catch let error as NSError {
        if let exception = error.userInfo["KotlinException"] as? NameProblemException {
            throw NameProblem(exception.problem)
        }
        throw error
    }
}

// The shared module's values, as the iPhone's own types, so views never see Kotlin's.

nonisolated extension NameProblem {
    init(_ problem: KitaeCore.NameProblem) {
        switch problem.name {
        case "Taken": self = .taken
        case "ExerciseTaken": self = .exerciseTaken
        default: self = .empty
        }
    }
}

nonisolated extension Exercise {
    init(_ exercise: KitaeCore.Exercise) {
        self.init(
            id: exercise.id,
            name: exercise.name,
            target: exercise.target,
            isIsometric: exercise.isIsometric,
            usesWeight: exercise.usesWeight,
            restSeconds: exercise.restSeconds?.intValue,
            reference: exercise.reference,
            instructions: exercise.instructions,
            libraryKey: exercise.libraryKey
        )
    }
}

nonisolated extension SetDraft {
    init(_ set: KitaeCore.SetDraft) {
        self.init(reps: Int(set.reps), weightKg: set.weightKg, type: SetType(stored: set.type.stored),
                  restSeconds: set.restSeconds?.intValue)
    }

    /// The shared module's own.
    var core: KitaeCore.SetDraft {
        KitaeCore.SetDraft(reps: Int32(clamping: reps), weightKg: weightKg, type: type.core,
                           restSeconds: restSeconds.map { KotlinInt(int: Int32(clamping: $0)) })
    }
}

nonisolated extension PlanSummary {
    init(_ plan: KitaeCore.PlanSummary) {
        self.init(id: plan.id, name: plan.name, dayCount: Int(plan.dayCount), exerciseCount: Int(plan.exerciseCount))
    }
}

nonisolated extension Plan {
    init(_ plan: PlanWithDays) {
        self.init(id: plan.plan.id, name: plan.plan.name, days: plan.days.map { TrainingDay($0) })
    }
}

nonisolated extension TrainingDay {
    init(_ day: DayWithExercises) {
        self.init(id: day.day.id, name: day.day.name, exercises: day.exercises.map { DayExercise($0) })
    }
}

nonisolated extension DayExercise {
    init(_ slot: DayExerciseWithSets) {
        self.init(id: slot.dayExercise.id, exercise: Exercise(slot.exercise), sets: slot.sets.map { PlannedSet($0) })
    }
}

nonisolated extension PlannedSet {
    init(_ set: KitaeCore.PlannedSet) {
        self.init(id: set.id, type: SetType(stored: set.type), reps: Int(set.reps), weightKg: set.weightKg,
                  restSeconds: set.restSeconds?.intValue)
    }
}

nonisolated extension ActiveSession {
    init(_ workout: WorkoutWithDay) {
        self.init(
            id: workout.workout.id,
            planID: workout.workout.planId?.int64Value,
            dayID: workout.workout.dayId?.int64Value,
            dayName: workout.dayName,
            startedAt: workout.workout.startedAt?.int64Value,
            date: Int(workout.workout.date)
        )
    }
}

nonisolated extension WorkoutExercise {
    init(_ exercise: KitaeCore.WorkoutExercise) {
        self.init(id: exercise.exercise.id, exercise: Exercise(exercise.exercise), sets: exercise.sets.map { WorkoutSet($0) })
    }
}

nonisolated extension WorkoutSet {
    init(_ set: KitaeCore.WorkoutSet) {
        self.init(
            id: set.id,
            type: SetType(stored: set.type),
            reps: Int(set.reps),
            weightKg: set.weightKg,
            restSeconds: set.restSeconds?.intValue,
            isDone: set.isDone,
            isAdded: set.plannedSetId == nil
        )
    }
}

nonisolated extension WorkoutSummary {
    init(_ summary: KitaeCore.WorkoutSummary) {
        let workout = summary.workout
        self.init(
            id: workout.id,
            date: Int(workout.date),
            planID: workout.planId?.int64Value,
            dayID: workout.dayId?.int64Value,
            dayName: summary.dayName,
            isFinished: workout.isFinished,
            startedAt: workout.startedAt?.int64Value,
            finishedAt: workout.finishedAt?.int64Value,
            setCount: Int(summary.setCount),
            doneCount: Int(summary.doneCount)
        )
    }
}

nonisolated extension DoneSet {
    init(_ set: KitaeCore.DoneSet) {
        self.init(exerciseID: set.exerciseId, libraryKey: set.libraryKey, target: set.target, isIsometric: set.isIsometric,
                  reps: Int(set.reps), weightKg: set.weightKg, type: SetType(stored: set.type.stored))
    }
}

nonisolated extension ExerciseSession {
    init(_ session: KitaeCore.ExerciseSession) {
        self.init(id: session.id, date: Int(session.date), dayName: session.dayName, sets: session.sets.map { SetDraft($0) })
    }
}

nonisolated extension NewBest {
    init(_ best: KitaeCore.NewBest) {
        let record: Record = switch best.record.name {
        case "LeastHelp": .leastHelp
        case "MostReps": .mostReps
        case "LongestHold": .longestHold
        default: .heaviest
        }
        self.init(exercise: Exercise(best.exercise), record: record, set: SetDraft(best.set), previous: SetDraft(best.previous))
    }
}

nonisolated extension TrainingTotals {
    init(_ totals: KitaeCore.TrainingTotals) {
        self.init(lifts: Int(totals.lifts), days: Int(totals.days), exercises: Int(totals.exercises))
    }
}

nonisolated extension ExerciseSetCount {
    init(_ count: KitaeCore.ExerciseSetCount) {
        self.init(exercise: Exercise(count.exercise), sets: Int(count.sets))
    }
}

nonisolated extension BodyLog {
    init(_ log: KitaeCore.BodyLog) {
        let profile = log.profile
        self.init(
            profile: BodyProfile(
                heightCm: profile.heightCm?.doubleValue,
                birthDate: profile.birthDate.map { Int($0.int64Value) },
                sex: profile.sex.flatMap { Sex(rawValue: $0.stored) },
                activityLevel: profile.activityLevel.flatMap { ActivityLevel(rawValue: $0.stored) }
            ),
            entries: log.entries.map {
                BodyEntry(id: $0.id, date: Int($0.date), weightKg: $0.weightKg?.doubleValue, bodyFatPercent: $0.bodyFatPercent?.doubleValue)
            }
        )
    }
}

nonisolated extension Int64 {
    /// For the shared module's optional numbers.
    var kotlin: KotlinLong { KotlinLong(longLong: self) }
}

nonisolated extension Double {
    /// For the shared module's optional numbers.
    var kotlin: KotlinDouble { KotlinDouble(double: self) }
}
