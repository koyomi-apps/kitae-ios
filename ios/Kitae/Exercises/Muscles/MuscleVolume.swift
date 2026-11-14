// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// How much each muscle was worked over some days, in sets: a done set counts once for the
/// muscles its exercise works most and half for the ones that help, as its page's muscle map
/// shows them. Warm-ups don't count. The shared module counts them (`Muscles.kt`).
nonisolated struct MuscleVolume: Equatable, Sendable {
    let sets: [Muscle: Double]
    let totalSets: Int
    /// The muscles worked, most sets first.
    let ranked: [Muscle]
    /// For the muscle map: the muscles with at least half the sets of the most worked one as
    /// primary, in olive, and the others that were worked as secondary, lighter.
    let shading: MuscleWork

    init(_ counts: [ExerciseSetCount] = []) {
        self.init(KitaeCore.MuscleVolume(counts: counts.map(\.core), muscles: ExerciseMuscles.core))
    }

    /// From a workout's done sets.
    init(doneSets: [DoneSet]) {
        self.init(KitaeCore.MuscleVolume.Companion.shared.of(doneSets: doneSets.map(\.core),
                                                             muscles: ExerciseMuscles.core))
    }

    private init(_ volume: KitaeCore.MuscleVolume) {
        // The sets come as plain `NSNumber`s (see `int64s`).
        var sets: [Muscle: Double] = [:]
        for (muscle, count) in volume.sets as NSDictionary {
            sets[Muscle(muscle as! KitaeCore.Muscle)] = (count as! NSNumber).doubleValue
        }
        self.sets = sets
        totalSets = Int(volume.totalSets)
        ranked = volume.ranked.map { Muscle($0) }
        shading = MuscleWork(volume.shading)
    }
}
