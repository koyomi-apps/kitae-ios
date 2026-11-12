// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The database's file and the ones SQLite keeps beside it, for restoring a backup and Reset
 * Kitae once the app has closed the database. (The iPhone copies into its open database instead.)
 */
object DatabaseFiles {
    private val companions = listOf("-wal", "-shm", "-journal")

    /**
     * Deletes the database at [path] with its log, shared memory, journal and Room's lock file, as
     * Reset Kitae does: the next time it opens, it's a new one, with the starter plan and exercises.
     */
    fun delete(path: String) {
        for (suffix in listOf("", ".lck") + companions) SystemFileSystem.delete(Path(path + suffix), mustExist = false)
    }

    /**
     * Puts a copy of the database at [source] in place of the one at [path], whose log and journal
     * go with it, so they're never read into the new one.
     */
    fun replace(path: String, source: String) {
        val incoming = Path("$path-incoming")
        SystemFileSystem.source(Path(source)).buffered().use { from ->
            SystemFileSystem.sink(incoming).use { to -> from.transferTo(to) }
        }
        for (suffix in companions) SystemFileSystem.delete(Path(path + suffix), mustExist = false)
        SystemFileSystem.atomicMove(incoming, Path(path))
    }
}
