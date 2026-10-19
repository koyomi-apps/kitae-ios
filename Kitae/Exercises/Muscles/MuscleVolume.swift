// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// How much each muscle was worked over some days, in sets: a done set counts once for the
/// muscles its exercise works most and half for the ones that help, as its page's muscle map
/// shows them. Warm-ups don't count.
nonisolated struct MuscleVolume: Equatable, Sendable {
    private(set) var sets: [Muscle: Double] = [:]
    private(set) var totalSets = 0

    init(_ counts: [ExerciseSetCount] = []) {
        for count in counts {
            add(count.sets, of: ExerciseMuscles.work(for: count.exercise))
        }
    }

    /// From a workout's done sets.
    init(doneSets: [DoneSet]) {
        for set in doneSets where set.type != .warmup {
            add(1, of: ExerciseMuscles.work(key: set.libraryKey, target: set.target))
        }
    }

    private mutating func add(_ count: Int, of work: MuscleWork?) {
        totalSets += count
        guard let work else { return }
        for muscle in work.primary {
            sets[muscle, default: 0] += Double(count)
        }
        for muscle in work.secondary {
            sets[muscle, default: 0] += Double(count) / 2
        }
    }

    /// The muscles worked, most sets first.
    var ranked: [Muscle] {
        let order = Dictionary(uniqueKeysWithValues: Muscle.allCases.enumerated().map { ($1, $0) })
        return sets.keys.sorted { a, b in
            let (x, y) = (sets[a] ?? 0, sets[b] ?? 0)
            return x != y ? x > y : (order[a] ?? 0) < (order[b] ?? 0)
        }
    }

    /// For the muscle map: the muscles with at least half the sets of the most worked one as
    /// primary, in olive, and the others that were worked as secondary, lighter.
    var shading: MuscleWork {
        let top = sets.values.max() ?? 0
        let ranked = ranked
        return MuscleWork(
            primary: ranked.filter { (sets[$0] ?? 0) >= top / 2 },
            secondary: ranked.filter { (sets[$0] ?? 0) < top / 2 }
        )
    }
}
