// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Makes a new exercise, or changes one: its name, muscle, whether it's held, its rest, how
/// to do it and a link to see it done. Kitae for Android's exercise editor.
struct ExerciseEditor: View {
    /// The exercise being changed, or nil for a new one.
    let exerciseID: Exercise.ID?
    /// Called with the exercise's id once it's saved.
    let onSave: (Exercise.ID) -> Void
    /// Called once the exercise is deleted.
    let onDelete: () -> Void

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var draft: ExerciseDraft
    @State private var problem: String?
    @State private var confirmsDelete = false
    @FocusState private var isNameFocused: Bool

    /// A new exercise, starting from `draft`.
    init(draft: ExerciseDraft = ExerciseDraft(), onSave: @escaping (Exercise.ID) -> Void = { _ in }) {
        exerciseID = nil
        self.onSave = onSave
        onDelete = {}
        _draft = State(initialValue: draft)
    }

    /// Changes `exercise`.
    init(exercise: Exercise, onSave: @escaping (Exercise.ID) -> Void = { _ in }, onDelete: @escaping () -> Void = {}) {
        exerciseID = exercise.id
        self.onSave = onSave
        self.onDelete = onDelete
        _draft = State(initialValue: ExerciseDraft(exercise))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $draft.name)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .focused($isNameFocused)
                        .listRowBackground(Color.card)
                    Picker("Muscle", selection: $draft.muscle) {
                        ForEach(muscleOrder) { muscle in
                            Text(muscle.name).tag(muscle)
                        }
                    }
                    .listRowBackground(Color.card)
                    Toggle("Isometric", isOn: $draft.isIsometric)
                        .listRowBackground(Color.card)
                    Toggle("Uses weight", isOn: $draft.usesWeight)
                        .listRowBackground(Color.card)
                } footer: {
                    if let problem {
                        Text(problem)
                            .foregroundStyle(.red)
                    } else {
                        Text("Isometric exercises, like a plank, are held, so they count seconds instead of reps. Exercises done with your body alone, like sit-ups, don't use weight, so their sets have none.")
                    }
                }

                Section {
                    Picker("Rest after each set", selection: $draft.restSeconds) {
                        Text("Default, \(restSetting(seconds: model.defaultRestSeconds))").tag(Int?.none)
                        ForEach(restChoices, id: \.self) { seconds in
                            Text(restLabel(seconds: seconds)).tag(Int?.some(seconds))
                        }
                    }
                    .listRowBackground(Color.card)
                } footer: {
                    Text("Used after each set of this exercise, unless the set has its own rest.")
                }

                Section {
                    TextField("One step per line", text: $draft.instructions, axis: .vertical)
                        .lineLimit(4...)
                        .listRowBackground(Color.card)
                } header: {
                    SectionCaption(".how to do it")
                }

                Section {
                    TextField("A link to a video or page", text: $draft.reference)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .listRowBackground(Color.card)
                } header: {
                    SectionCaption(".see it done")
                } footer: {
                    if !isReferenceValid {
                        Text("That doesn't look like a web address.")
                            .foregroundStyle(.red)
                    }
                }

                if let exerciseID {
                    Section {
                        Button("Delete exercise", role: .destructive) {
                            confirmsDelete = true
                        }
                        .disabled(!model.canDeleteExercise(exerciseID))
                        .listRowBackground(Color.card)
                    } footer: {
                        if !model.canDeleteExercise(exerciseID) {
                            Text("It can't be deleted while it's in the workout you're doing.")
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .navigationTitle(exerciseID == nil ? "New exercise" : "Edit exercise")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark", action: save)
                        .disabled(!canSave)
                }
            }
            .confirmationDialog(
                "Delete \(draft.name.trimmingCharacters(in: .whitespaces))?",
                isPresented: $confirmsDelete,
                titleVisibility: .visible
            ) {
                Button("Delete exercise", role: .destructive, action: delete)
            } message: {
                Text("It will be taken out of your plans, and its sets out of past workouts. This can't be undone.")
            }
        }
        .onAppear {
            if exerciseID == nil, draft.name.isEmpty { isNameFocused = true }
        }
        .onChange(of: draft.name) { problem = nil }
    }

    /// The usual rest lengths, plus the exercise's own if it's another one.
    private var restChoices: [Int] {
        guard let seconds = draft.restSeconds, !RestSection.choices.contains(seconds) else { return RestSection.choices }
        return (RestSection.choices + [seconds]).sorted()
    }

    private var isReferenceValid: Bool {
        guard let saved = Exercises.savedReference(draft.reference) else { return true }
        return Exercise(id: 0, name: "", target: "", reference: saved).referenceURL != nil
    }

    private var canSave: Bool {
        !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && isReferenceValid
    }

    private func save() {
        guard canSave else { return }
        do {
            let id = try model.saveExercise(draft, id: exerciseID)
            onSave(id)
            dismiss()
        } catch let nameProblem as NameProblem {
            problem = nameProblem.localizedDescription
        } catch {
            problem = "Couldn't save it. \(error.localizedDescription)"
        }
    }

    private func delete() {
        guard let exerciseID else { return }
        model.deleteExercise(exerciseID)
        dismiss()
        onDelete()
    }
}
