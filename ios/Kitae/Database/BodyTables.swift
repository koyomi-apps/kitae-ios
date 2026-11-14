// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import KitaeCore

/// Reads and writes the weigh-ins and the profile, in Kitae's own tables, which the shared module
/// makes and keeps (`BodyTables.kt` says how they're laid out).
nonisolated enum BodyStore {
    static func log(_ db: Database) throws -> BodyLog {
        BodyLog(try db.core.bodyLog())
    }

    /// Saves a day's weight and body fat. A day has one weigh-in, so this replaces whatever
    /// that day had, and `replacing`, the weigh-in being changed, which may have been on
    /// another day. With neither a weight nor body fat, nothing is saved for the day.
    static func save(_ db: Database, date: Int, weightKg: Double?, bodyFatPercent: Double?, replacing id: Int64? = nil) throws {
        try db.core.saveWeighIn(date: Int64(date), weightKg: weightKg?.kotlin, bodyFatPercent: bodyFatPercent?.kotlin, replacingId: id?.kotlin)
    }

    static func delete(_ db: Database, id: Int64) throws {
        try db.core.deleteWeighIn(id: id)
    }

    static func save(_ db: Database, profile: BodyProfile) throws {
        try db.core.saveProfile(heightCm: profile.heightCm?.kotlin, birthDate: profile.birthDate.map { Int64($0).kotlin },
                               sex: profile.sex?.rawValue, activityLevel: profile.activityLevel?.rawValue)
    }
}
