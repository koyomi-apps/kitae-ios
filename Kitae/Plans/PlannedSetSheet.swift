// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

struct PlannedSetTarget: Identifiable, Hashable {
    let id: PlannedSet.ID
}

/// Opens from a set in the plan editor: its reps and weight, its type and rest, and deleting it.
struct PlannedSetSheet: View {
    let editor: PlanEditor
    let target: PlannedSetTarget
    let formatter: WeightFormatter
    let defaultRestSeconds: Int

    @Environment(\.dismiss) private var dismiss
    @FocusState private var focus: Field?
    @State private var repsText = ""
    @State private var weightText = ""

    private enum Field: Hashable {
        case reps
        case weight
    }

    var body: some View {
        NavigationStack {
            List {
                if let set {
                    Section {
                        HStack(spacing: 8) {
                            field(isIsometric ? ".seconds" : ".reps", text: $repsText, keyboard: .numberPad, value: .reps)
                            if usesWeight {
                                field(".weight", text: $weightText, unit: formatter.unit.symbol, keyboard: .decimalPad, value: .weight)
                            }
                        }
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    }

                    SetTypeSection(selected: set.type) { type in
                        editor.updateSet(target.id) { $0.type = type }
                    }

                    RestSection(seconds: restSelection, usualSeconds: exerciseRest)

                    Section {
                        Button("Delete set", role: .destructive) {
                            dismiss()
                            editor.deleteSet(target.id)
                        }
                        .listRowBackground(Color.card)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.paper)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(exercise?.exercise.name ?? "Set")
            .navigationSubtitle("set \(label)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") { dismiss() }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    keyboardSteps
                }
            }
        }
        .presentationDetents([.fraction(0.84), .large])
        .onAppear {
            guard let set else { return }
            repsText = "\(set.reps)"
            weightText = formatter.string(kilograms: set.weightKg)
        }
        .onChange(of: set?.reps) { _, reps in
            if let reps, Int(repsText) != reps { repsText = "\(reps)" }
        }
        .onChange(of: set?.weightKg) { _, kg in
            guard let kg else { return }
            if let typed = formatter.kilograms(from: weightText), abs(typed - kg) < 0.0001 { return }
            weightText = formatter.string(kilograms: kg)
        }
        .onChange(of: repsText) { _, text in
            guard let set, let reps = Int(text), reps >= 0, reps != set.reps else { return }
            editor.updateSet(target.id) { $0.reps = reps }
        }
        .onChange(of: weightText) { _, text in
            guard let set, let kg = formatter.kilograms(from: text), abs(kg - set.weightKg) >= 0.0001 else { return }
            editor.updateSet(target.id) { $0.weightKg = kg }
        }
        .task { await openDebugScreen() }
        .onChange(of: focus) { old, _ in
            // Tidy what was typed once the field loses the keyboard.
            guard let set else { return }
            if old == .reps, Int(repsText) == nil { repsText = "\(set.reps)" }
            if old == .weight { weightText = formatter.string(kilograms: set.weightKg) }
        }
    }

    private var set: PlannedSet? { editor.plannedSet(target.id) }
    private var exercise: DayExercise? { editor.exercise(containing: target.id) }
    private var isIsometric: Bool { exercise?.exercise.isIsometric ?? false }
    private var usesWeight: Bool { exercise?.exercise.usesWeight ?? true }

    /// The set's label as it is now, so changing its type to failure turns 03 into F1.
    private var label: String {
        guard let exercise, let index = exercise.sets.firstIndex(where: { $0.id == target.id }) else { return "" }
        return setLabels(for: exercise.sets.map(\.type))[index]
    }

    private var exerciseRest: Int {
        exercise?.exercise.restSeconds ?? defaultRestSeconds
    }

    private var restSelection: Binding<Int?> {
        Binding(
            get: { set?.restSeconds },
            set: { seconds in editor.updateSet(target.id) { $0.restSeconds = seconds } }
        )
    }

    private func field(_ caption: String, text: Binding<String>, unit: String? = nil, keyboard: UIKeyboardType, value: Field) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(caption)
                .font(.kitaeMono(10, relativeTo: .caption2))
                .foregroundStyle(.inkSoft)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                TextField("0", text: text)
                    .font(.kitaeMono(17, relativeTo: .body))
                    .keyboardType(keyboard)
                    .focused($focus, equals: value)
                if let unit {
                    Text(unit)
                        .font(.kitaeMono(13, relativeTo: .footnote))
                        .foregroundStyle(.inkSoft)
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .background(.card, in: .rect(cornerRadius: 18))
        .overlay {
            if focus == value {
                RoundedRectangle(cornerRadius: 18).strokeBorder(.olive, lineWidth: 2)
            }
        }
        .contentShape(.rect)
        .onTapGesture { focus = value }
        .accessibilityElement(children: .combine)
    }

    /// Steps for the field being typed in, just above the keyboard.
    @ViewBuilder
    private var keyboardSteps: some View {
        switch focus {
        case .weight?:
            ForEach(formatter.unit.steps, id: \.self) { step in
                Button(formatter.step(step)) { changeWeight(by: step) }
                    .accessibilityLabel("\(step < 0 ? "Minus" : "Plus") \(formatter.step(abs(step)).dropFirst()) \(formatter.unit.symbol)")
            }
        case .reps?:
            Button("−1") { changeReps(by: -1) }
                .accessibilityLabel(isIsometric ? "One second less" : "One rep less")
            Button("+1") { changeReps(by: 1) }
                .accessibilityLabel(isIsometric ? "One second more" : "One rep more")
        case nil:
            EmptyView()
        }
        Spacer()
        Button("Done") { focus = nil }
            .fontWeight(.semibold)
    }

    /// `-KitaeScreen plansettyping`: types in the weight.
    private func openDebugScreen() async {
        #if DEBUG
        guard DebugLaunch.screen == "plansettyping", DebugLaunch.consume("plansettyping-field") else { return }
        try? await Task.sleep(for: .milliseconds(600))
        focus = .weight
        #endif
    }

    private func changeReps(by step: Int) {
        editor.updateSet(target.id) { $0.reps = max($0.reps + step, 0) }
    }

    private func changeWeight(by step: Double) {
        let unit = formatter.unit
        editor.updateSet(target.id) { set in
            let shown = unit.fromKilograms(set.weightKg)
            set.weightKg = unit.toKilograms(max(shown + step, 0))
        }
    }
}
