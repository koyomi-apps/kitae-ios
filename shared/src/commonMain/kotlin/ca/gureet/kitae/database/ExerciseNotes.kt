// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * People's notes on their exercises, like a seat height or a grip, in Kitae's own table, as the
 * iPhone keeps them (`ExerciseNotes.swift`). Like [BodyTables], it's outside Room's schema and
 * laid out as Room would make it for this entity:
 *
 *     @Entity(tableName = "exercise_notes")
 *     data class ExerciseNote(@PrimaryKey val exerciseId: Long, val note: String)
 *
 * An exercise has one note at most, and deleting the exercise deletes it. There's no foreign
 * key on purpose: a migration that rebuilds `exercises` would take every note with it.
 */
internal object ExerciseNotes {
    /** Makes the table if it isn't there yet. */
    fun create(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_notes` (`exerciseId` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "PRIMARY KEY(`exerciseId`))",
        )
    }
}
