// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// A done set, as a workout's energy estimate needs it.
nonisolated struct DoneSet: Hashable, Sendable {
    var exerciseID: Int64
    /// The exercise's library key, when it's one of Kitae's.
    var libraryKey: String?
    /// The exercise's muscle group, as it's stored, like "Chest".
    var target: String
    var isIsometric: Bool
    var reps: Int
    var weightKg: Double
    var type: SetType
}

/// An estimate of the energy a workout used on top of resting, its active energy as Apple Health
/// counts it, so a calorie tracker can add it to the day without counting resting energy twice.
///
/// The shared Kotlin module works it out (`WorkoutEnergy.kt`, which explains it): from the sets
/// done, each by its exercise, reps, weight and type (see `ExerciseEnergy`), scaled by height when
/// it's known, plus being up and about between sets, for at most three minutes a set.
nonisolated enum WorkoutEnergy {
    /// How much further than the reference a rep moves the weight for someone `heightCm` tall,
    /// as limbs grow with height: 1 when the height isn't known, and from 0.8 to 1.2.
    static func reach(heightCm: Double?) -> Double {
        KitaeCore.WorkoutEnergy.shared.reach(heightCm: heightCm.map { KotlinDouble(double: $0) })
    }

    /// Active kcal for a workout's done sets.
    static func active(sets: [DoneSet], durationMilliseconds: Int64?, bodyKg: Double, heightCm: Double? = nil) -> Double {
        KitaeCore.WorkoutEnergy.shared.active(
            sets: sets.map(\.core),
            durationMilliseconds: durationMilliseconds.map { KotlinLong(longLong: $0) },
            bodyKg: bodyKg,
            heightCm: heightCm.map { KotlinDouble(double: $0) }
        )
    }
}

nonisolated extension BodyLog {
    /// A finished workout's active energy from its done sets, or nil when Kitae can't tell: no
    /// weight yet, or no set done.
    func activeKilocalories(_ workout: WorkoutSummary, sets: [DoneSet]) -> Double? {
        core.activeKilocalories(workout: workout.core, sets: sets.map(\.core))?.doubleValue
    }

    /// The workouts of a stretch of days, their minutes, and their active energy when a weight
    /// is known. `sets` are the done sets by workout.
    func totals(_ workouts: [WorkoutSummary], sets: [Int64: [DoneSet]]) -> WorkoutTotals {
        let done = Dictionary(uniqueKeysWithValues: sets.map { ($0.key.kotlin, $0.value.map(\.core)) })
        let totals = core.totals(workouts: workouts.map(\.core), sets: done)
        return WorkoutTotals(workouts: Int(totals.workouts), minutes: Int(totals.minutes),
                             kilocalories: totals.kilocalories?.doubleValue)
    }
}

/// What the You tab counts for this week.
nonisolated struct WorkoutTotals: Equatable, Sendable {
    var workouts = 0
    var minutes = 0
    /// nil until a weight is known.
    var kilocalories: Double?
}

/// The first day of the week `today` is in, on the phone's first day of the week.
nonisolated func startOfWeek(_ today: Int, firstWeekday: Int) -> Int {
    Int(WorkoutEnergyKt.startOfWeek(today: Int64(today), firstWeekday: Int32(firstWeekday)))
}

nonisolated extension DoneSet {
    /// The shared module's own.
    var core: KitaeCore.DoneSet {
        KitaeCore.DoneSet(exerciseId: exerciseID, libraryKey: libraryKey, target: target, isIsometric: isIsometric,
                          reps: Int32(clamping: reps), weightKg: weightKg, type: type.core)
    }
}

nonisolated extension WorkoutExercise {
    /// Its done sets, for the energy estimate.
    var doneSets: [DoneSet] {
        sets.filter(\.isDone).map {
            DoneSet(exerciseID: exercise.id, libraryKey: exercise.libraryKey, target: exercise.target,
                    isIsometric: exercise.isIsometric, reps: $0.reps, weightKg: exercise.weight($0.weightKg), type: $0.type)
        }
    }
}
