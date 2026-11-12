// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import Testing
@testable import Kitae

/// The rest timer's time is never cut short: it keeps room for its widest, and Finish makes
/// room for it.
struct RestRoomTests {
    @Test func theTimeKeepsRoomForItsWidest() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        var rest = RestTimer()
        #expect(rest.widestClock == "0:00")
        rest.start(seconds: 95, at: now)
        #expect(rest.widestClock == "0:00")
        rest.start(seconds: 599, at: now)
        #expect(rest.widestClock == "0:00")
        rest.add(15, at: now)
        #expect(rest.widestClock == "00:00")
        rest.start(seconds: 600, at: now)
        #expect(rest.widestClock == "00:00")
        rest.start(seconds: 3600, at: now)
        #expect(rest.widestClock == "0:00:00")
        // As many characters as the time it starts at.
        #expect(restLabel(seconds: 599).count == "0:00".count)
        #expect(restLabel(seconds: 600).count == "00:00".count)
    }

    @Test func finishTakesTheRoomTheRestTimerLeaves() {
        // Its usual width when there's room…
        #expect(WorkoutBottomLayout.finishWidth(besideRest: 252, in: 370) == 100)
        // …then narrower, down to just its checkmark…
        #expect(WorkoutBottomLayout.finishWidth(besideRest: 252, in: 343) == 83)
        #expect(WorkoutBottomLayout.finishWidth(besideRest: 252, in: 318) == 58)
        // …and below that, under the rest timer.
        #expect(WorkoutBottomLayout.finishWidth(besideRest: 252, in: 317) == nil)
    }
}
