// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import ActivityKit
import Foundation

/// The rest timer on the Lock Screen and in the Dynamic Island. It's there while a rest is
/// counting down or paused, like Kitae for Android's "Resting" notification.
nonisolated struct RestActivityAttributes: ActivityAttributes {
    nonisolated struct ContentState: Codable, Hashable, Sendable {
        /// When the rest ends, while it's counting down.
        var endsAt: Date?
        /// The time left, while it's paused.
        var pausedRemaining: TimeInterval?
        /// How long the rest is in total, for the progress bar.
        var total: TimeInterval
        var doneSets: Int
        var setCount: Int
        /// The next set to do: its exercise, and reps and weight like "10 × 17.5 kg".
        var nextExercise: String?
        var nextSet: String?

        var isPaused: Bool { pausedRemaining != nil }

        /// When the rest would have started, given how long it is, for the progress bar.
        var startedAt: Date? { endsAt.map { $0.addingTimeInterval(-max(total, 1)) } }
    }

    /// The workout's id, so Kitae can tell its own Live Activity apart.
    var workoutID: Int64
    var dayName: String
}

/// Time as a countdown shows it, 1:12, rounding up like Kitae for Android.
nonisolated func restClock(_ interval: TimeInterval) -> String {
    let seconds = Int(max(interval, 0).rounded(.up))
    return "\(seconds / 60):\(seconds % 60 < 10 ? "0" : "")\(seconds % 60)"
}
