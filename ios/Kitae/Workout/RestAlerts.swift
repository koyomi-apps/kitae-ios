// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import UserNotifications

/// "Rest over: time for your next set", for when a rest runs out, like Kitae for Android's
/// alert. With Kitae open, only its sound plays; the workout screen taps the phone too.
final class RestAlerts: NSObject, UNUserNotificationCenterDelegate {
    private static let identifier = "rest-over"
    private let center = UNUserNotificationCenter.current()
    /// When the alert that's waiting goes off.
    private var scheduledFor: Date?
    private var hasAsked = false

    /// Called when people allow alerts, so the rest that's going gets one.
    var onAllowed: (() -> Void)?

    override init() {
        super.init()
        center.delegate = self
    }

    /// Sets the alert for when the rest ends, or takes it away if the rest stopped early.
    func update(for rest: RestTimer?) {
        let endsAt = rest?.endsAt
        guard endsAt != scheduledFor else { return }
        // A rest that ran out keeps its alert: it's going off now.
        if let scheduledFor, endsAt != nil || scheduledFor.timeIntervalSinceNow > 1 {
            center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        }
        scheduledFor = nil
        guard let endsAt, endsAt.timeIntervalSinceNow >= 1 else { return }
        askOnce()

        let content = UNMutableNotificationContent()
        content.title = "Rest over"
        content.body = "Time for your next set"
        content.sound = .default
        content.threadIdentifier = "rest"
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: endsAt.timeIntervalSinceNow, repeats: false)
        center.add(UNNotificationRequest(identifier: Self.identifier, content: content, trigger: trigger)) { _ in }
        scheduledFor = endsAt
    }

    /// Asks for alerts the first time a rest starts, as Kitae for Android does.
    private func askOnce() {
        guard !hasAsked else { return }
        hasAsked = true
        Task {
            let settings = await center.notificationSettings()
            guard settings.authorizationStatus == .notDetermined else { return }
            let allowed = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
            if allowed {
                scheduledFor = nil
                onAllowed?()
            }
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.sound]
    }
}
