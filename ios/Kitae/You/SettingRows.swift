// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI
import UIKit

/// Kilograms or pounds, and how decimals are written, as rows of a list: in Settings and in
/// the welcome.
struct WeightRows: View {
    @Binding var settings: AppSettings

    var body: some View {
        ChoiceRow(
            title: "Units",
            selection: $settings.unit,
            choices: WeightUnit.allCases,
            name: \.symbol,
            spokenName: { $0.spokenName }
        )
        .listRowBackground(Color.card)

        ChoiceRow(
            title: "Decimals",
            selection: $settings.decimals,
            choices: DecimalStyle.allCases,
            name: \.title,
            spokenName: { $0.spokenName }
        )
        .listRowBackground(Color.card)
    }
}

/// The rest after a set without one of its own, as a row of a list: in Settings and in the
/// welcome.
struct DefaultRestRow: View {
    @Binding var seconds: Int

    var body: some View {
        MenuRow(
            title: "Default rest",
            selection: $seconds,
            choices: Self.choices(for: seconds),
            name: { $0 > 0 ? restLabel(seconds: $0) : "Off" },
            spokenName: Self.spokenRest,
            font: .kitaeMono(15, relativeTo: .body)
        )
        .listRowBackground(Color.card)
    }

    /// Off, and the rest lengths the set and exercise pickers offer, with `seconds` among
    /// them when it's another, from a backup.
    static func choices(for seconds: Int) -> [Int] {
        let choices = [0] + RestSection.choices
        return choices.contains(seconds) ? choices : (choices + [seconds]).sorted()
    }

    /// "1 minute, 30 seconds".
    private nonisolated static func spokenRest(_ seconds: Int) -> String {
        guard seconds > 0 else { return "Off" }
        return Duration.seconds(seconds).formatted(.units(allowed: [.minutes, .seconds], width: .wide))
    }
}

/// How big the text is, and light or dark, as rows of a list: in Settings and in the welcome.
struct AppearanceRows: View {
    @Binding var settings: AppSettings

    var body: some View {
        TextSizeRows(textSize: $settings.textSize)

        ChoiceRow(title: "Theme", selection: $settings.theme, choices: AppTheme.allCases, name: \.name)
            .listRowBackground(Color.card)
    }
}

/// A setting with a few choices: its title, and the choices beside it, or under it when the
/// text is too big for both on one line.
struct ChoiceRow<Value: Hashable>: View {
    let title: String
    @Binding var selection: Value
    let choices: [Value]
    let name: (Value) -> String
    var spokenName: ((Value) -> String)?

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                label
                Spacer(minLength: 0)
                bar
                    .fixedSize()
            }
            VStack(alignment: .leading, spacing: 10) {
                label
                bar
            }
            .padding(.vertical, 8)
        }
    }

    private var label: some View {
        Text(title)
            // The choices say it.
            .accessibilityHidden(true)
    }

    private var bar: some View {
        ChoiceBar(title: title, selection: $selection, choices: choices, name: name, spokenName: spokenName)
    }
}

/// Kitae's text size, as two rows of a list: whether it's the iPhone's, and a slider from
/// small to large. Moving the slider gives Kitae a size of its own.
struct TextSizeRows: View {
    @Binding var textSize: TextSize
    /// Read so the slider follows when the iPhone's text size changes.
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Toggle("Use iPhone text size", isOn: followsIPhone)
            .listRowBackground(Color.card)

        HStack(spacing: 16) {
            // Always the same sizes, so the slider stays put while the text changes size.
            Text("A")
                .font(.system(size: 13, weight: .semibold))
            Slider(value: sizeIndex, in: 0...Double(TextSize.sizes.count - 1), step: 1) {
                Text("Text size")
            }
            .accessibilityValue(shown.name)
            Text("A")
                .font(.system(size: 24, weight: .semibold))
        }
        .foregroundStyle(.inkSoft)
        .listRowBackground(Color.card)
    }

    /// The size the slider shows: the one picked, or the nearest to the iPhone's.
    private var shown: TextSize {
        guard textSize == .iPhone else { return textSize }
        return TextSize(nearest: UIApplication.shared.preferredContentSizeCategory)
    }

    private var followsIPhone: Binding<Bool> {
        Binding(
            get: { textSize == .iPhone },
            // Turned off, the text keeps the size it has.
            set: { textSize = $0 ? .iPhone : shown }
        )
    }

    private var sizeIndex: Binding<Double> {
        Binding(
            get: { Double(TextSize.sizes.firstIndex(of: shown) ?? 0) },
            set: { index in
                let step = min(max(Int(index.rounded()), 0), TextSize.sizes.count - 1)
                textSize = TextSize.sizes[step]
            }
        )
    }
}

extension TextSize {
    /// iOS's name for the size, or nil for the iPhone's own.
    var contentSizeCategory: UIContentSizeCategory? {
        switch self {
        case .iPhone: nil
        case .extraSmall: .extraSmall
        case .small: .small
        case .medium: .medium
        case .standard: .large
        case .large: .extraLarge
        case .extraLarge: .extraExtraLarge
        case .largest: .extraExtraExtraLarge
        }
    }

    /// The size to pick nearest to one of iOS's. Its accessibility sizes are bigger than
    /// any, so they're the largest.
    init(nearest category: UIContentSizeCategory) {
        if category.isAccessibilityCategory {
            self = .largest
        } else {
            self = Self.sizes.first { $0.contentSizeCategory == category } ?? .standard
        }
    }
}

/// A setting picked from a menu, with its value and ⌃⌄ on the right, like "Default rest 1:30".
struct MenuRow<Value: Hashable>: View {
    let title: String
    @Binding var selection: Value
    let choices: [Value]
    let name: (Value) -> String
    var spokenName: ((Value) -> String)?
    var font: Font = .body
    /// A second line for a choice in the menu.
    var detail: ((Value) -> String?)?

    var body: some View {
        LabeledContent(title) {
            Menu {
                Picker(title, selection: $selection) {
                    ForEach(choices, id: \.self) { choice in
                        if let detail = detail?(choice) {
                            VStack {
                                Text(name(choice))
                                Text(detail)
                            }
                            .tag(choice)
                        } else {
                            Text(name(choice)).tag(choice)
                        }
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Text(name(selection))
                        .font(font)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.footnote.weight(.semibold))
                }
                .foregroundStyle(.inkSoft)
                .frame(minHeight: 44)
                .contentShape(.rect)
            }
            .accessibilityLabel(title)
            .accessibilityValue((spokenName ?? name)(selection))
        }
    }
}
