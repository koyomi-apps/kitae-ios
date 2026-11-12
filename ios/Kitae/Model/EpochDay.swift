// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation

/// Dates the way Kitae for Android stores them: a workout's day as a count of days since
/// 1 January 1970 in the phone's time zone, and moments as milliseconds since 1970.
nonisolated enum EpochDay {
    /// The day `date` falls on where the phone is. Counted on the Gregorian calendar
    /// whatever calendar the phone uses, like Android's `LocalDate.toEpochDays()`.
    static func of(_ date: Date, in timeZone: TimeZone = .current) -> Int {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = timeZone
        let parts = local.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = .gmt
        guard let midnight = utc.date(from: parts) else { return 0 }
        return Int((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    /// Midnight at the start of that day where the phone is.
    static func date(_ day: Int, in timeZone: TimeZone = .current) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = .gmt
        let parts = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: TimeInterval(day) * 86_400))
        var local = Calendar(identifier: .gregorian)
        local.timeZone = timeZone
        return local.date(from: parts) ?? Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
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
