// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// How an exercise's sets use energy, for a workout's estimate (see `WorkoutEnergy`).
///
/// The figures and the maths are the shared Kotlin module's (`ExerciseEnergy.kt`, which says
/// where each figure comes from), so both phones count the same. This is a Swift copy of one
/// exercise's figures: a rep costs `perKilogram` kcal for each kilogram it moves, the weight
/// logged times `loadFactor` (2 for a pair of dumbbells, -1 for a machine's help) plus
/// `bodyShare` of the body's own weight, all times `sides`. A hold costs `holdMETs` a second
/// instead, for the body and, in a carry, the weight logged times `carryFactor`.
nonisolated struct ExerciseEnergy: Equatable, Sendable {
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

    /// Each of Kitae's exercises', by its library key.
    static let starters: [String: ExerciseEnergy] =
        KitaeCore.ExerciseEnergy.Companion.shared.starters.mapValues { ExerciseEnergy($0) }

    /// An exercise's: its own when it's one of Kitae's, else by its muscle group, or as a hold.
    static func of(key: String?, target: String, isIsometric: Bool) -> ExerciseEnergy {
        ExerciseEnergy(KitaeCore.ExerciseEnergy.Companion.shared.of(key: key, target: target, isIsometric: isIsometric))
    }

    /// The energy of one set, in kcal, on top of resting. `reach` scales how far each rep moves
    /// the weight, by the lifter's height (see `WorkoutEnergy.reach`); a hold doesn't move.
    func kilocalories(reps: Int, weightKg: Double, bodyKg: Double, reach: Double = 1) -> Double {
        core.kilocalories(reps: Int32(clamping: reps), weightKg: weightKg, bodyKg: bodyKg, reach: reach)
    }
}

nonisolated extension ExerciseEnergy {
    init(_ energy: KitaeCore.ExerciseEnergy) {
        self.init(perKilogram: energy.perKilogram, bodyShare: energy.bodyShare, loadFactor: energy.loadFactor,
                  sides: energy.sides, holdMETs: energy.holdMETs?.doubleValue, carryFactor: energy.carryFactor)
    }

    /// The shared module's own.
    var core: KitaeCore.ExerciseEnergy {
        KitaeCore.ExerciseEnergy(perKilogram: perKilogram, bodyShare: bodyShare, loadFactor: loadFactor, sides: sides,
                                 holdMETs: holdMETs.map { KotlinDouble(double: $0) }, carryFactor: carryFactor)
    }
}

nonisolated extension SetType {
    /// How much more a set of this type costs than its logged reps and weight show: 30% more for
    /// sets to failure, drop sets and rest-pause sets (`ExerciseEnergy.kt` says why).
    var energyFactor: Double { core.energyFactor }

    /// The shared module's own.
    var core: KitaeCore.SetType { KitaeCore.SetType.Companion.shared.fromStored(stored: rawValue) }
}
