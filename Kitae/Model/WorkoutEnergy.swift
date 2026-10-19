// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

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
/// It's worked out from the sets done, not from how long the workout took: each set by its
/// exercise, reps, weight and type (see `ExerciseEnergy`), counting the body's own weight where
/// the exercise moves it and the recovery after the set. Height, when it's known, scales how far
/// each rep moves: longer limbs move the weight further. On top comes being up and about between
/// sets, half a MET over resting, for at most three minutes a set, so a workout left running or
/// a long chat between sets adds little. Without its length, that's two and a half minutes a set.
///
/// Sex, age and body fat aren't used: they mainly change resting energy, which active energy
/// leaves out. Scott et al. 2014 fitted men's and women's sets with one line of energy against
/// work, the weight times how far the bar moved, and found no difference between them in where
/// that energy came from.
nonisolated enum WorkoutEnergy {
    static let betweenSetsMETs = 0.5
    static let longestMinutesPerSet = 3.0
    static let usualMinutesPerSet = 2.5
    /// About how tall the lifters were whose sets give the costs per kilogram.
    static let referenceHeightCm = 175.0

    /// How much further than the reference a rep moves the weight for someone `heightCm` tall,
    /// as limbs grow with height: 1 when the height isn't known, and from 0.8 to 1.2.
    static func reach(heightCm: Double?) -> Double {
        guard let heightCm, heightCm > 0 else { return 1 }
        return min(max(heightCm / referenceHeightCm, 0.8), 1.2)
    }

    /// Active kcal for a workout's done sets.
    static func active(sets: [DoneSet], durationMilliseconds: Int64?, bodyKg: Double, heightCm: Double? = nil) -> Double {
        guard !sets.isEmpty, bodyKg > 0 else { return 0 }
        let scale = Self.reach(heightCm: heightCm)
        let lifting = sets.reduce(0.0) { total, set in
            let energy = ExerciseEnergy.of(key: set.libraryKey, target: set.target, isIsometric: set.isIsometric)
            let cost = energy.kilocalories(reps: set.reps, weightKg: set.weightKg, bodyKg: bodyKg, reach: scale)
            return total + cost * set.type.energyFactor
        }
        let count = Double(sets.count)
        let length = durationMilliseconds.map { Double($0) / 60_000 } ?? count * usualMinutesPerSet
        let minutes = min(length, count * longestMinutesPerSet)
        return lifting + betweenSetsMETs * bodyKg * minutes / 60
    }
}

nonisolated extension BodyLog {
    /// A finished workout's active energy from its done sets, or nil when Kitae can't tell: no
    /// weight yet, or no set done.
    func activeKilocalories(_ workout: WorkoutSummary, sets: [DoneSet]) -> Double? {
        guard workout.isFinished, !sets.isEmpty, let weight = weightKg(on: workout.date) else { return nil }
        return WorkoutEnergy.active(sets: sets, durationMilliseconds: workout.durationMilliseconds, bodyKg: weight,
                                    heightCm: profile.heightCm)
    }

    /// The workouts of a stretch of days, their minutes, and their active energy when a weight
    /// is known. `sets` are the done sets by workout.
    func totals(_ workouts: [WorkoutSummary], sets: [Int64: [DoneSet]]) -> WorkoutTotals {
        let done = workouts.filter { $0.isFinished && $0.doneCount > 0 }
        let energies = done.map { activeKilocalories($0, sets: sets[$0.id] ?? []) }
        return WorkoutTotals(
            workouts: done.count,
            minutes: done.compactMap(\.minutes).reduce(0, +),
            kilocalories: latestWeight == nil ? nil : energies.compactMap { $0 }.reduce(0, +)
        )
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
    today - (weekday(ofEpochDay: today) - firstWeekday + 7) % 7
}

nonisolated extension WorkoutExercise {
    /// Its done sets, for the energy estimate.
    var doneSets: [DoneSet] {
        sets.filter(\.isDone).map {
            DoneSet(exerciseID: exercise.id, libraryKey: exercise.libraryKey, target: exercise.target,
                    isIsometric: exercise.isIsometric, reps: $0.reps, weightKg: $0.weightKg, type: $0.type)
        }
    }
}
