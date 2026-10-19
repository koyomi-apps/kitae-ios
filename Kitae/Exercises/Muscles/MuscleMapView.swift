// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The muscle map on an exercise's page, under its animation: a body from the front and from
/// the back with the muscles the exercise works most in olive and the ones that help in a
/// lighter olive, and their names beside it.
struct MuscleMapView: View {
    let work: MuscleWork

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            if let map = ExerciseMuscles.map {
                HStack(spacing: 4) {
                    MuscleFigure(map: map, side: .front, work: work)
                    MuscleFigure(map: map, side: .back, work: work)
                }
                .frame(height: 172)
            }
            VStack(alignment: .leading, spacing: 14) {
                group(".primary", work.primary, color: .olive)
                group(".secondary", work.secondary, color: .muscleMapSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.leading, 12)
        .padding(.trailing, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.card, in: .rect(cornerRadius: 24))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(work.spoken)
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
                Text(MuscleWork.list(muscles))
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// One side of the body, its muscles shaded: `work`'s primary ones in olive and its secondary
/// ones lighter. Exercise pages and the You tab draw it.
struct MuscleFigure: View {
    let map: MuscleMap
    let side: MuscleMap.Side
    let work: MuscleWork

    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / map.width, size.height / map.height)
            context.translateBy(x: (size.width - map.width * scale) / 2, y: (size.height - map.height * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            let outline = MuscleMap.path(map.body)
            context.fill(outline, with: .color(.muscleMapBase))
            var inside = context
            inside.clip(to: outline)
            for (muscle, shapes) in map.muscles(side) {
                let path = MuscleMap.path(shapes)
                let color: Color = work.primary.contains(muscle) ? .olive
                    : work.secondary.contains(muscle) ? .muscleMapSecondary : .muscleMapIdle
                inside.fill(path, with: .color(color))
                // A gap round each muscle, in the body's colour.
                inside.stroke(path, with: .color(.muscleMapBase), style: StrokeStyle(lineWidth: 1.1, lineJoin: .round))
            }
        }
        .aspectRatio(map.width / map.height, contentMode: .fit)
        .accessibilityHidden(true)
    }
}
