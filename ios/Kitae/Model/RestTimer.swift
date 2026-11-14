// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// The rest between sets. It counts down to a moment rather than ticking, so it stays
/// right while the app is in the background. The shared module counts it (`RestTimer.kt`, in
/// milliseconds); this keeps its times as the iPhone saves them.
nonisolated struct RestTimer: Codable, Equatable, Sendable {
    /// When the rest ends, while it's counting down.
    private(set) var endsAt: Date?
    /// The time left, while it's paused.
    private(set) var pausedRemaining: TimeInterval?
    /// How long this rest is in total, for the progress bar.
    private(set) var total: TimeInterval = 0

    var isRunning: Bool { core.isRunning }
    var isPaused: Bool { core.isPaused }
    var isActive: Bool { core.isActive }

    func remaining(at now: Date) -> TimeInterval {
        TimeInterval(core.remaining(now: now.epochMilliseconds)) / 1000
    }

    mutating func start(seconds: Int, at now: Date) {
        self = RestTimer(core.started(seconds: Int32(clamping: seconds), now: now.epochMilliseconds))
    }

    mutating func pause(at now: Date) {
        self = RestTimer(core.paused(now: now.epochMilliseconds))
    }

    mutating func resume(at now: Date) {
        self = RestTimer(core.resumed(now: now.epochMilliseconds))
    }

    /// Adds or removes time. Taking away more than is left ends the rest.
    mutating func add(_ seconds: TimeInterval, at now: Date) {
        self = RestTimer(core.adding(seconds: Int32(clamping: Int(seconds.rounded())), now: now.epochMilliseconds))
    }

    mutating func stop() {
        self = RestTimer()
    }
}

nonisolated extension RestTimer {
    /// The countdown at its widest, as it starts: "0:00", "00:00" from ten minutes, or
    /// "0:00:00" from an hour. The time left keeps this much room, so it's never cut short.
    var widestClock: String { core.widestClock }

    /// The shared module's own.
    private var core: KitaeCore.RestTimer {
        KitaeCore.RestTimer(
            endsAt: endsAt?.epochMilliseconds.kotlin,
            pausedRemaining: pausedRemaining.map { Int64(($0 * 1000).rounded()).kotlin },
            total: Int64((total * 1000).rounded())
        )
    }

    private init(_ timer: KitaeCore.RestTimer) {
        endsAt = timer.endsAt.map { Date(epochMilliseconds: $0.int64Value) }
        pausedRemaining = timer.pausedRemaining.map { TimeInterval($0.int64Value) / 1000 }
        total = TimeInterval(timer.total) / 1000
    }
}
