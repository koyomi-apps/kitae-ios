// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// An exercise's muscles in a line under its name on the workout screen: a swatch in olive and the
/// muscles it works most, then a lighter one and the muscles that help, as on its muscle map. The
/// two go one under the other when they don't fit side by side.
struct MuscleLine: View {
    let work: MuscleWork

    @ScaledMetric(relativeTo: .footnote) private var swatch = 9.0

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) { groups }
            VStack(alignment: .leading, spacing: 2) { groups }
        }
        .font(.footnote)
        .foregroundStyle(.inkSoft)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(work.spoken)
    }

    @ViewBuilder
    private var groups: some View {
        group(work.primary, color: .olive)
        group(work.secondary, color: .muscleMapSecondary)
    }

    @ViewBuilder
    private func group(_ muscles: [Muscle], color: Color) -> some View {
        if !muscles.isEmpty {
            // The swatch stands on the text's baseline, about as tall as its capitals.
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                RoundedRectangle(cornerRadius: swatch * 0.28)
                    .fill(color)
                    .frame(width: swatch, height: swatch)
                Text(MuscleWork.list(muscles))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
