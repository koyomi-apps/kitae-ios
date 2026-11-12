// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Which field has the keyboard: a set's reps or its weight.
enum SetField: Hashable {
    case reps(WorkoutSet.ID)
    case weight(WorkoutSet.ID)
}

/// One set in the workout: its label, reps and weight you can type into, and the done button.
/// A held exercise counts seconds instead of reps, and one that doesn't use weight has no
/// weight to type.
struct SetRow: View {
    let set: WorkoutSet
    let label: String
    let isIsometric: Bool
    let usesWeight: Bool
    let formatter: WeightFormatter
    var focus: FocusState<SetField?>.Binding
    let onReps: (Int) -> Void
    let onWeight: (Double) -> Void
    let onToggleDone: () -> Void
    let onOptions: () -> Void

    @State private var repsText = ""
    @State private var weightText = ""

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onOptions) {
                Text(label)
                    .font(.kitaeMono(26, relativeTo: .title2, bold: true))
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .foregroundStyle(.inkSoft)
                    .frame(width: 56, height: 56)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Set \(label) options")

            field(isIsometric ? ".seconds" : ".reps", text: $repsText, keyboard: .numberPad, value: .reps(set.id))
                .frame(width: usesWeight ? 100 : nil)
            if usesWeight {
                field(".weight", text: $weightText, unit: formatter.unit.symbol, keyboard: .decimalPad, value: .weight(set.id))
            }

            Button(action: onToggleDone) {
                Image(systemName: "checkmark")
                    .font(.system(size: 17, weight: set.isDone ? .bold : .semibold))
                    .frame(width: 44, height: 44)
                    .background(set.isDone ? Color.olive : Color.field, in: .circle)
                    .foregroundStyle(set.isDone ? Color.onOlive : Color.inkSoft)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(set.isDone ? "Set \(label) done" : "Mark set \(label) done")
            .accessibilityAddTraits(set.isDone ? .isSelected : [])
        }
        .frame(minHeight: 60)
        .onAppear {
            repsText = "\(set.reps)"
            weightText = formatter.string(kilograms: set.weightKg)
        }
        .onChange(of: set.reps) { _, reps in
            if Int(repsText) != reps { repsText = "\(reps)" }
        }
        .onChange(of: set.weightKg) { _, kg in
            if let typed = formatter.kilograms(from: weightText), abs(typed - kg) < 0.0001 { return }
            weightText = formatter.string(kilograms: kg)
        }
        .onChange(of: repsText) { _, text in
            if let reps = Int(text), reps >= 0, reps != set.reps { onReps(reps) }
        }
        .onChange(of: weightText) { _, text in
            if let kg = formatter.kilograms(from: text), abs(kg - set.weightKg) >= 0.0001 { onWeight(kg) }
        }
        .onChange(of: focus.wrappedValue) { old, _ in
            // Tidy what was typed once the field loses the keyboard.
            if old == .reps(set.id), Int(repsText) == nil { repsText = "\(set.reps)" }
            if old == .weight(set.id) { weightText = formatter.string(kilograms: set.weightKg) }
        }
    }

    private func field(_ caption: String, text: Binding<String>, unit: String? = nil, keyboard: UIKeyboardType, value: SetField) -> some View {
        let isFocused = focus.wrappedValue == value
        return VStack(alignment: .leading, spacing: 0) {
            Text(caption)
                .font(.kitaeMono(10, relativeTo: .caption2))
                .foregroundStyle(set.isDone ? Color.onOliveSoft : Color.inkSoft)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                TextField("0", text: text)
                    .font(.kitaeMono(17, relativeTo: .body))
                    .keyboardType(keyboard)
                    .focused(focus, equals: value)
                    .fixedSize(horizontal: true, vertical: false)
                if let unit {
                    Text(unit)
                        .font(.kitaeMono(13, relativeTo: .footnote))
                        .foregroundStyle(set.isDone ? Color.onOliveSoft : Color.inkSoft)
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .background(set.isDone ? Color.oliveSoft : Color.field, in: .rect(cornerRadius: 18))
        .foregroundStyle(set.isDone ? Color.onOliveSoft : Color.ink)
        .overlay {
            if isFocused {
                RoundedRectangle(cornerRadius: 18).strokeBorder(.olive, lineWidth: 2)
            }
        }
        .contentShape(.rect)
        .onTapGesture { focus.wrappedValue = value }
        .accessibilityElement(children: .combine)
    }
}
