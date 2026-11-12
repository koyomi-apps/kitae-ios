// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// Muscles worked on the muscle map: the ones with the most sets in olive, the others worked in
/// a lighter olive, each with its sets. You shows the last 7 days', a workout's summary its own.
struct MuscleVolumeCard: View {
    /// Beside the title, like "Last 7 days".
    let subtitle: String
    let volume: MuscleVolume
    let formatter: WeightFormatter
    /// What to say when no sets count.
    var empty = "No sets yet."

    var body: some View {
        let shading = volume.shading
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Muscles")
                    .font(.kitaeDisplay(24, relativeTo: .title3))
                    .foregroundStyle(.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
            }
            HStack(alignment: .center, spacing: 14) {
                if let map = ExerciseMuscles.map {
                    HStack(spacing: 4) {
                        MuscleFigure(map: map, side: .front, work: shading)
                        MuscleFigure(map: map, side: .back, work: shading)
                    }
                    .frame(height: 172)
                }
                VStack(alignment: .leading, spacing: 14) {
                    if volume.totalSets == 0 {
                        Text(empty)
                            .font(.callout)
                            .foregroundStyle(.inkSoft)
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        group(".most sets", shading.primary, color: .olive)
                        group(".fewer sets", shading.secondary, color: .muscleMapSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if volume.totalSets > 0 {
                Text("\(counted(volume.totalSets, "set", "sets")). A set counts once for the muscles an exercise works most, and half for the ones that help.")
                    .font(.footnote)
                    .foregroundStyle(.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.card, in: .rect(cornerRadius: 24))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
    }

    @ViewBuilder
    private func group(_ caption: String, _ muscles: [Muscle], color: Color) -> some View {
        if !muscles.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(color)
                        .frame(width: 10, height: 10)
                    Text(caption)
                        .font(.kitaeMono(11, relativeTo: .caption2))
                        .foregroundStyle(.inkSoft)
                }
                Text(list(muscles))
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// "Chest 12, triceps 9, front delts 7.5".
    private func list(_ muscles: [Muscle]) -> String {
        muscles.enumerated()
            .map { index, muscle in
                let name = index == 0 ? muscle.name : muscle.name.lowercased()
                return "\(name) \(formatter.tenthsOrWhole(volume.sets[muscle] ?? 0))"
            }
            .joined(separator: ", ")
    }

    private var spoken: String {
        guard volume.totalSets > 0 else { return "Muscles: \(empty)" }
        let shading = volume.shading
        var parts = ["Muscles worked, \(subtitle.lowercased()), in sets. Most: \(list(shading.primary))"]
        if !shading.secondary.isEmpty {
            parts.append("Fewer: \(list(shading.secondary))")
        }
        return parts.joined(separator: ". ")
    }
}
