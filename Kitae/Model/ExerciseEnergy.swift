// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// How an exercise's sets use energy, for a workout's estimate (see `WorkoutEnergy`).
///
/// A rep costs `perKilogram` kcal for each kilogram it moves: the weight logged times
/// `loadFactor` (2 for a pair of dumbbells, less on a sled pushed at an angle), plus `bodyShare`
/// of the body's own weight (most of it in a squat or a pull-up, little in a curl), all times
/// `sides` for exercises done one side at a time. A hold costs `holdMETs` a second instead.
///
/// The costs per kilogram come from sets whose whole cost was measured, aerobic, anaerobic and
/// the recovery after (Scott et al. 2014, "Total Energy Costs – Aerobic and Anaerobic, Exercise
/// and Recovery – of Five Resistance Exercises"): about 0.0075 kcal per kilogram a rep for
/// squats, 0.012 for deadlifts, 0.011 for presses and 0.0034 for calf raises, with the body's
/// weight counted. Rows, pulls, curls and other lifts of the arms use the press's figure, and
/// bodyweight squats and push-ups match Nakagata et al. (2022).
nonisolated struct ExerciseEnergy: Equatable, Sendable {
    var perKilogram: Double
    var bodyShare: Double
    var loadFactor = 1.0
    var sides = 1.0
    /// For holds, whose reps are seconds: METs while holding.
    var holdMETs: Double?

    /// The most a set counts for, against typing slips.
    static let mostReps = 300
    static let mostKilograms = 600.0

    /// The energy of one set, in kcal, on top of resting. `reach` scales how far each rep moves
    /// the weight, by the lifter's height (see `WorkoutEnergy.reach`); a hold doesn't move.
    func kilocalories(reps: Int, weightKg: Double, bodyKg: Double, reach: Double = 1) -> Double {
        let reps = Double(min(max(reps, 0), Self.mostReps))
        if let holdMETs {
            return max(holdMETs - 1, 0) * bodyKg * reps / 3600 * sides
        }
        let load = min(max(weightKg, 0), Self.mostKilograms)
        return perKilogram * (load * loadFactor + bodyShare * bodyKg) * reps * sides * reach
    }

    // Kcal for each kilogram moved, each rep.
    private static let squat = 0.0075
    private static let hinge = 0.012
    private static let arms = 0.011
    private static let raise = 0.012
    private static let calf = 0.0034
    private static let legMachine = 0.008
    private static let core = 0.008

    /// Each of Kitae's exercises', by its library key. Dumbbells are counted as a pair at the
    /// weight of one, as people log them, and a side at a time as both sides done.
    static let starters: [String: ExerciseEnergy] = [
        "dumbbell-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Curls
        "barbell-curls": .init(perKilogram: arms, bodyShare: 0.02), // Barbell Curls
        "ez-bar-preacher-curls": .init(perKilogram: arms, bodyShare: 0.02), // Preacher Curls
        "incline-dumbbell-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Incline Bicep Curls
        "tricep-pushdowns": .init(perKilogram: arms, bodyShare: 0.02), // Tricep Push Down
        "ez-bar-skull-crushers": .init(perKilogram: arms, bodyShare: 0.02), // Skull-Crushers
        "dumbbell-overhead-tricep-extensions": .init(perKilogram: arms, bodyShare: 0.02), // Overhead Extensions
        "dumbbell-lateral-raises": .init(perKilogram: raise, bodyShare: 0.03, loadFactor: 2), // Lateral Raises
        "dumbbell-shoulder-press": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Shoulder Press
        "face-pulls": .init(perKilogram: arms, bodyShare: 0.03), // Face Pulls
        "barbell-back-squats": .init(perKilogram: squat, bodyShare: 0.85), // Squats
        "leg-press": .init(perKilogram: squat, bodyShare: 0.15, loadFactor: 0.7), // Leg Press
        "hack-squats": .init(perKilogram: squat, bodyShare: 0.7, loadFactor: 0.7), // Hack Squats
        "barbell-stiff-legged-deadlift": .init(perKilogram: hinge, bodyShare: 0.55), // Stiff Legged Deadlift
        "lying-leg-curls": .init(perKilogram: legMachine, bodyShare: 0.1), // Lying Leg Curls
        "standing-calf-raises": .init(perKilogram: calf, bodyShare: 1), // Calf Raises
        "barbell-hip-thrusts": .init(perKilogram: 0.007, bodyShare: 0.4), // Hip Thrusts
        "dumbbell-lunges": .init(perKilogram: squat, bodyShare: 0.85, loadFactor: 2), // Lunges
        "sit-ups": .init(perKilogram: core, bodyShare: 0.5), // Sit-ups
        "lying-leg-raises": .init(perKilogram: core, bodyShare: 0.3), // Leg Raises
        "barbell-bench-press": .init(perKilogram: arms, bodyShare: 0.05), // Bench Press
        "barbell-incline-bench-press": .init(perKilogram: arms, bodyShare: 0.05), // Incline Bench
        "pec-deck": .init(perKilogram: arms, bodyShare: 0.02), // Pec Dec
        "dumbbell-flyes": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Chest Fly
        "dumbbell-shrugs": .init(perKilogram: 0.007, bodyShare: 0, loadFactor: 2), // Shrugs
        "lat-pulldowns": .init(perKilogram: arms, bodyShare: 0.05), // Lat Pull-down
        "pull-ups": .init(perKilogram: arms, bodyShare: 0.95), // Pull-ups
        "lat-prayers": .init(perKilogram: arms, bodyShare: 0.1), // Lat Prayers
        "barbell-bent-over-rows": .init(perKilogram: arms, bodyShare: 0.1), // Bent-over Rows
        "chest-supported-dumbbell-rows": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Chest-Supported Rows
        "leg-extensions": .init(perKilogram: legMachine, bodyShare: 0.05), // Leg Extensions
        "behind-the-back-cable-curls": .init(perKilogram: arms, bodyShare: 0.01, sides: 2), // Behind-the-Back Bicep Curls
        "smith-machine-squats": .init(perKilogram: squat, bodyShare: 0.85), // Smith-Squats
        "cable-lateral-raises": .init(perKilogram: raise, bodyShare: 0.015, sides: 2), // Cable Lateral Raises
        "barbell-upright-rows": .init(perKilogram: arms, bodyShare: 0.04), // Upright Rows
        "dumbbell-bench-press": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Dumbbell Bench Press
        "incline-dumbbell-press": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Incline Dumbbell Press
        "barbell-decline-bench-press": .init(perKilogram: arms, bodyShare: 0.05), // Decline Bench Press
        "cable-crossovers": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Cable Crossovers
        "push-ups": .init(perKilogram: 0.014, bodyShare: 0.64), // Push-ups
        "machine-chest-press": .init(perKilogram: arms, bodyShare: 0.05), // Machine Chest Press
        "chin-ups": .init(perKilogram: arms, bodyShare: 0.95), // Chin-ups
        "straight-arm-pulldowns": .init(perKilogram: arms, bodyShare: 0.05), // Straight-Arm Pulldowns
        "seated-cable-rows": .init(perKilogram: arms, bodyShare: 0.05), // Seated Cable Rows
        "single-arm-dumbbell-rows": .init(perKilogram: arms, bodyShare: 0.05, sides: 2), // Single-Arm Dumbbell Rows
        "t-bar-rows": .init(perKilogram: arms, bodyShare: 0.1), // T-Bar Rows
        "barbell-deadlift": .init(perKilogram: 0.0124, bodyShare: 0.6), // Deadlift
        "barbell-overhead-press": .init(perKilogram: arms, bodyShare: 0.05), // Overhead Press
        "arnold-press": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Arnold Press
        "dumbbell-front-raises": .init(perKilogram: raise, bodyShare: 0.03, loadFactor: 2), // Front Raises
        "dumbbell-rear-delt-flyes": .init(perKilogram: raise, bodyShare: 0.03, loadFactor: 2), // Rear Delt Flyes
        "hammer-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Hammer Curls
        "cable-curls": .init(perKilogram: arms, bodyShare: 0.02), // Cable Curls
        "concentration-curls": .init(perKilogram: arms, bodyShare: 0.01, sides: 2), // Concentration Curls
        "dips": .init(perKilogram: arms, bodyShare: 0.95), // Dips
        "close-grip-bench-press": .init(perKilogram: arms, bodyShare: 0.05), // Close-Grip Bench Press
        "dumbbell-tricep-kickbacks": .init(perKilogram: arms, bodyShare: 0.01, sides: 2), // Tricep Kickbacks
        "barbell-front-squats": .init(perKilogram: squat, bodyShare: 0.85), // Front Squats
        "dumbbell-bulgarian-split-squats": .init(perKilogram: squat, bodyShare: 0.8, loadFactor: 2, sides: 2), // Bulgarian Split Squats
        "goblet-squats": .init(perKilogram: squat, bodyShare: 0.85), // Goblet Squats
        "wall-sit": .init(perKilogram: 0, bodyShare: 0, holdMETs: 3.5), // Wall Sit
        "barbell-romanian-deadlift": .init(perKilogram: hinge, bodyShare: 0.55), // Romanian Deadlift
        "seated-leg-curls": .init(perKilogram: legMachine, bodyShare: 0.05), // Seated Leg Curls
        "good-mornings": .init(perKilogram: hinge, bodyShare: 0.55), // Good Mornings
        "glute-bridges": .init(perKilogram: 0.007, bodyShare: 0.4), // Glute Bridges
        "cable-glute-kickbacks": .init(perKilogram: arms, bodyShare: 0.15, sides: 2), // Cable Kickbacks
        "machine-hip-abductions": .init(perKilogram: 0.006, bodyShare: 0), // Hip Abductions
        "seated-calf-raises": .init(perKilogram: calf, bodyShare: 0), // Seated Calf Raises
        "plank": .init(perKilogram: 0, bodyShare: 0, holdMETs: 3.5), // Plank
        "side-plank": .init(perKilogram: 0, bodyShare: 0, sides: 2, holdMETs: 3), // Side Plank
        "crunches": .init(perKilogram: 0.006, bodyShare: 0.35), // Crunches
        "cable-crunches": .init(perKilogram: 0.006, bodyShare: 0.35), // Cable Crunches
        "russian-twists": .init(perKilogram: 0.004, bodyShare: 0.3), // Russian Twists
        "hanging-leg-raises": .init(perKilogram: 0.012, bodyShare: 0.35), // Hanging Leg Raises
        "ab-wheel-rollouts": .init(perKilogram: 0.011, bodyShare: 0.55), // Ab Wheel Rollouts
    ]

    /// One of Kitae's exercises' own, or for people's own exercises, one for their muscle group,
    /// or a hold for isometric ones.
    static func of(key: String?, target: String, isIsometric: Bool) -> ExerciseEnergy {
        if let key, let starter = starters[key] { return starter }
        if isIsometric { return ExerciseEnergy(perKilogram: 0, bodyShare: 0, holdMETs: 3.5) }
        switch MuscleGroup(rawValue: target) {
        case .biceps, .triceps: return ExerciseEnergy(perKilogram: arms, bodyShare: 0.02)
        case .shoulders: return ExerciseEnergy(perKilogram: arms, bodyShare: 0.04)
        case .chest, .lats, .upperBack: return ExerciseEnergy(perKilogram: arms, bodyShare: 0.05)
        case .traps: return ExerciseEnergy(perKilogram: 0.007, bodyShare: 0)
        case .quads: return ExerciseEnergy(perKilogram: squat, bodyShare: 0.4)
        case .glutes: return ExerciseEnergy(perKilogram: 0.007, bodyShare: 0.3)
        case .hamstrings: return ExerciseEnergy(perKilogram: 0.009, bodyShare: 0.2)
        case .calves: return ExerciseEnergy(perKilogram: calf, bodyShare: 0.5)
        case .core: return ExerciseEnergy(perKilogram: core, bodyShare: 0.35)
        case nil: return ExerciseEnergy(perKilogram: 0.01, bodyShare: 0.2)
        }
    }
}

nonisolated extension SetType {
    /// How much more a set of this type costs than its logged reps and weight show.
    ///
    /// Sets taken to failure cost more for the same work, as extra anaerobic and recovery
    /// energy (Scott & Earnest 2011): single bench press sets to failure measured about 43 and
    /// 60 kJ (Scott et al. 2011) where the same lab's sets stopped short of failure come to
    /// about 26 and 45 kJ for the same work (Scott et al. 2014), a third to two thirds more.
    /// Kitae counts 30%, at the low end, as sets logged as standard are often close to failure
    /// too. Drop and rest-pause sets go to failure as well, and their drops and mini-sets are
    /// logged as sets of their own, so they count the same: with the same weight lifted, drop
    /// sets cost no more than straight sets (Enes et al. 2024). A warm-up counts for its own,
    /// lighter weight.
    var energyFactor: Double {
        switch self {
        case .warmup, .standard: 1
        case .failure, .drop, .restPause: 1.3
        }
    }
}
