// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Writes an exercise's note: anything to remember for it, like a seat height or a grip. The
/// note shows on the exercise's page and under its name during workouts. Saving an empty note
/// deletes it.
struct ExerciseNoteSheet: View {
    let exercise: Exercise

    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var text: String
    @FocusState private var isFocused: Bool

    init(exercise: Exercise, note: String?) {
        self.exercise = exercise
        _text = State(initialValue: note ?? "")
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                TextField("Seat height, grip, anything to remember", text: $text, axis: .vertical)
                    .lineLimit(3...8)
                    .focused($isFocused)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 16)
                    .background(.field, in: .rect(cornerRadius: 18))
                Text("It shows on the exercise's page and under its name during workouts.")
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
                    .padding(.horizontal, 8)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.paper)
            .navigationTitle(exercise.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark") {
                        model.saveNote(text, for: exercise.id)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { isFocused = true }
    }
}
