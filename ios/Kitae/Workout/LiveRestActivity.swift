// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import ActivityKit
import Foundation
import os

/// Puts the rest timer on the Lock Screen and in the Dynamic Island while a rest is going,
/// and takes it away when the rest ends or the workout does.
final class LiveRestActivity {
    /// The rest the current Live Activity was started for, so one people swiped away doesn't
    /// come back until the next rest.
    private var shownRest: (workoutID: Int64, number: Int)?
    private var sentState: RestActivityAttributes.ContentState?
    /// Changes reach ActivityKit one after another, in order. This is the latest.
    private(set) var lastChange: Task<Void, Never>?

    private static let log = Logger(subsystem: "ca.gureet.kitae", category: "activity")

    func update(for workout: Workout?, formatter: WeightFormatter) {
        let state = workout.flatMap { Self.state(for: $0, formatter: formatter) }
        // A Live Activity goes stale when a rest runs out while Kitae is closed.
        let activities = Activity<RestActivityAttributes>.activities.filter {
            $0.activityState == .active || $0.activityState == .stale
        }
        let current = state == nil ? nil : activities.first { $0.attributes.workoutID == workout?.id }
        let finished = activities.filter { $0.id != current?.id }
        if !finished.isEmpty {
            send {
                for activity in finished {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
        guard let workout, let state else {
            sentState = nil
            return
        }

        let content = ActivityContent(state: state, staleDate: state.endsAt)
        if let current {
            guard state != sentState else { return }
            sentState = state
            send { await current.update(content) }
            return
        }
        let rest = (workoutID: workout.id, number: workout.restsStarted)
        guard shownRest.map({ $0 != rest }) ?? true,
              ActivityAuthorizationInfo().areActivitiesEnabled
        else { return }
        do {
            _ = try Activity.request(
                attributes: RestActivityAttributes(workoutID: workout.id, dayName: workout.dayName),
                content: content
            )
            shownRest = rest
            sentState = state
        } catch {
            // Only possible while Kitae is open; it tries again when it comes back.
            Self.log.notice("Couldn't show the rest timer: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func send(_ change: @escaping () async -> Void) {
        let previous = lastChange
        lastChange = Task {
            await previous?.value
            await change()
        }
    }

    /// What the Lock Screen shows for this workout, or nil when there's no rest going.
    static func state(for workout: Workout, formatter: WeightFormatter) -> RestActivityAttributes.ContentState? {
        let rest = workout.rest
        guard rest.isActive else { return nil }
        let next = workout.nextSet
        return RestActivityAttributes.ContentState(
            endsAt: rest.endsAt,
            pausedRemaining: rest.pausedRemaining,
            total: rest.total,
            doneSets: workout.doneCount,
            setCount: workout.setCount,
            nextExercise: next?.exercise.name,
            nextSet: next.map { describe($0.set, of: $0.exercise, formatter: formatter) }
        )
    }

    /// A set's reps and weight like "10 × 17.5 kg".
    static func describe(_ set: WorkoutSet, of exercise: Exercise, formatter: WeightFormatter) -> String {
        formatter.summary(reps: set.reps, kilograms: exercise.weight(set.weightKg), isIsometric: exercise.isIsometric)
    }
}
