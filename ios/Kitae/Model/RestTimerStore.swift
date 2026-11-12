// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// Keeps the rest timer when iOS closes Kitae, so the Lock Screen's buttons still work and
/// the timer is still running when Kitae opens again.
final class RestTimerStore {
    private nonisolated struct Saved: Codable {
        var workoutID: Int64
        var workoutStartedAt: Date
        var rest: RestTimer
    }

    private let defaults: UserDefaults?
    private let key = "restTimer"

    init(defaults: UserDefaults?) {
        self.defaults = defaults
    }

    static var standard: RestTimerStore { RestTimerStore(defaults: .standard) }

    /// Keeps nothing, for tests and example data.
    static var none: RestTimerStore { RestTimerStore(defaults: nil) }

    func save(_ rest: RestTimer, workoutID: Int64, workoutStartedAt: Date) {
        guard let defaults else { return }
        let saved = Saved(workoutID: workoutID, workoutStartedAt: workoutStartedAt, rest: rest)
        if rest.isActive, let data = try? JSONEncoder().encode(saved) {
            defaults.set(data, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    /// The rest that was going in this workout, unless it has run out since.
    func load(workoutID: Int64, workoutStartedAt: Date, now: Date) -> RestTimer? {
        guard let data = defaults?.data(forKey: key),
              let saved = try? JSONDecoder().decode(Saved.self, from: data),
              saved.workoutID == workoutID,
              abs(saved.workoutStartedAt.timeIntervalSince(workoutStartedAt)) < 1,
              saved.rest.isActive
        else { return nil }
        if let endsAt = saved.rest.endsAt, endsAt <= now { return nil }
        return saved.rest
    }
}
