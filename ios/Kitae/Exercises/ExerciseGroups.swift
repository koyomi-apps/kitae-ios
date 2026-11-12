// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The order of the muscle chips, and of the sections of exercises.
nonisolated let muscleOrder: [MuscleGroup] = [
    .chest, .upperBack, .lats, .shoulders, .biceps, .triceps,
    .traps, .core, .quads, .hamstrings, .glutes, .calves,
]

/// The exercises of one muscle, as a section of a list.
nonisolated struct ExerciseGroup: Identifiable, Sendable {
    /// nil for exercises whose muscle this version of Kitae doesn't know.
    let muscle: MuscleGroup?
    let exercises: [Exercise]

    var title: String { muscle?.name ?? "Other" }
    var id: String { title }
}

/// The exercises that match a search and a muscle, in sections by muscle.
nonisolated func exerciseGroups(_ exercises: [Exercise], muscle: MuscleGroup?, search: String) -> [ExerciseGroup] {
    let query = search.trimmingCharacters(in: .whitespaces)
    let found = exercises.filter { exercise in
        (muscle == nil || exercise.muscle == muscle)
            && (query.isEmpty || exercise.name.localizedStandardContains(query))
    }
    var groups = muscleOrder.compactMap { group -> ExerciseGroup? in
        let items = found.filter { $0.muscle == group }
        return items.isEmpty ? nil : ExerciseGroup(muscle: group, exercises: items)
    }
    let others = found.filter { $0.muscle == nil }
    if !others.isEmpty {
        groups.append(ExerciseGroup(muscle: nil, exercises: others))
    }
    return groups
}

/// Chips to show every exercise, or one muscle's.
struct MuscleChips: View {
    @Binding var selection: MuscleGroup?

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                DayChip(title: "All", isSelected: selection == nil) { selection = nil }
                ForEach(muscleOrder) { muscle in
                    DayChip(title: muscle.name, isSelected: selection == muscle) {
                        selection = selection == muscle ? nil : muscle
                    }
                }
            }
        }
        .scrollIndicators(.hidden)
        .contentMargins(.horizontal, 16, for: .scrollContent)
    }
}

/// Shown when a search finds no exercise: offers to make one with that name.
struct NoExerciseFound: View {
    let search: String
    let onMake: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("No “\(search)”", systemImage: "magnifyingglass")
        } description: {
            Text("Check the spelling, or make it an exercise of your own.")
        } actions: {
            Button("New exercise", systemImage: "plus", action: onMake)
                .buttonStyle(.borderedProminent)
        }
    }
}
