// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Kitae's small Space Mono captions, like ".up next" and ".reps".
struct Caption: View {
    let text: String
    var size: CGFloat = 13

    init(_ text: String, size: CGFloat = 13) {
        self.text = text
        self.size = size
    }

    var body: some View {
        Text(text)
            .font(.kitaeMono(size, relativeTo: .footnote))
            .foregroundStyle(.inkSoft)
    }
}

/// A training day you can pick, in a row of days.
struct DayChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.kitaeMono(14, relativeTo: .subheadline, bold: isSelected))
                .padding(.horizontal, 16)
                .frame(minHeight: 44)
                .background(isSelected ? Color.oliveSoft : Color.field, in: .capsule)
                .foregroundStyle(isSelected ? Color.onOliveSoft : Color.ink)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Kitae's big mauve buttons: Start, Continue and Finish.
struct MauveButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.kitaeMono(17, relativeTo: .headline, bold: true))
            .foregroundStyle(.onMauve)
            .background(.mauve, in: .capsule)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.snappy(duration: 0.2), value: configuration.isPressed)
    }
}

/// A thin separator in Kitae's colours.
struct Hairline: View {
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        Rectangle()
            .fill(.rule)
            .frame(height: 1 / displayScale)
    }
}
