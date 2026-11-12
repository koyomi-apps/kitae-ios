// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// The rest between sets. It counts down to a moment rather than ticking, so it stays
/// right while the app is in the background.
nonisolated struct RestTimer: Codable, Equatable, Sendable {
    /// When the rest ends, while it's counting down.
    private(set) var endsAt: Date?
    /// The time left, while it's paused.
    private(set) var pausedRemaining: TimeInterval?
    /// How long this rest is in total, for the progress bar.
    private(set) var total: TimeInterval = 0

    var isRunning: Bool { endsAt != nil }
    var isPaused: Bool { pausedRemaining != nil }
    var isActive: Bool { isRunning || isPaused }

    func remaining(at now: Date) -> TimeInterval {
        if let endsAt {
            return max(endsAt.timeIntervalSince(now), 0)
        }
        return pausedRemaining ?? 0
    }

    mutating func start(seconds: Int, at now: Date) {
        total = TimeInterval(max(seconds, 0))
        endsAt = now.addingTimeInterval(total)
        pausedRemaining = nil
    }

    mutating func pause(at now: Date) {
        guard let endsAt else { return }
        pausedRemaining = max(endsAt.timeIntervalSince(now), 0)
        self.endsAt = nil
    }

    mutating func resume(at now: Date) {
        guard let pausedRemaining else { return }
        endsAt = now.addingTimeInterval(pausedRemaining)
        self.pausedRemaining = nil
    }

    /// Adds or removes time. Taking away more than is left ends the rest.
    mutating func add(_ seconds: TimeInterval, at now: Date) {
        guard isActive else { return }
        let left = max(remaining(at: now) + seconds, 0)
        if left == 0 {
            stop()
            return
        }
        total = max(total + seconds, left)
        if isRunning {
            endsAt = now.addingTimeInterval(left)
        } else {
            pausedRemaining = left
        }
    }

    mutating func stop() {
        endsAt = nil
        pausedRemaining = nil
        total = 0
    }
}

nonisolated extension RestTimer {
    /// The countdown at its widest, as it starts: "0:00", "00:00" from ten minutes, or
    /// "0:00:00" from an hour. The time left keeps this much room, so it's never cut short.
    var widestClock: String {
        let seconds = Int(total.rounded(.up))
        if seconds >= 3600 { return "0:00:00" }
        if seconds >= 600 { return "00:00" }
        return "0:00"
    }
}
