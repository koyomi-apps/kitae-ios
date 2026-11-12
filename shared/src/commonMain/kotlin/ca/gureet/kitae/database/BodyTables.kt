// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Kitae's own tables for what people tell it about their body, as the iPhone keeps them
 * (`BodyTables.swift`). They're in the same file as Room's tables, so backups carry them, but
 * outside Room's schema: Room checks the identity hash and its own tables, and leaves these
 * alone. They're laid out as Room would make them for these entities, so a later version can
 * take them into Room's schema unchanged:
 *
 *     @Entity(tableName = "body_measurements", indices = [Index(value = ["date"], unique = true)])
 *     data class BodyMeasurement(val date: Long, val weight: Double?, val bodyFat: Double?,
 *                                @PrimaryKey(autoGenerate = true) val id: Long = 0)
 *
 *     @Entity(tableName = "body_profile")
 *     data class BodyProfile(val height: Double?, val birthDate: Long?, val sex: String?,
 *                            val activityLevel: String?, @PrimaryKey val id: Long = 1)
 *
 * `body_measurements` has a row a day: `date` in days since 1970 where the phone was (like
 * [Workout.date]), `weight` in kilograms and `bodyFat` in percent, like 18.5. Either may be
 * NULL. `body_profile` has one row, `id` 1: `height` in centimetres, `birthDate` in days since
 * 1970, `sex` "Female" or "Male", and `activityLevel` "Sedentary", "LightlyActive",
 * "ModeratelyActive", "VeryActive" or "ExtraActive", for the day outside workouts. NULL is
 * what wasn't given.
 */
internal object BodyTables {
    /** Makes the tables that aren't there yet. */
    fun create(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_measurements` (`date` INTEGER NOT NULL, `weight` REAL, `bodyFat` REAL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_measurements_date` ON `body_measurements` (`date`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_profile` (`height` REAL, `birthDate` INTEGER, `sex` TEXT, " +
                "`activityLevel` TEXT, `id` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}
