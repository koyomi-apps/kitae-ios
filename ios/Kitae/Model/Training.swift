// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// A set's type. The raw values are how Kitae for Android stores them, so backups
/// stay compatible: don't rename them.
nonisolated enum SetType: String, CaseIterable, Identifiable, Hashable, Sendable {
    case warmup = "Warmup"
    case standard = "Standard"
    case failure = "Failure"
    case drop = "Drop"
    case restPause = "RestPause"

    var id: Self { self }

    var name: String {
        switch self {
        case .warmup: "Warm-up"
        case .standard: "Standard"
        case .failure: "Failure"
        case .drop: "Drop set"
        case .restPause: "Rest-pause"
        }
    }

    /// A type as it's stored. One this version doesn't know counts as standard.
    init(stored: String) {
        self.init(KitaeCore.SetType.Companion.shared.fromStored(stored: stored))
    }

    init(_ type: KitaeCore.SetType) {
        self = SetType(rawValue: type.stored) ?? .standard
    }

    /// The letter in a set's label, like the W in W1. Standard sets only have a number.
    var letter: String? { core.letter }
}

/// Labels sets the way Kitae does: W1, 01, 02, F1, D1, R1. Each type is counted on its
/// own, so standard sets are numbered 01, 02… however many warm-ups come first. The shared
/// module numbers them (`Sets.kt`).
nonisolated func setLabels(for types: [SetType]) -> [String] {
    SetsKt.setLabels(types: types.map(\.core))
}

/// The muscle an exercise mainly works. Raw values are how Kitae for Android stores them.
nonisolated enum MuscleGroup: String, CaseIterable, Identifiable, Hashable, Sendable {
    case biceps = "Biceps"
    case triceps = "Triceps"
    case shoulders = "Shoulders"
    case quads = "Quads"
    case hamstrings = "Hamstrings"
    case calves = "Calves"
    case glutes = "Glutes"
    case core = "Core"
    case chest = "Chest"
    case traps = "Traps"
    case lats = "Lats"
    case upperBack = "UpperBack"

    var id: Self { self }

    var name: String {
        switch self {
        case .upperBack: "Back"
        default: rawValue
        }
    }
}

nonisolated struct Exercise: Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    /// The muscle as it's stored, like "Chest" or "UpperBack".
    var target: String
    var isIsometric = false
    /// False for exercises done with the body alone, like sit-ups: their sets are reps (or
    /// seconds) without weight, and a weight a set has from before is left out.
    var usesWeight = true
    /// Rest after each set of this exercise, unless the set has its own. nil uses the default.
    var restSeconds: Int?
    /// A link to a video or page that shows it.
    var reference: String?
    /// How to do it, one step per line.
    var instructions: String?
    /// One of Kitae's own exercises' lasting name, like "barbell-bench-press", by which its
    /// animation, muscles and energy are found. People's own exercises have none.
    var libraryKey: String?

    var muscle: MuscleGroup? { MuscleGroup(rawValue: target) }

    // These four are the shared module's (`Exercise` in `Tables.kt`).

    /// A set's weight as it counts for this exercise: none if it doesn't use weight.
    func weight(_ kilograms: Double) -> Double {
        core.weight(kilograms: kilograms)
    }

    /// Whether the weight logged is an assisted machine's help, as on assisted dips, so less of
    /// it is better: Kitae's exercises whose energy takes it off the body's weight.
    var isAssisted: Bool { core.isAssisted }

    /// The instructions as steps, without numbers typed in front of them. Kitae for
    /// Android's exercise page reads them the same way.
    var steps: [String] { core.steps }

    /// The link to the reference, if it's a web address.
    var referenceURL: URL? {
        core.referenceLink.flatMap { URL(string: $0) }
    }
}

/// An exercise's details while they're being edited.
nonisolated struct ExerciseDraft: Hashable, Sendable {
    var name = ""
    var muscle: MuscleGroup = .chest
    var isIsometric = false
    var usesWeight = true
    var restSeconds: Int?
    var instructions = ""
    var reference = ""

    init(name: String = "", muscle: MuscleGroup = .chest) {
        self.name = name
        self.muscle = muscle
    }

    init(_ exercise: Exercise) {
        name = exercise.name
        muscle = exercise.muscle ?? .chest
        isIsometric = exercise.isIsometric
        usesWeight = exercise.usesWeight
        restSeconds = exercise.restSeconds
        instructions = exercise.instructions ?? ""
        reference = exercise.reference ?? ""
    }
}

/// A set's values before it's saved. Kitae for Android's `SetDraft`.
nonisolated struct SetDraft: Hashable, Sendable {
    var reps: Int
    /// Always kilograms.
    var weightKg: Double
    var type: SetType = .standard
    /// Rest after this set. nil uses the exercise's rest, or the default.
    var restSeconds: Int?
}

/// A set as it's planned for a training day.
nonisolated struct PlannedSet: Identifiable, Hashable, Sendable {
    var id: Int64
    var type: SetType = .standard
    var reps: Int
    /// Always kilograms. Settings decides the unit people see.
    var weightKg: Double
    /// Rest after this set. nil uses the exercise's rest, or the default.
    var restSeconds: Int?

    var draft: SetDraft {
        SetDraft(reps: reps, weightKg: weightKg, type: type, restSeconds: restSeconds)
    }
}

nonisolated struct DayExercise: Identifiable, Hashable, Sendable {
    var id: Int64
    var exercise: Exercise
    var sets: [PlannedSet]
}

/// A named training day, like Push A or Legs.
nonisolated struct TrainingDay: Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    var exercises: [DayExercise]
    /// From the shared module, which reads the day.
    var setCount: Int
}

nonisolated struct Plan: Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    var days: [TrainingDay]
    /// Different exercises across the plan's days, from the shared module, which reads the plan.
    var exerciseCount: Int

    /// "5 days · 23 exercises".
    var sizeLabel: String {
        planSizeLabel(days: days.count, exercises: exerciseCount)
    }
}

/// A plan as the list of plans shows it.
nonisolated struct PlanSummary: Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    var dayCount = 0
    var exerciseCount = 0

    var sizeLabel: String {
        planSizeLabel(days: dayCount, exercises: exerciseCount)
    }
}

nonisolated func planSizeLabel(days: Int, exercises: Int) -> String {
    "\(counted(days, "day", "days")) · \(counted(exercises, "exercise", "exercises"))"
}

/// `items` in a new order, the way a list moves rows: the ones at `source` go in front of the
/// one at `destination`, which counts from before the move. The shared module moves them
/// (`moved` in `RestTimer.kt`).
nonisolated func moved<T>(_ items: [T], from source: IndexSet, to destination: Int) -> [T] {
    let order = RestTimerKt.moved(
        items: items.indices.map { KotlinInt(int: Int32($0)) },
        source: Set(source.map { KotlinInt(int: Int32(clamping: $0)) }),
        destination: Int32(clamping: destination)
    )
    // The positions come back as plain `NSNumber`s (see `int64s`).
    return (order as NSArray).map { items[($0 as! NSNumber).intValue] }
}

/// "1 set", "15 sets". Kitae's text is English only for now.
nonisolated func counted(_ count: Int, _ singular: String, _ plural: String) -> String {
    "\(count) \(count == 1 ? singular : plural)"
}

/// A rest length as Kitae shows it: 90 seconds is 1:30.
nonisolated func restLabel(seconds: Int) -> String {
    RestTimerKt.restLabel(seconds: Int32(clamping: seconds))
}
