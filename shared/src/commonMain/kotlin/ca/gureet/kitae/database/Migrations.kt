// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Databases and backups from versions 8 and 9 are brought up to 10, as the iPhone does
// (`Schema.migrate8` and `Schema.migrate9`). Version 8 is the oldest Kitae opens: Kitae for
// Android's earlier migrations aren't Kitae's own. Room then records version 10 and its hash.

internal fun kitaeMigrations(seed: Seed): Array<Migration> = arrayOf(
    LibraryKeys(seed.starterKeys),
    UsesWeight(seed.weightlessKeys),
)

/**
 * Version 9: exercises get a library key, and the starter exercises theirs ([keys], by id),
 * whether or not they were renamed.
 */
internal class LibraryKeys(private val keys: Map<Long, String>) : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `libraryKey` TEXT")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_libraryKey` ON `exercises` (`libraryKey`)",
        )
        connection.prepare("UPDATE `exercises` SET `libraryKey` = ? WHERE `id` = ?").use { statement ->
            for ((id, key) in keys.entries.sortedBy { it.key }) {
                statement.bindText(1, key)
                statement.bindLong(2, id)
                statement.step()
                statement.reset()
            }
        }
    }
}

/**
 * Version 10: exercises say whether they use weight, and Kitae's exercises done with the body
 * alone ([weightless], by library key) don't. Weights their sets already have stay as they
 * were, so turning weight back on brings them back.
 */
internal class UsesWeight(private val weightless: Set<String>) : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `usesWeight` INTEGER NOT NULL DEFAULT 1")
        connection.prepare("UPDATE `exercises` SET `usesWeight` = 0 WHERE `libraryKey` = ?").use { statement ->
            for (key in weightless.sorted()) {
                statement.bindText(1, key)
                statement.step()
                statement.reset()
            }
        }
    }
}
