// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

#if DEBUG
import SwiftUI

/// The rest timer's Lock Screen and Dynamic Island looks, drawn in the app for screenshots,
/// since the simulator can't be locked from a script. `activity` shows a rest counting down,
/// `activitystates` shows it paused and over.
struct RestActivityGallery: View {
    let page: String

    private let attributes = RestActivityAttributes(workoutID: 1, dayName: "Push A")

    private func state(paused: Bool = false) -> RestActivityAttributes.ContentState {
        RestActivityAttributes.ContentState(
            endsAt: paused ? nil : .now.addingTimeInterval(72),
            pausedRemaining: paused ? 72 : nil,
            total: 180,
            doneSets: 6,
            setCount: 15,
            nextExercise: "Shoulder Press",
            nextSet: "10 × 17.5 kg"
        )
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                if page == "activity" {
                    caption(".dynamic island")
                    compactIsland(state())
                    expandedIsland(state(), isStale: false)
                    caption(".lock screen")
                    lockScreen(state(), isStale: false)
                } else {
                    caption(".paused")
                    expandedIsland(state(paused: true), isStale: false)
                    lockScreen(state(paused: true), isStale: false)
                    caption(".rest over")
                    lockScreen(state(), isStale: true)
                }
            }
            .padding(.horizontal, 8)
            .padding(.top, 64)
        }
        .background {
            LinearGradient(
                colors: [Color(red: 0.42, green: 0.41, blue: 0.2), Color(red: 0.16, green: 0.16, blue: 0.12), Color(red: 0.42, green: 0.31, blue: 0.4)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
        }
    }

    private func caption(_ text: String) -> some View {
        Text(text)
            .font(.kitaeMono(12, relativeTo: .caption))
            .foregroundStyle(.white.opacity(0.75))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 12)
    }

    private func compactIsland(_ state: RestActivityAttributes.ContentState) -> some View {
        HStack {
            RestActivityIcon()
            Spacer()
            RestIslandCompactClock(state: state, isStale: false)
        }
        .padding(.leading, 12)
        .padding(.trailing, 16)
        .frame(width: 248, height: 37)
        .background(.black, in: .capsule)
    }

    private func expandedIsland(_ state: RestActivityAttributes.ContentState, isStale: Bool) -> some View {
        VStack(spacing: 12) {
            HStack {
                RestIslandStatus(state: state, isStale: isStale)
                Spacer()
                RestIslandDayName(name: attributes.dayName)
            }
            RestIslandBottom(state: state, isStale: isStale)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 18)
        .background(.black, in: .rect(cornerRadius: 44))
        .measured(limit: 160)
    }

    private func lockScreen(_ state: RestActivityAttributes.ContentState, isStale: Bool) -> some View {
        RestActivityLockScreen(attributes: attributes, state: state, isStale: isStale)
            .measured(limit: 160)
            .background(RestActivityStyle.panel, in: .rect(cornerRadius: 30))
    }
}

private extension View {
    /// Shows the height under the view, red above the limit iOS allows.
    func measured(limit: CGFloat) -> some View {
        overlay(alignment: .bottomTrailing) {
            GeometryReader { proxy in
                Text("\(Int(proxy.size.height.rounded())) pt")
                    .font(.kitaeMono(11, relativeTo: .caption2))
                    .foregroundStyle(proxy.size.height > limit ? Color.red : Color.white.opacity(0.6))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .offset(x: -12, y: 16)
            }
        }
    }
}
#endif
