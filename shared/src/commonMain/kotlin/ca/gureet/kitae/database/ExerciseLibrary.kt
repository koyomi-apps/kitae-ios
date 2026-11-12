// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Kitae's own exercises: the ones a database starts with and the ones later versions bring, as
 * on the iPhone (`ExerciseLibrary.swift`). Each has a library key ([Exercise.libraryKey], like
 * "barbell-bench-press") that never changes, by which Kitae finds its animation, muscles and
 * energy. Exercises people make have none.
 *
 * Which keys a database has been given is kept in Kitae's own table `exercise_library`, outside
 * Room's schema like [BodyTables], so a new version adds the exercises it brings once and never
 * brings back one people deleted. It's laid out as Room would make it for this entity:
 *
 *     @Entity(tableName = "exercise_library")
 *     data class LibraryEntity(@PrimaryKey val key: String)
 */
internal object ExerciseLibrary {
    /**
     * Makes the table if it isn't there yet. Every Kitae database started with the starter
     * exercises, so one without the table has been given those, deleted or not.
     */
    fun create(connection: SQLiteConnection, seed: Seed) {
        if (connection.hasRow("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'exercise_library'")) return
        connection.execSQL("CREATE TABLE IF NOT EXISTS `exercise_library` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
        for (exercise in seed.exercises) {
            if (exercise.id != null) {
                connection.execute("INSERT OR IGNORE INTO `exercise_library` (`key`) VALUES (?)", exercise.key)
            }
        }
    }

    /**
     * Adds the library's exercises the database hasn't been given yet, with the next free ids.
     * An exercise people made with the same name becomes Kitae's (it gets the key, and so the
     * animation) instead of being added twice.
     */
    fun sync(connection: SQLiteConnection, library: List<Seed.Exercise>) {
        val given = connection.texts("SELECT `key` FROM `exercise_library`").toSet()
        for (exercise in library) {
            if (exercise.key in given) continue
            if (!connection.hasRow("SELECT 1 FROM `exercises` WHERE `libraryKey` = ?", exercise.key)) {
                val sameName = connection.prepare(
                    "SELECT `id`, `libraryKey` FROM `exercises` WHERE `name` = ? COLLATE NOCASE",
                ).use { statement ->
                    statement.bindText(1, exercise.name)
                    if (statement.step()) SameName(statement.getLong(0), !statement.isNull(1)) else null
                }
                when {
                    sameName == null -> exercise.insert(connection)
                    // Someone's own exercise becomes Kitae's; one of Kitae's own keeps its key.
                    !sameName.hasKey -> connection.execute(
                        "UPDATE `exercises` SET `libraryKey` = ? WHERE `id` = ?",
                        exercise.key,
                        sameName.id,
                    )
                }
            }
            connection.execute("INSERT OR IGNORE INTO `exercise_library` (`key`) VALUES (?)", exercise.key)
        }
    }

    private class SameName(val id: Long, val hasKey: Boolean)
}
