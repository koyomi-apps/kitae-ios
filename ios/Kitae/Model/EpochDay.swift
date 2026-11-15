// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// Dates the way Kitae for Android stores them: a workout's day as a count of days since
/// 1 January 1970 in the phone's time zone, and moments as milliseconds since 1970. The shared
/// module counts the days (`EpochDay.kt`).
nonisolated enum EpochDay {
    /// The day `date` falls on where the phone is. Counted on the Gregorian calendar
    /// whatever calendar the phone uses, like Android's `LocalDate.toEpochDays()`.
    static func of(_ date: Date, in timeZone: TimeZone = .current) -> Int {
        // Down to the millisecond, as a day starts on one.
        let milliseconds = Int64((date.timeIntervalSince1970 * 1000).rounded(.down))
        return Int(IPhoneDates.shared.dayOf(epochMilliseconds: milliseconds, zone: timeZone.identifier))
    }

    /// Midnight at the start of that day where the phone is.
    static func date(_ day: Int, in timeZone: TimeZone = .current) -> Date {
        Date(epochMilliseconds: IPhoneDates.shared.startOf(day: Int64(day), zone: timeZone.identifier))
    }
}

nonisolated extension Date {
    /// Milliseconds since 1970, as Kitae for Android stores the start and end of a workout.
    var epochMilliseconds: Int64 {
        Int64((timeIntervalSince1970 * 1000).rounded())
    }

    init(epochMilliseconds: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(epochMilliseconds) / 1000)
    }
}
