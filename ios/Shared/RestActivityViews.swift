// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import AppIntents
import SwiftUI

// The rest timer as the Lock Screen and the Dynamic Island draw it, from the design canvas.
// It sits on a dark background whatever the phone's appearance, so Kitae's colours are
// used in their dark versions. Sizes are fixed, and tighter than the mockup: iOS cuts off a
// Lock Screen Live Activity taller than 160 points.

enum RestActivityStyle {
    /// The Lock Screen panel: Kitae's darkest ink, see-through.
    static let panel = Color(red: 24 / 255, green: 24 / 255, blue: 16 / 255).opacity(0.62)
    static let skip = Color(red: 233 / 255, green: 207 / 255, blue: 226 / 255)

    static func mono(_ size: CGFloat, bold: Bool = false) -> Font {
        .custom(bold ? KitaeFonts.monoBold : KitaeFonts.monoRegular, fixedSize: size)
    }
}

extension RestActivityAttributes.ContentState {
    func title(isStale: Bool) -> String {
        if isStale { return "Rest over" }
        return isPaused ? "Paused" : "Resting"
    }
}

extension View {
    /// Space Mono's lines are much taller than its digits; this keeps a line to `height`.
    fileprivate func monoLine(_ height: CGFloat) -> some View {
        frame(height: height)
    }
}

/// The whole rest timer on the Lock Screen.
struct RestActivityLockScreen: View {
    let attributes: RestActivityAttributes
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                RestActivityIcon(isTile: true)
                Text(state.title(isStale: isStale))
                    .font(.system(size: 15, weight: .semibold))
                Text(attributes.dayName)
                    .font(.system(size: 15))
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
                Spacer(minLength: 0)
                Text("\(state.doneSets)/\(state.setCount) sets")
                    .font(RestActivityStyle.mono(12))
                    .foregroundStyle(.white.opacity(0.62))
            }
            .frame(height: 24)

            HStack(alignment: .center, spacing: 12) {
                RestActivityClock(state: state, isStale: isStale, size: 56)
                    .frame(maxWidth: .infinity, alignment: .leading)
                RestActivityUpNext(state: state)
            }

            if isStale {
                Text("Time for your next set")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.75))
            } else {
                RestActivityProgress(state: state)
                RestActivityButtons(state: state, height: 34, fill: 0.14)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .foregroundStyle(.white)
        .environment(\.colorScheme, .dark)
    }
}

/// Kitae's dumbbell, on an olive tile on the Lock Screen and bare in the Dynamic Island.
struct RestActivityIcon: View {
    var isTile = false

    var body: some View {
        Image(systemName: "dumbbell.fill")
            .font(.system(size: isTile ? 11 : 14, weight: .bold))
            .foregroundStyle(.olive)
            .frame(width: isTile ? 24 : nil, height: isTile ? 24 : nil)
            .background {
                if isTile {
                    RoundedRectangle(cornerRadius: 7).fill(.oliveSoft)
                }
            }
            .environment(\.colorScheme, .dark)
            .accessibilityHidden(true)
    }
}

/// The time left in big digits.
struct RestActivityClock: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool
    let size: CGFloat

    var body: some View {
        RestActivityClockText(state: state, isStale: isStale)
            .font(RestActivityStyle.mono(size, bold: true))
            .tracking(-2)
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .foregroundStyle(.olive)
            .monoLine(size * 0.8)
            .environment(\.colorScheme, .dark)
    }
}

/// The next set to do, on the right of the clock.
struct RestActivityUpNext: View {
    let state: RestActivityAttributes.ContentState

    var body: some View {
        if let exercise = state.nextExercise {
            VStack(alignment: .trailing, spacing: 0) {
                Text(".up next")
                    .font(RestActivityStyle.mono(11))
                    .foregroundStyle(.white.opacity(0.58))
                    .monoLine(14)
                Text(exercise)
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(1)
                if let set = state.nextSet {
                    Text(set)
                        .font(RestActivityStyle.mono(13))
                        .foregroundStyle(.white.opacity(0.75))
                        .lineLimit(1)
                        .monoLine(17)
                }
            }
            .frame(maxWidth: 190, alignment: .trailing)
            .accessibilityElement(children: .combine)
        }
    }
}

/// How much of the rest is left, as a bar.
struct RestActivityProgress: View {
    let state: RestActivityAttributes.ContentState

    var body: some View {
        Group {
            if let endsAt = state.endsAt, let startedAt = state.startedAt {
                ProgressView(timerInterval: startedAt...endsAt, countsDown: true) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
            } else {
                ProgressView(value: state.pausedRemaining ?? 0, total: max(state.total, 1))
            }
        }
        .progressViewStyle(.linear)
        .tint(.mauveSoft)
        .labelsHidden()
        .environment(\.colorScheme, .dark)
        .accessibilityHidden(true)
    }
}

/// Pause or resume, −15s, +15s and Skip.
struct RestActivityButtons: View {
    let state: RestActivityAttributes.ContentState
    let height: CGFloat
    /// How strongly the buttons show against the background.
    let fill: Double

    var body: some View {
        HStack(spacing: 8) {
            if state.isPaused {
                Button(intent: ResumeRestIntent()) {
                    pill { Image(systemName: "play.fill").font(.system(size: 15, weight: .bold)) }
                }
                .accessibilityLabel("Resume rest")
            } else {
                Button(intent: PauseRestIntent()) {
                    pill { Image(systemName: "pause.fill").font(.system(size: 15, weight: .bold)) }
                }
                .accessibilityLabel("Pause rest")
            }
            Button(intent: RemoveRestTimeIntent()) {
                pill { Text("−\(restAdjustSeconds)s") }
            }
            .accessibilityLabel("\(restAdjustSeconds) seconds less")
            Button(intent: AddRestTimeIntent()) {
                pill { Text("+\(restAdjustSeconds)s") }
            }
            .accessibilityLabel("\(restAdjustSeconds) seconds more")
            Button(intent: SkipRestIntent()) {
                pill {
                    Text("Skip")
                        .font(RestActivityStyle.mono(15, bold: true))
                        .foregroundStyle(RestActivityStyle.skip)
                }
            }
            .accessibilityLabel("Skip rest")
        }
        .buttonStyle(.plain)
        .font(RestActivityStyle.mono(15))
        .foregroundStyle(.white)
    }

    private func pill(@ViewBuilder _ label: () -> some View) -> some View {
        label()
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(.white.opacity(fill), in: .capsule)
            .contentShape(.capsule)
    }
}

// MARK: Dynamic Island

/// Held down, top left: the dumbbell and Resting, Paused or Rest over.
struct RestIslandStatus: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        HStack(spacing: 8) {
            RestActivityIcon()
            Text(state.title(isStale: isStale))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
        }
        .padding(.leading, 6)
        .environment(\.colorScheme, .dark)
    }
}

/// Held down, top right: the day's name.
struct RestIslandDayName: View {
    let name: String

    var body: some View {
        Text(name)
            .font(.system(size: 15))
            .foregroundStyle(.white.opacity(0.62))
            .lineLimit(1)
            .padding(.trailing, 6)
    }
}

/// Held down, below the camera: the clock, what's next, and the buttons.
struct RestIslandBottom: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                RestActivityClock(state: state, isStale: isStale, size: 52)
                    .frame(maxWidth: .infinity, alignment: .leading)
                RestActivityUpNext(state: state)
            }
            if isStale {
                Text("Time for your next set")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.75))
            } else {
                RestActivityButtons(state: state, height: 36, fill: 0.16)
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 6)
        .environment(\.colorScheme, .dark)
    }
}

/// Beside the camera: the time left.
struct RestIslandCompactClock: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        RestActivityClockText(state: state, isStale: isStale)
            .font(RestActivityStyle.mono(15, bold: true))
            .monospacedDigit()
            .multilineTextAlignment(.trailing)
            .foregroundStyle(.olive)
            .frame(width: 46, alignment: .trailing)
            .environment(\.colorScheme, .dark)
    }
}

/// When another app's activity shares the island: a ring of the time left around the dumbbell.
struct RestIslandMinimal: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        if !isStale, let endsAt = state.endsAt, let startedAt = state.startedAt {
            ProgressView(timerInterval: startedAt...endsAt, countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                RestActivityIcon()
            }
            .progressViewStyle(.circular)
            .tint(.olive)
            .environment(\.colorScheme, .dark)
        } else {
            RestActivityIcon()
        }
    }
}

/// The clock's text alone: counting down, stopped while paused, or 0:00 once the rest is over.
private struct RestActivityClockText: View {
    let state: RestActivityAttributes.ContentState
    let isStale: Bool

    var body: some View {
        if !isStale, let endsAt = state.endsAt, let startedAt = state.startedAt {
            Text(timerInterval: startedAt...endsAt, countsDown: true)
        } else if !isStale, let left = state.pausedRemaining {
            Text(restClock(left))
        } else {
            Text(restClock(0))
        }
    }
}
