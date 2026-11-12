// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import AppIntents

/// What the rest timer's buttons on the Lock Screen and in the Dynamic Island do.
nonisolated enum RestTimerAction: Hashable, Sendable {
    case pause
    case resume
    case add(seconds: Int)
    case skip
}

/// The Lock Screen's buttons run their intents inside Kitae, which iOS starts in the
/// background if it isn't running. The app passes them to the workout in progress through
/// this, and returns once the Lock Screen shows the change.
@MainActor
enum RestTimerRemote {
    static var perform: ((RestTimerAction) async -> Void)?

    static func run(_ action: RestTimerAction) async {
        await perform?(action)
    }
}

nonisolated struct PauseRestIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause rest"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await RestTimerRemote.run(.pause)
        return .result()
    }
}

nonisolated struct ResumeRestIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Resume rest"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await RestTimerRemote.run(.resume)
        return .result()
    }
}

/// How much −15s and +15s change a rest by.
nonisolated let restAdjustSeconds = 15

nonisolated struct AddRestTimeIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "15 seconds more rest"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await RestTimerRemote.run(.add(seconds: restAdjustSeconds))
        return .result()
    }
}

nonisolated struct RemoveRestTimeIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "15 seconds less rest"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await RestTimerRemote.run(.add(seconds: -restAdjustSeconds))
        return .result()
    }
}

nonisolated struct SkipRestIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Skip rest"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await RestTimerRemote.run(.skip)
        return .result()
    }
}
