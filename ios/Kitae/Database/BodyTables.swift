// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later

import Foundation
import GRDB

/// Kitae's own tables, for what people tell it about their body. They're in the same file as
/// Kitae for Android's tables, so backups carry them, but they aren't part of its Room schema.
///
/// Room on Android checks the identity hash in `room_master_table` and the tables it knows, and
/// nothing else, so it opens a database with these tables, keeps them in its own backups and
/// leaves them alone. They're laid out exactly as Room would make them for these entities, so
/// Kitae for Android can take them over in a later version without a migration that changes them:
///
///     @Entity(tableName = "body_measurements", indices = [Index(value = ["date"], unique = true)])
///     data class BodyMeasurement(val date: Long, val weight: Double?, val bodyFat: Double?,
///                                @PrimaryKey(autoGenerate = true) val id: Long = 0)
///
///     @Entity(tableName = "body_profile")
///     data class BodyProfile(val height: Double?, val birthDate: Long?, val sex: String?,
///                            val activityLevel: String?, @PrimaryKey val id: Long = 1)
///
/// `body_measurements` has a row a day: `date` in days since 1970 where the phone was (like
/// `sessions.date`), `weight` in kilograms and `bodyFat` in percent, like 18.5. Either may be
/// NULL. `body_profile` has one row, `id` 1: `height` in centimetres, `birthDate` in days since
/// 1970, `sex` "Female" or "Male", and `activityLevel` "Sedentary", "LightlyActive",
/// "ModeratelyActive", "VeryActive" or "ExtraActive", for the day outside workouts. NULL is
/// what wasn't given.
nonisolated enum BodyTables {
    static let statements = [
        "CREATE TABLE IF NOT EXISTS `body_measurements` (`date` INTEGER NOT NULL, `weight` REAL, `bodyFat` REAL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_measurements_date` ON `body_measurements` (`date`)",
        "CREATE TABLE IF NOT EXISTS `body_profile` (`height` REAL, `birthDate` INTEGER, `sex` TEXT, `activityLevel` TEXT, `id` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    ]

    /// Makes the tables that aren't there yet: in a new database, in one from before they
    /// existed, and in one restored from Kitae for Android.
    static func create(_ db: Database) throws {
        for statement in statements {
            try db.execute(sql: statement)
        }
    }
}

/// Reads and writes the weigh-ins and the profile.
nonisolated enum BodyStore {
    static func log(_ db: Database) throws -> BodyLog {
        let entries = try Row.fetchAll(db, sql: "SELECT * FROM `body_measurements` ORDER BY `date`").map { row in
            BodyEntry(id: row["id"], date: row["date"], weightKg: row["weight"], bodyFatPercent: row["bodyFat"])
        }
        let profile = try Row.fetchOne(db, sql: "SELECT * FROM `body_profile` WHERE `id` = 1").map { row in
            BodyProfile(
                heightCm: row["height"],
                birthDate: row["birthDate"],
                sex: (row["sex"] as String?).flatMap(Sex.init(rawValue:)),
                activityLevel: (row["activityLevel"] as String?).flatMap(ActivityLevel.init(rawValue:))
            )
        }
        return BodyLog(profile: profile ?? BodyProfile(), entries: entries)
    }

    /// Saves a day's weight and body fat. A day has one weigh-in, so this replaces whatever
    /// that day had, and `replacing`, the weigh-in being changed, which may have been on
    /// another day. With neither a weight nor body fat, nothing is saved for the day.
    static func save(_ db: Database, date: Int, weightKg: Double?, bodyFatPercent: Double?, replacing id: Int64? = nil) throws {
        if let id {
            try db.execute(sql: "DELETE FROM `body_measurements` WHERE `id` = ?", arguments: [id])
        }
        guard weightKg != nil || bodyFatPercent != nil else {
            try db.execute(sql: "DELETE FROM `body_measurements` WHERE `date` = ?", arguments: [date])
            return
        }
        try db.execute(sql: """
            INSERT INTO `body_measurements` (`date`, `weight`, `bodyFat`) VALUES (?, ?, ?)
            ON CONFLICT(`date`) DO UPDATE SET `weight` = excluded.`weight`, `bodyFat` = excluded.`bodyFat`
            """, arguments: [date, weightKg, bodyFatPercent])
    }

    static func delete(_ db: Database, id: Int64) throws {
        try db.execute(sql: "DELETE FROM `body_measurements` WHERE `id` = ?", arguments: [id])
    }

    static func save(_ db: Database, profile: BodyProfile) throws {
        try db.execute(sql: """
            INSERT OR REPLACE INTO `body_profile` (`height`, `birthDate`, `sex`, `activityLevel`, `id`)
            VALUES (?, ?, ?, ?, 1)
            """, arguments: [profile.heightCm, profile.birthDate, profile.sex?.rawValue, profile.activityLevel?.rawValue])
    }
}
