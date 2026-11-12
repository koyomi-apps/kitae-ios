// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The third tab: every exercise by muscle, with the heaviest set of the last time it was
/// done, and new exercises of your own.
struct ExercisesView: View {
    @Environment(AppModel.self) private var model
    @State private var exercises: [Exercise] = []
    /// Each exercise's newest workout that had a set of it done.
    @State private var lastSessions: [Exercise.ID: ExerciseSession] = [:]
    @State private var hasLoaded = false
    @State private var search = ""
    @State private var muscle: MuscleGroup?
    @State private var path: [AppRoute] = []
    @State private var showsEditor = false
    /// An exercise that was just made, to open once the editor is gone.
    @State private var madeExercise: Exercise.ID?

    var body: some View {
        NavigationStack(path: $path) {
            List {
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
            .navigationTitle("Exercises")
            // A large title blurs under the chips' safeAreaBar as soon as the list scrolls (iOS 26
            // and 27, issue #8), so the big title sits in the bar beside the + instead.
            .toolbarTitleDisplayMode(.inlineLarge)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("New exercise", systemImage: "plus") { showsEditor = true }
                }
            }
            .appRoutes()
        }
        .sheet(isPresented: $showsEditor, onDismiss: openMadeExercise) {
            ExerciseEditor(draft: ExerciseDraft(name: search.trimmingCharacters(in: .whitespaces), muscle: muscle ?? .chest)) { id in
                madeExercise = id
            }
            .environment(model)
        }
        .task(id: model.historyRevision) {
            load()
            await openDebugScreen()
        }
    }

    private var groups: [ExerciseGroup] {
        exerciseGroups(exercises, muscle: muscle, search: search)
    }

    private func row(_ exercise: Exercise) -> some View {
        NavigationLink(value: AppRoute.exercise(exercise.id)) {
            HStack(spacing: 10) {
                Text(exercise.name)
                    .foregroundStyle(.ink)
                Spacer(minLength: 8)
                if let set = lastSessions[exercise.id]?.best(usesWeight: exercise.usesWeight, assisted: exercise.isAssisted) {
                    let text = model.weightFormatter.weightFirst(reps: set.reps, kilograms: exercise.weight(set.weightKg), isIsometric: exercise.isIsometric)
                    Text(text)
                        .font(.kitaeMono(12, relativeTo: .caption))
                        .foregroundStyle(.inkSoft)
                        .accessibilityLabel("Last time \(text)")
                }
            }
        }
        .listRowBackground(Color.card)
    }

    private func load() {
        do {
            let loaded = try model.database.read { db in
                (try Exercises.all(db), try Exercises.lastSessions(db))
            }
            exercises = loaded.0
            lastSessions = loaded.1
        } catch {
            model.problem = "Couldn't read the exercises. \(error.localizedDescription)"
        }
        hasLoaded = true
    }

    private func openMadeExercise() {
        guard let madeExercise else { return }
        search = ""
        path.append(.exercise(madeExercise))
        self.madeExercise = nil
    }

    /// `-KitaeScreen exercise`: opens Incline Bench's page. `howto` opens Deadlift's, which has no workouts,
    /// so How to do it and its animation come first.
    private func openDebugScreen() async {
        #if DEBUG
        guard let screen = DebugLaunch.screen, ["exercise", "howto"].contains(screen), DebugLaunch.consume(screen),
              let exercise = exercises.first(where: { $0.name == (screen == "howto" ? "Deadlift" : "Incline Bench") })
        else { return }
        try? await Task.sleep(for: .milliseconds(500))
        path = [.exercise(exercise.id)]
        #endif
    }
}
