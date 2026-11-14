// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// Checks that the shared Kotlin module counts energy exactly as the iPhone's Swift did, before the
/// Swift copy goes: every exercise's figures, people's own exercises by muscle group, and a few
/// hundred generated workouts with every set type, odd weights and reps, lengths and heights.
struct EnergyParityTests {
    private let targets = MuscleGroup.allCases.map(\.rawValue) + ["Forearms", ""]

    @Test func everyExerciseHasTheSameFigures() {
        #expect(Set(ExerciseEnergy.starters.keys) == Set(OldExerciseEnergy.starters.keys))
        for (key, old) in OldExerciseEnergy.starters {
            #expect(ExerciseEnergy.starters[key].map { figures($0) } == figures(old), "\(key)")
        }
        for target in targets {
            for isometric in [false, true] {
                let old = OldExerciseEnergy.of(key: nil, target: target, isIsometric: isometric)
                #expect(figures(ExerciseEnergy.of(key: nil, target: target, isIsometric: isometric)) == figures(old), "\(target)")
            }
        }
        #expect(SetType.allCases.map(\.energyFactor) == SetType.allCases.map(\.oldEnergyFactor))
    }

    @Test func generatedWorkoutsCountTheSame() {
        var random = SplitMix64(seed: 20261113)
        let keys = OldExerciseEnergy.starters.keys.sorted()
        var exact = 0
        for _ in 0..<500 {
            let sets = (0..<Int.random(in: 0...30, using: &random)).map { _ in
                DoneSet(exerciseID: 0,
                        libraryKey: Int.random(in: 0..<4, using: &random) == 0 ? nil : keys.randomElement(using: &random),
                        target: targets.randomElement(using: &random) ?? "",
                        isIsometric: Int.random(in: 0..<5, using: &random) == 0,
                        reps: Int.random(in: -2...320, using: &random),
                        weightKg: Double.random(in: -10...1300, using: &random).rounded(.down) / 2,
                        type: SetType.allCases.randomElement(using: &random) ?? .standard)
            }
            let duration: Int64? = Bool.random(using: &random) ? nil : Int64.random(in: 0...(5 * 3_600_000), using: &random)
            let bodyKg = Int.random(in: 0..<20, using: &random) == 0 ? 0 : Double.random(in: 35...180, using: &random)
            let heightCm: Double? = Bool.random(using: &random) ? nil : Double.random(in: 0...250, using: &random)
            let old = OldWorkoutEnergy.active(sets: sets, durationMilliseconds: duration, bodyKg: bodyKg, heightCm: heightCm)
            let new = WorkoutEnergy.active(sets: sets, durationMilliseconds: duration, bodyKg: bodyKg, heightCm: heightCm)
            #expect(abs(new - old) <= 1e-9 * max(1, abs(old)))
            if new == old { exact += 1 }
        }
        #expect(exact == 500, "\(exact) of 500 workouts came out to the same bits")
        for height in [nil, 0, 120, 160, 175, 190, 230] as [Double?] {
            #expect(WorkoutEnergy.reach(heightCm: height) == OldWorkoutEnergy.reach(heightCm: height))
        }
    }

    private func figures(_ energy: ExerciseEnergy) -> [Double?] {
        [energy.perKilogram, energy.bodyShare, energy.loadFactor, energy.sides, energy.holdMETs, energy.carryFactor]
    }

    private func figures(_ energy: OldExerciseEnergy) -> [Double?] {
        [energy.perKilogram, energy.bodyShare, energy.loadFactor, energy.sides, energy.holdMETs, energy.carryFactor]
    }
}

/// A seeded random number generator, so every run checks the same workouts.
private struct SplitMix64: RandomNumberGenerator {
    var state: UInt64

    init(seed: UInt64) { state = seed }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

// The Swift copy, as it was.

nonisolated enum OldWorkoutEnergy {
    static let betweenSetsMETs = 0.5
    static let longestMinutesPerSet = 3.0
    static let usualMinutesPerSet = 2.5
    static let referenceHeightCm = 175.0

    static func reach(heightCm: Double?) -> Double {
        guard let heightCm, heightCm > 0 else { return 1 }
        return min(max(heightCm / referenceHeightCm, 0.8), 1.2)
    }

    static func active(sets: [DoneSet], durationMilliseconds: Int64?, bodyKg: Double, heightCm: Double? = nil) -> Double {
        guard !sets.isEmpty, bodyKg > 0 else { return 0 }
        let scale = Self.reach(heightCm: heightCm)
        let lifting = sets.reduce(0.0) { total, set in
            let energy = OldExerciseEnergy.of(key: set.libraryKey, target: set.target, isIsometric: set.isIsometric)
            let cost = energy.kilocalories(reps: set.reps, weightKg: set.weightKg, bodyKg: bodyKg, reach: scale)
            return total + cost * set.type.oldEnergyFactor
        }
        let count = Double(sets.count)
        let length = durationMilliseconds.map { Double($0) / 60_000 } ?? count * usualMinutesPerSet
        let minutes = min(length, count * longestMinutesPerSet)
        return lifting + betweenSetsMETs * bodyKg * minutes / 60
    }
}

/// How an exercise's sets use energy, for a workout's estimate (see `WorkoutEnergy`).
///
/// A rep costs `perKilogram` kcal for each kilogram it moves: the weight logged times
/// `loadFactor` (2 for a pair of dumbbells, less on a sled pushed at an angle, -1 for a machine's
/// help, which comes off the body's weight), plus `bodyShare` of the body's own weight (most of it
/// in a squat or a pull-up, little in a curl), all times `sides` for exercises done one side at a
/// time. A hold costs `holdMETs` a second instead, for the body and, in a carry, for the weight
/// carried as well (the weight logged times `carryFactor`).
///
/// The costs per kilogram come from sets whose whole cost was measured, aerobic, anaerobic and
/// the recovery after (Scott et al. 2014, "Total Energy Costs – Aerobic and Anaerobic, Exercise
/// and Recovery – of Five Resistance Exercises"): about 0.0075 kcal per kilogram a rep for
/// squats, 0.012 for deadlifts, 0.011 for presses and 0.0034 for calf raises, with the body's
/// weight counted. Rows, pulls, curls and other lifts of the arms use the press's figure, and
/// bodyweight squats and push-ups match Nakagata et al. (2022).
nonisolated struct OldExerciseEnergy: Equatable, Sendable {
    var perKilogram: Double
    var bodyShare: Double
    var loadFactor = 1.0
    var sides = 1.0
    /// For holds, whose reps are seconds: METs while holding.
    var holdMETs: Double?
    /// For carries, holds that walk with the weight: the weight logged times this counts like
    /// body weight (2 for a pair of dumbbells).
    var carryFactor = 0.0

    /// The weight logged is an assisted machine's help, which comes off the body's weight.
    var isAssisted: Bool { loadFactor < 0 }

    /// The most a set counts for, against typing slips.
    static let mostReps = 300
    static let mostKilograms = 600.0

    /// The energy of one set, in kcal, on top of resting. `reach` scales how far each rep moves
    /// the weight, by the lifter's height (see `WorkoutEnergy.reach`); a hold doesn't move.
    func kilocalories(reps: Int, weightKg: Double, bodyKg: Double, reach: Double = 1) -> Double {
        let reps = Double(min(max(reps, 0), Self.mostReps))
        let load = min(max(weightKg, 0), Self.mostKilograms)
        if let holdMETs {
            return max(holdMETs - 1, 0) * (bodyKg + load * carryFactor) * reps / 3600 * sides
        }
        return perKilogram * max(load * loadFactor + bodyShare * bodyKg, 0) * reps * sides * reach
    }

    // Kcal for each kilogram moved, each rep.
    private static let squat = 0.0075
    private static let hinge = 0.012
    private static let arms = 0.011
    private static let raise = 0.012
    private static let calf = 0.0034
    private static let legMachine = 0.008
    private static let core = 0.008

    /// Each of Kitae's exercises', by its library key: the starters, then the library's later
    /// ones. Dumbbells are counted as a pair at the weight of one, as people log them, and a side
    /// at a time as both sides done.
    static let starters: [String: OldExerciseEnergy] = [
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
        // The library's later exercises.
        "decline-dumbbell-press": .init(perKilogram: arms, bodyShare: 0.05, loadFactor: 2), // Decline Dumbbell Press
        "smith-machine-bench-press": .init(perKilogram: arms, bodyShare: 0.05), // Smith Machine Bench Press
        "smith-machine-incline-press": .init(perKilogram: arms, bodyShare: 0.05), // Smith Machine Incline Press
        "incline-machine-press": .init(perKilogram: arms, bodyShare: 0.05), // Incline Machine Press
        "incline-dumbbell-flyes": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Incline Dumbbell Flyes
        "low-to-high-cable-flyes": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Low-to-High Cable Flyes
        // Hands or feet raised a bench's height: about 48% or 72% of the body's weight on the
        // hands, from Ebben et al. (2011), who measured 64% in a push-up on the floor.
        "incline-push-ups": .init(perKilogram: 0.014, bodyShare: 0.48), // Incline Push-ups
        "decline-push-ups": .init(perKilogram: 0.014, bodyShare: 0.72), // Decline Push-ups
        "chest-dips": .init(perKilogram: arms, bodyShare: 0.95), // Chest Dips
        "seated-barbell-shoulder-press": .init(perKilogram: arms, bodyShare: 0.05), // Seated Barbell Shoulder Press
        "machine-shoulder-press": .init(perKilogram: arms, bodyShare: 0.05), // Machine Shoulder Press
        "smith-machine-shoulder-press": .init(perKilogram: arms, bodyShare: 0.05), // Smith Machine Shoulder Press
        // The legs' dip and drive lift the body a little each rep too.
        "barbell-push-press": .init(perKilogram: arms, bodyShare: 0.25), // Push Press
        // The hand lifts the plates on the bar's end as far as itself; one arm at a time.
        "landmine-press": .init(perKilogram: arms, bodyShare: 0.03, sides: 2), // Landmine Press
        // About 60% of the body's weight on the hands: where the body's centre of mass sits
        // between hands and feet in the figure, scaled to Ebben et al.'s 64% for a push-up.
        "pike-push-ups": .init(perKilogram: 0.014, bodyShare: 0.6), // Pike Push-ups
        "machine-lateral-raises": .init(perKilogram: raise, bodyShare: 0.03), // Machine Lateral Raises
        "cable-front-raises": .init(perKilogram: raise, bodyShare: 0.03), // Cable Front Raises
        "plate-front-raises": .init(perKilogram: raise, bodyShare: 0.03), // Plate Front Raises
        "reverse-pec-deck": .init(perKilogram: raise, bodyShare: 0.03), // Reverse Pec Deck
        "cable-rear-delt-flyes": .init(perKilogram: raise, bodyShare: 0.03, loadFactor: 2), // Cable Rear Delt Flyes
        "band-pull-aparts": .init(perKilogram: raise, bodyShare: 0.03), // Band Pull-Aparts
        "dumbbell-upright-rows": .init(perKilogram: arms, bodyShare: 0.04, loadFactor: 2), // Dumbbell Upright Rows
        "cable-upright-rows": .init(perKilogram: arms, bodyShare: 0.04), // Cable Upright Rows
        "ez-bar-curls": .init(perKilogram: arms, bodyShare: 0.02), // EZ-Bar Curls
        "dumbbell-preacher-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Dumbbell Preacher Curls
        "machine-preacher-curls": .init(perKilogram: arms, bodyShare: 0.02), // Machine Preacher Curls
        "dumbbell-spider-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Spider Curls
        "dumbbell-zottman-curls": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Zottman Curls
        "cable-hammer-curls": .init(perKilogram: arms, bodyShare: 0.02), // Cable Hammer Curls
        "barbell-reverse-curls": .init(perKilogram: arms, bodyShare: 0.02), // Reverse Curls
        // The weight moves only as far as the hand turns, about a fifth as far as in a curl.
        "dumbbell-wrist-curls": .init(perKilogram: arms * 0.2, bodyShare: 0, loadFactor: 2), // Wrist Curls
        "dumbbell-reverse-wrist-curls": .init(perKilogram: arms * 0.2, bodyShare: 0, loadFactor: 2), // Reverse Wrist Curls
        "straight-bar-pushdowns": .init(perKilogram: arms, bodyShare: 0.02), // Straight-Bar Pushdowns
        "cable-overhead-tricep-extensions": .init(perKilogram: arms, bodyShare: 0.02), // Cable Overhead Extensions
        "ez-bar-overhead-tricep-extensions": .init(perKilogram: arms, bodyShare: 0.02), // EZ-Bar Overhead Extensions
        "dumbbell-skull-crushers": .init(perKilogram: arms, bodyShare: 0.02, loadFactor: 2), // Dumbbell Skull-Crushers
        "cable-tricep-kickbacks": .init(perKilogram: arms, bodyShare: 0.01, sides: 2), // Cable Tricep Kickbacks
        // About 60% of the body's weight on the hands: where its centre of mass sits between the
        // hands and the heels in the figure, scaled as for pike push-ups.
        "bench-dips": .init(perKilogram: arms, bodyShare: 0.6), // Bench Dips
        // The machine's help comes off the body's weight.
        "assisted-dips": .init(perKilogram: arms, bodyShare: 0.95, loadFactor: -1), // Assisted Dips
        // Hands together under the chest carry a little more of the body: about 68%, scaled as for
        // pike push-ups.
        "diamond-push-ups": .init(perKilogram: 0.014, bodyShare: 0.68), // Diamond Push-ups
        "close-grip-lat-pulldowns": .init(perKilogram: arms, bodyShare: 0.05), // Close-Grip Lat Pull-down
        "single-arm-lat-pulldowns": .init(perKilogram: arms, bodyShare: 0.025, sides: 2), // Single-Arm Lat Pull-down
        "assisted-pull-ups": .init(perKilogram: arms, bodyShare: 0.95, loadFactor: -1), // Assisted Pull-ups
        "neutral-grip-pull-ups": .init(perKilogram: arms, bodyShare: 0.95), // Neutral-Grip Pull-ups
        "dumbbell-pullovers": .init(perKilogram: arms, bodyShare: 0.02), // Dumbbell Pullovers
        // From the knees up, the bar travels about 60% as far as in a deadlift.
        "barbell-rack-pulls": .init(perKilogram: hinge * 0.6, bodyShare: 0.55), // Rack Pulls
        "dumbbell-bent-over-rows": .init(perKilogram: arms, bodyShare: 0.1, loadFactor: 2), // Dumbbell Bent-over Rows
        "barbell-pendlay-rows": .init(perKilogram: arms, bodyShare: 0.1), // Pendlay Rows
        "barbell-seal-rows": .init(perKilogram: arms, bodyShare: 0.05), // Seal Rows
        "machine-rows": .init(perKilogram: arms, bodyShare: 0.05), // Machine Rows
        "single-arm-cable-rows": .init(perKilogram: arms, bodyShare: 0.025, sides: 2), // Single-Arm Cable Rows
        // About 80% of the body's weight hangs from the hands: where its centre of mass sits
        // between the bar and the heels in the figure, scaled as for pike push-ups.
        "inverted-rows": .init(perKilogram: arms, bodyShare: 0.8), // Inverted Rows
        "barbell-shrugs": .init(perKilogram: 0.007, bodyShare: 0), // Barbell Shrugs
        // Walking at a moderate pace, about 3.5 METs, with the weights counted like body weight,
        // as in Pandolf et al.'s (1977) equation for walking with a load.
        "dumbbell-farmers-carry": .init(perKilogram: 0, bodyShare: 0, holdMETs: 3.5, carryFactor: 2), // Farmer's Carry
        "trap-bar-deadlift": .init(perKilogram: 0.0124, bodyShare: 0.6), // Trap Bar Deadlift
        "barbell-lunges": .init(perKilogram: squat, bodyShare: 0.85), // Barbell Lunges
        "dumbbell-walking-lunges": .init(perKilogram: squat, bodyShare: 0.85, loadFactor: 2), // Walking Lunges
        "dumbbell-reverse-lunges": .init(perKilogram: squat, bodyShare: 0.85, loadFactor: 2), // Reverse Lunges
        "dumbbell-split-squats": .init(perKilogram: squat, bodyShare: 0.8, loadFactor: 2, sides: 2), // Split Squats
        // Each step lifts the whole body the bench's height, about as far as a squat moves it.
        "dumbbell-step-ups": .init(perKilogram: squat, bodyShare: 0.95, loadFactor: 2, sides: 2), // Step-ups
        "dumbbell-squats": .init(perKilogram: squat, bodyShare: 0.85, loadFactor: 2), // Dumbbell Squats
        "bodyweight-squats": .init(perKilogram: squat, bodyShare: 0.85), // Bodyweight Squats
        "barbell-box-squats": .init(perKilogram: squat, bodyShare: 0.85), // Box Squats
        "dumbbell-romanian-deadlift": .init(perKilogram: hinge, bodyShare: 0.55, loadFactor: 2), // Dumbbell Romanian Deadlift
        // The free leg sinks as the body rises, taking back some of the lift.
        "dumbbell-single-leg-romanian-deadlift": .init(perKilogram: hinge, bodyShare: 0.4, sides: 2), // Single-Leg Romanian Deadlift
        "standing-leg-curls": .init(perKilogram: legMachine, bodyShare: 0.05, sides: 2), // Standing Leg Curls
        // The body above the knees, most of its weight, falls and rises about two thirds as far as
        // a squat moves it.
        "nordic-hamstring-curls": .init(perKilogram: squat * 0.7, bodyShare: 0.85), // Nordic Curls
        // The bar travels about a quarter less than in a deadlift: Escamilla et al. (2000) measured
        // 25 to 40% more distance and work in the conventional one.
        "barbell-sumo-deadlift": .init(perKilogram: 0.0124 * 0.75, bodyShare: 0.6), // Sumo Deadlift
        "machine-hip-thrusts": .init(perKilogram: 0.007, bodyShare: 0.4), // Machine Hip Thrusts
        "single-leg-hip-thrusts": .init(perKilogram: 0.007, bodyShare: 0.4, sides: 2), // Single-Leg Hip Thrusts
        "barbell-glute-bridges": .init(perKilogram: 0.007, bodyShare: 0.4), // Barbell Glute Bridges
        "cable-pull-throughs": .init(perKilogram: hinge, bodyShare: 0.55), // Cable Pull-throughs
        "kettlebell-swings": .init(perKilogram: hinge, bodyShare: 0.55), // Kettlebell Swings
        "back-extensions": .init(perKilogram: hinge, bodyShare: 0.5), // Back Extensions
        "machine-hip-adductions": .init(perKilogram: 0.006, bodyShare: 0), // Hip Adductions
        "cable-hip-abductions": .init(perKilogram: 0.006, bodyShare: 0.05, sides: 2), // Cable Hip Abductions
        // Sitting, only the sled moves, at the leg press's angle.
        "leg-press-calf-raises": .init(perKilogram: calf, bodyShare: 0, loadFactor: 0.7), // Leg Press Calf Raises
        "single-leg-calf-raises": .init(perKilogram: calf, bodyShare: 1, sides: 2), // Single-Leg Calf Raises
        "smith-machine-calf-raises": .init(perKilogram: calf, bodyShare: 1), // Smith Machine Calf Raises
        // The shins hang, so the legs rise about 80% as far as in a hanging leg raise.
        "hanging-knee-raises": .init(perKilogram: 0.012 * 0.8, bodyShare: 0.35), // Hanging Knee Raises
        "captains-chair-knee-raises": .init(perKilogram: 0.012 * 0.8, bodyShare: 0.35), // Captain's Chair Knee Raises
        // The legs rise all the way to the bar, about twice as far as in a hanging leg raise.
        "toes-to-bar": .init(perKilogram: 0.012 * 2, bodyShare: 0.35), // Toes-to-Bar
        // From 30 degrees below level, the upper body rises about 1.6 times as far as in a sit-up.
        "decline-sit-ups": .init(perKilogram: core * 1.6, bodyShare: 0.5), // Decline Sit-ups
        // A sit-up and a leg raise at once, each rising a little less far.
        "v-ups": .init(perKilogram: core, bodyShare: 0.7), // V-ups
        "bicycle-crunches": .init(perKilogram: 0.006, bodyShare: 0.35), // Bicycle Crunches
        // The hips and legs curl up instead of the shoulders.
        "reverse-crunches": .init(perKilogram: 0.006, bodyShare: 0.4), // Reverse Crunches
        "machine-crunches": .init(perKilogram: 0.006, bodyShare: 0.35), // Machine Crunches
        // An arm and the other leg move each rep: about a fifth of the body.
        "dead-bugs": .init(perKilogram: core, bodyShare: 0.2), // Dead Bugs
        "bird-dogs": .init(perKilogram: core, bodyShare: 0.2), // Bird Dogs
        "hollow-body-hold": .init(perKilogram: 0, bodyShare: 0, holdMETs: 3.5), // Hollow Body Hold
        // Each rep drives one leg, about a sixth of the body, in and back.
        "mountain-climbers": .init(perKilogram: core, bodyShare: 0.16), // Mountain Climbers
        // The hands press across the cable's pull, so the stack moves only about a quarter as far
        // as in a press.
        "cable-pallof-press": .init(perKilogram: arms * 0.25, bodyShare: 0.02, sides: 2), // Pallof Press
        "cable-woodchoppers": .init(perKilogram: arms, bodyShare: 0.05, sides: 2), // Cable Woodchoppers
        // Bending sideways costs about as much as a crunch.
        "dumbbell-side-bends": .init(perKilogram: 0.006, bodyShare: 0.3, sides: 2), // Side Bends
    ]

    /// One of Kitae's exercises' own, or for people's own exercises, one for their muscle group,
    /// or a hold for isometric ones.
    static func of(key: String?, target: String, isIsometric: Bool) -> OldExerciseEnergy {
        if let key, let starter = starters[key] { return starter }
        if isIsometric { return OldExerciseEnergy(perKilogram: 0, bodyShare: 0, holdMETs: 3.5) }
        switch MuscleGroup(rawValue: target) {
        case .biceps, .triceps: return OldExerciseEnergy(perKilogram: arms, bodyShare: 0.02)
        case .shoulders: return OldExerciseEnergy(perKilogram: arms, bodyShare: 0.04)
        case .chest, .lats, .upperBack: return OldExerciseEnergy(perKilogram: arms, bodyShare: 0.05)
        case .traps: return OldExerciseEnergy(perKilogram: 0.007, bodyShare: 0)
        case .quads: return OldExerciseEnergy(perKilogram: squat, bodyShare: 0.4)
        case .glutes: return OldExerciseEnergy(perKilogram: 0.007, bodyShare: 0.3)
        case .hamstrings: return OldExerciseEnergy(perKilogram: 0.009, bodyShare: 0.2)
        case .calves: return OldExerciseEnergy(perKilogram: calf, bodyShare: 0.5)
        case .core: return OldExerciseEnergy(perKilogram: core, bodyShare: 0.35)
        case nil: return OldExerciseEnergy(perKilogram: 0.01, bodyShare: 0.2)
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
    var oldEnergyFactor: Double {
        switch self {
        case .warmup, .standard: 1
        case .failure, .drop, .restPause: 1.3
        }
    }
}
