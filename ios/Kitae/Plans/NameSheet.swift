// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Asks for a name: for a new plan or day, or a new name for one. Tapping a suggestion fills it in.
struct NameSheet: View {
    let title: String
    let placeholder: String
    let actionTitle: String
    let suggestions: [String]
    /// Saves the name. A `NameProblem` it throws is shown under the field.
    let save: (String) throws -> Void

    @State private var name: String
    @State private var problem: String?
    @FocusState private var isFocused: Bool
    @Environment(\.dismiss) private var dismiss

    init(
        _ title: String,
        placeholder: String,
        actionTitle: String,
        name: String = "",
        suggestions: [String] = [],
        save: @escaping (String) throws -> Void
    ) {
        self.title = title
        self.placeholder = placeholder
        self.actionTitle = actionTitle
        self.suggestions = suggestions
        self.save = save
        _name = State(initialValue: name)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                TextField(placeholder, text: $name)
                    .font(.title3)
                    .textInputAutocapitalization(.words)
                    .submitLabel(.done)
                    .focused($isFocused)
                    .onSubmit(submit)
                    .padding(.horizontal, 18)
                    .frame(minHeight: 56)
                    .background(.field, in: .rect(cornerRadius: 18))

                if let problem {
                    Text(problem)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .padding(.horizontal, 8)
                }

                if !suggestions.isEmpty {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(suggestions, id: \.self) { suggestion in
                                DayChip(title: suggestion, isSelected: name == suggestion) {
                                    name = suggestion
                                }
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                    .contentMargins(.horizontal, 16, for: .scrollContent)
                    .padding(.horizontal, -16)
                    .padding(.top, 6)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.paper)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(actionTitle, systemImage: "checkmark", action: submit)
                        .disabled(isBlank)
                }
            }
        }
        .presentationDetents([.height(suggestions.isEmpty ? 200 : 264)])
        .onAppear { isFocused = true }
        .onChange(of: name) { problem = nil }
    }

    private var isBlank: Bool {
        name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submit() {
        guard !isBlank else { return }
        do {
            try save(name)
            dismiss()
        } catch {
            problem = error.localizedDescription
        }
    }
}
