// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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
class ExerciseNotes internal constructor(private val database: KitaeDatabase) {
    private val changes = MutableStateFlow(0)

    /**
     * Every exercise's note, by exercise, now and after each change made here. Room doesn't see
     * this table change, so this follows the notes saved and deleted through this store.
     */
    fun observeAll(): Flow<Map<Long, String>> = changes.map { all() }

    /** Every exercise's note, by exercise, leaving out notes whose exercise is gone. */
    suspend fun all(): Map<Long, String> = database.withReadTransaction {
        usePrepared(
            """
            SELECT `exercise_notes`.`exerciseId`, `exercise_notes`.`note`
            FROM `exercise_notes`
            INNER JOIN `exercises` ON `exercises`.`id` = `exercise_notes`.`exerciseId`
            """,
        ) { statement -> buildMap { while (statement.step()) put(statement.getLong(0), statement.getText(1)) } }
    }

    /** Saves an exercise's note, trimmed. An empty one deletes it. */
    suspend fun save(exerciseId: Long, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return delete(exerciseId)
        database.withWriteTransaction {
            execute(
                """
                INSERT INTO `exercise_notes` (`exerciseId`, `note`) VALUES (?, ?)
                ON CONFLICT(`exerciseId`) DO UPDATE SET `note` = excluded.`note`
                """,
                exerciseId,
                trimmed,
            )
        }
        changes.update { it + 1 }
    }

    suspend fun delete(exerciseId: Long) {
        database.withWriteTransaction { execute("DELETE FROM `exercise_notes` WHERE `exerciseId` = ?", exerciseId) }
        changes.update { it + 1 }
    }

    internal companion object {
        /** Makes the table if it isn't there yet. */
        fun create(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `exercise_notes` (`exerciseId` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                    "PRIMARY KEY(`exerciseId`))",
            )
        }
    }
}
