// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The set types to choose from, with the set's own ticked. Used for workout sets and
/// planned sets alike.
struct SetTypeSection: View {
    let selected: SetType?
    let onSelect: (SetType) -> Void

    var body: some View {
        Section {
            ForEach(SetType.allCases) { type in
                Button {
                    onSelect(type)
                } label: {
                    HStack(spacing: 14) {
                        Text(type.letter ?? "01")
                            .font(.kitaeMono(15, relativeTo: .body, bold: true))
                            .foregroundStyle(.inkSoft)
                            .frame(width: 30, alignment: .leading)
                        Text(type.name)
                            .foregroundStyle(.ink)
                        Spacer()
                        if selected == type {
                            Image(systemName: "checkmark")
                                .fontWeight(.semibold)
                                .foregroundStyle(.olive)
                        }
                    }
                    .contentShape(.rect)
                }
                .accessibilityAddTraits(selected == type ? .isSelected : [])
                .listRowBackground(Color.card)
            }
        } header: {
            SectionCaption(".set type")
        }
    }
}

/// How long to rest after a set: the exercise's usual rest, or a length of its own.
struct RestSection: View {
    @Binding var seconds: Int?
    /// The rest used when the set has none of its own.
    let usualSeconds: Int

    static let choices = [30, 45, 60, 75, 90, 120, 150, 180, 240, 300]

    var body: some View {
        Section {
            Picker("Rest after this set", selection: $seconds) {
                Text("Default, \(restSetting(seconds: usualSeconds))").tag(Int?.none)
                ForEach(choices, id: \.self) { choice in
                    Text(restLabel(seconds: choice)).tag(Int?.some(choice))
                }
            }
            .listRowBackground(Color.card)
        }
    }

    /// The usual lengths, plus the set's own if it's another one, like a rest set on Android.
    private var choices: [Int] {
        guard let seconds, !Self.choices.contains(seconds) else { return Self.choices }
        return (Self.choices + [seconds]).sorted()
    }
}

/// A list section's header in Kitae's style, like ".set type".
struct SectionCaption: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .font(.kitaeMono(12, relativeTo: .caption))
            .foregroundStyle(.inkSoft)
            .textCase(nil)
    }
}
