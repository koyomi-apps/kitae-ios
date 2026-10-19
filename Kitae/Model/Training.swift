// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

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
        self = SetType(rawValue: stored) ?? .standard
    }

    /// The letter in a set's label, like the W in W1. Standard sets only have a number.
    var letter: String? {
        switch self {
        case .warmup: "W"
        case .standard: nil
        case .failure: "F"
        case .drop: "D"
        case .restPause: "R"
        }
    }
}

/// Labels sets the way Kitae does: W1, 01, 02, F1, D1, R1. Each type is counted on its
/// own, so standard sets are numbered 01, 02… however many warm-ups come first.
nonisolated func setLabels(for types: [SetType]) -> [String] {
    var counts: [SetType: Int] = [:]
    return types.map { type in
        let number = (counts[type] ?? 0) + 1
        counts[type] = number
        if let letter = type.letter {
            return "\(letter)\(number)"
        }
        return number < 10 ? "0\(number)" : "\(number)"
    }
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

    /// The instructions as steps, without numbers typed in front of them. Kitae for
    /// Android's exercise page reads them the same way.
    var steps: [String] {
        (instructions ?? "")
            .split(whereSeparator: \.isNewline)
            .map { line in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                guard let number = trimmed.firstRange(of: /^\d+[.)]\s*/) else { return trimmed }
                return String(trimmed[number.upperBound...])
            }
            .filter { !$0.isEmpty }
    }

    /// The link to the reference, if it's a web address.
    var referenceURL: URL? {
        guard let reference, !reference.contains(where: \.isWhitespace), let url = URL(string: reference),
              url.scheme == "https" || url.scheme == "http", let host = url.host(), host.contains(".")
        else { return nil }
        return url
    }
}

/// An exercise's details while they're being edited.
nonisolated struct ExerciseDraft: Hashable, Sendable {
    var name = ""
    var muscle: MuscleGroup = .chest
    var isIsometric = false
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

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets.count } }
}

nonisolated struct Plan: Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    var days: [TrainingDay]

    /// Different exercises across the plan's days.
    var exerciseCount: Int {
        Set(days.flatMap { $0.exercises.map(\.exercise.id) }).count
    }

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

/// New positions that put warm-ups in front of the other sets, keeping each group's order.
/// Only the sets that move are returned. `items` must be in their current order.
/// Kitae for Android's `warmupsFirstSlots`.
nonisolated func warmupsFirstSlots<T>(_ items: [T], slot: (T) -> Int, isWarmup: (T) -> Bool) -> [(item: T, slot: Int)] {
    let slots = items.map(slot)
    let wanted = items.filter(isWarmup) + items.filter { !isWarmup($0) }
    return wanted.enumerated().compactMap { index, item in
        slot(item) == slots[index] ? nil : (item, slots[index])
    }
}

/// `items` in a new order, the way a list moves rows: the ones at `source` go in front of the
/// one at `destination`, which counts from before the move.
nonisolated func moved<T>(_ items: [T], from source: IndexSet, to destination: Int) -> [T] {
    let moving = source.filter { items.indices.contains($0) }.map { items[$0] }
    var rest = items.enumerated().filter { !source.contains($0.offset) }.map(\.element)
    let index = destination - source.count(in: 0..<max(destination, 0))
    rest.insert(contentsOf: moving, at: min(max(index, 0), rest.count))
    return rest
}

/// "1 set", "15 sets". Kitae's text is English only for now.
nonisolated func counted(_ count: Int, _ singular: String, _ plural: String) -> String {
    "\(count) \(count == 1 ? singular : plural)"
}

/// A rest length as Kitae shows it: 90 seconds is 1:30.
nonisolated func restLabel(seconds: Int) -> String {
    let minutes = seconds / 60
    let rest = seconds % 60
    return "\(minutes):\(rest < 10 ? "0" : "")\(rest)"
}
