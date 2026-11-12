// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import ActivityKit
import SwiftUI
import WidgetKit

/// The rest timer on the Lock Screen and in the Dynamic Island. Its views are in Shared/,
/// so the app can draw them too.
struct RestTimerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            RestActivityLockScreen(attributes: context.attributes, state: context.state, isStale: context.isStale)
                .activityBackgroundTint(RestActivityStyle.panel)
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    RestIslandStatus(state: context.state, isStale: context.isStale)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    RestIslandDayName(name: context.attributes.dayName)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    RestIslandBottom(state: context.state, isStale: context.isStale)
                }
            } compactLeading: {
                RestActivityIcon()
            } compactTrailing: {
                RestIslandCompactClock(state: context.state, isStale: context.isStale)
            } minimal: {
                RestIslandMinimal(state: context.state, isStale: context.isStale)
            }
            .keylineTint(Color.olive)
        }
    }
}
