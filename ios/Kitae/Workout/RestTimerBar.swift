// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The rest timer at the bottom of the workout: time left, pause, −15s, +15s and skip.
struct RestTimerBar: View {
    let workout: Workout

    var body: some View {
        HStack(spacing: 0) {
            RestCountdown(rest: workout.rest)
                .font(.kitaeMono(22, relativeTo: .title3, bold: true))
                .opacity(workout.rest.isPaused ? 0.55 : 1)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                if workout.rest.isPaused { workout.resumeRest() } else { workout.pauseRest() }
            } label: {
                Image(systemName: workout.rest.isPaused ? "play.fill" : "pause.fill")
                    .font(.system(size: 17))
                    .frame(width: 44, height: 44)
                    .contentShape(.rect)
            }
            .accessibilityLabel(workout.rest.isPaused ? "Resume rest" : "Pause rest")

            Button { workout.addRest(-15) } label: {
                Text("−15s").frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel("15 seconds less")

            Button { workout.addRest(15) } label: {
                Text("+15s").frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel("15 seconds more")

            Button { workout.skipRest() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(.rect)
            }
            .accessibilityLabel("Skip rest")
        }
        .font(.kitaeMono(14, relativeTo: .subheadline))
        .buttonStyle(.plain)
        .padding(.leading, 16)
        .padding(.trailing, 6)
        .frame(height: 58)
        .background(.mauveSoft, in: .capsule)
        .foregroundStyle(.onMauveSoft)
        // Its height is fixed, so its text stops growing at the first accessibility size.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }
}

/// The time left in a rest, counting down, or standing still while the rest is paused. It
/// keeps the room the rest's widest time needs, so the time is never cut short, whatever the
/// iPhone and text size, and doesn't move things about as it counts down.
struct RestCountdown: View {
    let rest: RestTimer

    var body: some View {
        Text(rest.widestClock)
            .hidden()
            .overlay(alignment: .leading) {
                time
                    .fixedSize()
            }
    }

    @ViewBuilder
    private var time: some View {
        if let endsAt = rest.endsAt {
            Text(timerInterval: min(Date.now, endsAt)...endsAt, countsDown: true)
        } else {
            let left = restLabel(seconds: Int(rest.remaining(at: .now).rounded(.up)))
            Text(left)
                .accessibilityLabel("Rest paused, \(left)")
        }
    }
}

/// Finish at the bottom of the workout, with the rest timer before it while a rest is going.
/// The rest timer gets the room its time needs, and Finish what's left up to its usual width,
/// down to just its checkmark. With less room than that, as with big text on a small iPhone,
/// the rest timer goes above Finish.
struct WorkoutBottomLayout: Layout {
    nonisolated static let spacing: CGFloat = 8
    /// Finish beside the rest timer, at its widest…
    nonisolated static let finishMaxWidth: CGFloat = 100
    /// …and its narrowest, as just its checkmark.
    nonisolated static let finishMinWidth: CGFloat = 58

    /// How wide Finish is beside a rest timer that needs `restWidth`, in a bar `width` wide,
    /// or nil when it has to go under the rest timer.
    nonisolated static func finishWidth(besideRest restWidth: CGFloat, in width: CGFloat) -> CGFloat? {
        let room = width - restWidth - spacing
        guard room >= finishMinWidth else { return nil }
        return min(room, finishMaxWidth)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width.flatMap { $0.isFinite ? $0 : nil }
        return arrange(subviews, width: width).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let arranged = arrange(subviews, width: bounds.width)
        for (subview, frame) in zip(subviews, arranged.frames) {
            subview.place(
                at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                proposal: ProposedViewSize(frame.size)
            )
        }
    }

    /// Where the rest timer and Finish go, or Finish alone across the whole width.
    private func arrange(_ subviews: Subviews, width proposed: CGFloat?) -> (frames: [CGRect], size: CGSize) {
        guard let finish = subviews.last else { return ([], .zero) }
        guard subviews.count == 2, let rest = subviews.first else {
            let width = proposed ?? finish.sizeThatFits(.unspecified).width
            let height = finish.sizeThatFits(ProposedViewSize(width: width, height: nil)).height
            return ([CGRect(x: 0, y: 0, width: width, height: height)], CGSize(width: width, height: height))
        }

        let restWidth = rest.sizeThatFits(.unspecified).width
        let width = proposed ?? restWidth + Self.spacing + Self.finishMaxWidth
        if let finishWidth = Self.finishWidth(besideRest: restWidth, in: width) {
            let barWidth = width - Self.spacing - finishWidth
            let height = max(
                rest.sizeThatFits(ProposedViewSize(width: barWidth, height: nil)).height,
                finish.sizeThatFits(ProposedViewSize(width: finishWidth, height: nil)).height
            )
            return (
                [
                    CGRect(x: 0, y: 0, width: barWidth, height: height),
                    CGRect(x: barWidth + Self.spacing, y: 0, width: finishWidth, height: height),
                ],
                CGSize(width: width, height: height)
            )
        }

        let restHeight = rest.sizeThatFits(ProposedViewSize(width: width, height: nil)).height
        let finishHeight = finish.sizeThatFits(ProposedViewSize(width: width, height: nil)).height
        return (
            [
                CGRect(x: 0, y: 0, width: width, height: restHeight),
                CGRect(x: 0, y: restHeight + Self.spacing, width: width, height: finishHeight),
            ],
            CGSize(width: width, height: restHeight + Self.spacing + finishHeight)
        )
    }
}
