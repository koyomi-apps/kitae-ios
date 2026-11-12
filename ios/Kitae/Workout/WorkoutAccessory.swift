// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import SwiftUI

/// The bar above the tabs while a workout is minimized. Tapping it opens the workout again.
struct WorkoutAccessory: View {
    @Environment(AppModel.self) private var model
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        if let workout = model.workout {
            Button {
                model.isWorkoutPresented = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "dumbbell.fill")
                        .foregroundStyle(.olive)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(workout.dayName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.ink)
                            .lineLimit(1)
                        if placement != .inline {
                            HStack(spacing: 0) {
                                Text(timerInterval: workout.startedAt...Date.distantFuture, countsDown: false)
                                Text(" · \(workout.doneCount)/\(workout.setCount) sets")
                            }
                            .font(.kitaeMono(12, relativeTo: .caption))
                            .foregroundStyle(.inkSoft)
                            .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 8)
                    if workout.rest.isRunning {
                        // The rest's time is never squeezed: the day's name gives way instead.
                        HStack(spacing: 6) {
                            Image(systemName: "timer")
                            RestCountdown(rest: workout.rest)
                        }
                        .font(.kitaeMono(14, relativeTo: .subheadline, bold: true))
                        .padding(.horizontal, 12)
                        .frame(height: 34)
                        .background(.mauveSoft, in: .capsule)
                        .foregroundStyle(.onMauveSoft)
                        .fixedSize()
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    }
                }
                .padding(.horizontal, 16)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(workout.dayName) in progress, \(workout.doneCount) of \(workout.setCount) sets done")
            .accessibilityHint("Opens the workout")
        }
    }
}

extension View {
    /// Shows the workout bar above the tabs only while there's a workout.
    /// iOS 26.0 and 26.1 can't hide an accessory, so there it's left out instead.
    @ViewBuilder
    func workoutAccessory<Content: View>(_ isEnabled: Bool, @ViewBuilder content: () -> Content) -> some View {
        if #available(iOS 26.2, *) {
            tabViewBottomAccessory(isEnabled: isEnabled, content: content)
        } else if isEnabled {
            tabViewBottomAccessory(content: content)
        } else {
            self
        }
    }
}
