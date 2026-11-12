// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Picks an exercise to add to a day: one done lately, or search by name, narrow it down by
/// muscle, or make a new one.
struct ExercisePicker: View {
    let dayName: String
    /// The exercises the day has already. They're ticked and can't be added twice.
    let added: Set<Exercise.ID>
    let onPick: (Exercise) -> Void

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var exercises: [Exercise] = []
    /// The exercises done most recently, newest first.
    @State private var recentIDs: [Exercise.ID] = []
    @State private var hasLoaded = false
    @State private var search = ""
    @State private var muscle: MuscleGroup?
    @State private var showsEditor = false
    /// An exercise that was just made, to add once the editor is gone.
    @State private var madeExercise: Exercise.ID?

    var body: some View {
        NavigationStack {
            List {
                if !recent.isEmpty {
                    Section {
                        ForEach(recent) { exercise in
                            row(exercise)
                        }
                    } header: {
                        SectionCaption(".recent")
                    }
                }
                ForEach(groups) { group in
                    Section {
                        ForEach(group.exercises) { exercise in
                            row(exercise)
                        }
                    } header: {
                        SectionCaption(".\(group.title.lowercased())")
                    }
                }
            }
            .listSectionSpacing(12)
            .environment(\.defaultMinListRowHeight, 52)
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .overlay {
                if hasLoaded, groups.isEmpty, !search.trimmingCharacters(in: .whitespaces).isEmpty {
                    NoExerciseFound(search: search) { showsEditor = true }
                }
            }
            .safeAreaBar(edge: .top) {
                MuscleChips(selection: $muscle)
                    .padding(.vertical, 6)
            }
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search \(exercises.count) exercises")
            .navigationTitle("Add to \(dayName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("New exercise", systemImage: "plus") { showsEditor = true }
                }
            }
        }
        .sheet(isPresented: $showsEditor, onDismiss: addMadeExercise) {
            ExerciseEditor(draft: ExerciseDraft(name: search.trimmingCharacters(in: .whitespaces), muscle: muscle ?? .chest)) { id in
                madeExercise = id
            }
            .environment(model)
        }
        .task {
            do {
                let loaded = try model.database.read { db in
                    // Enough to fill the section after leaving out the day's own.
                    (try Exercises.all(db), try Exercises.recentIDs(db, limit: Self.recentCount + added.count))
                }
                exercises = loaded.0
                recentIDs = loaded.1
            } catch {
                model.problem = "Couldn't read the exercises. \(error.localizedDescription)"
            }
            hasLoaded = true
        }
    }

    private var groups: [ExerciseGroup] {
        exerciseGroups(exercises, muscle: muscle, search: search)
    }

    /// How many exercises done lately the picker offers first.
    private static let recentCount = 5

    /// The exercises done most recently that the day doesn't have yet, while nothing is
    /// searched for and every muscle shows.
    private var recent: [Exercise] {
        guard muscle == nil, search.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        let byID = Dictionary(exercises.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return Array(recentIDs.filter { !added.contains($0) }.compactMap { byID[$0] }.prefix(Self.recentCount))
    }

    private func row(_ exercise: Exercise) -> some View {
        let isAdded = added.contains(exercise.id)
        return Button {
            onPick(exercise)
            dismiss()
        } label: {
            HStack(spacing: 10) {
                Text(exercise.name)
                    .foregroundStyle(isAdded ? Color.inkSoft : Color.ink)
                Spacer(minLength: 8)
                Image(systemName: isAdded ? "checkmark" : "plus")
                    .fontWeight(.semibold)
                    .foregroundStyle(isAdded ? Color.olive : Color.inkFaint)
            }
            .contentShape(.rect)
        }
        .disabled(isAdded)
        .listRowBackground(Color.card)
        .accessibilityLabel(exercise.name)
        .accessibilityValue(isAdded ? "Already in \(dayName)" : "")
    }

    /// Adds a new exercise to the day straight away, as it was made to be added.
    private func addMadeExercise() {
        guard let madeExercise else { return }
        self.madeExercise = nil
        let exercise = try? model.database.read { db in try Exercises.exercise(db, id: madeExercise) }
        guard let exercise else { return }
        onPick(exercise)
        dismiss()
    }
}
