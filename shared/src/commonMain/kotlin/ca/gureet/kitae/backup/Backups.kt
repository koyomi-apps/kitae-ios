// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.backup

import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ca.gureet.kitae.database.DatabaseFiles
import ca.gureet.kitae.database.KitaeDatabase
import ca.gureet.kitae.database.hasRow
import ca.gureet.kitae.database.texts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Why a backup can't be restored or made, for each app to say in its words, as the iPhone's `BackupError`. */
enum class BackupProblem {
    /** "This file isn't a Kitae backup." */
    NotKitae,

    /** Its database is older than version 8, Kitae for Android 2.0.0's: an earlier one's is Kenko's. */
    OlderVersion,

    /** "This backup is from a newer version of Kitae. Update Kitae, then try again." */
    NewerVersion,

    /** "This backup is damaged, so it can't be restored." */
    Damaged,

    /** "Finish or discard the workout in progress first." */
    WorkoutInProgress,

    /** "Kitae can't reach the backup folder. Choose it again in Settings." */
    FolderUnavailable,
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception("Backup problem: $problem", cause)

/**
 * Each app's zip library, which backups call: `java.util.zip` on Android, ZIPFoundation on the
 * iPhone. Paths are files' paths on the phone.
 */
interface BackupArchive {
    /** Writes a zip at [zip] holding each of [files], compressed, under its name. */
    fun write(zip: String, files: List<ArchiveFile>)

    /** The files in the zip at [zip], leaving out its folders, or throws when it isn't a zip. */
    fun files(zip: String): List<ArchiveEntry>

    /** Writes the file at [entry] in the zip at [zip] out to [destination], or throws when it can't. */
    fun extract(zip: String, entry: String, destination: String)
}

/** A file to put in a zip: its [name] in the zip, and where it is. */
data class ArchiveFile(val name: String, val path: String)

/** A file in a zip: its [path] in the zip, and its [size] once it's taken out, in bytes. */
data class ArchiveEntry(val path: String, val size: Long)

/**
 * A backup taken apart and checked, ready to restore: its [database] on its own in the work
 * folder, and its [preferences], if it has a settings file that can be read.
 */
class BackupContents(val database: String, val preferences: Map<String, PreferenceValue>?)

/**
 * Backups that either app restores, as the iPhone makes and checks them (`Backups.swift`). A
 * backup is a zip like Kitae for Android 2.0.0's: the database file `kenko_database`, and
 * `settings.preferences_pb` with the settings. Android's can also hold the database's log,
 * `kenko_database-wal`.
 *
 * Each app keeps its zip library ([BackupArchive]), the backup folder, and the copying in and out
 * of files people pick; it hands in an empty work folder for each backup made or opened, and
 * deletes it afterwards. To restore, an app opens and checks the backup ([open]), saves what it
 * has as [BEFORE_RESTORE_NAME] in the backup folder ([makeArchive]), then on Android closes the
 * database, [install]s the backup's and starts again, and takes in the backup's settings.
 */
object Backups {
    const val DATABASE_ENTRY = "kenko_database"
    const val LOG_ENTRY = "kenko_database-wal"
    const val SETTINGS_ENTRY = "settings.preferences_pb"

    /** What's in Kitae just before a backup is restored over it. */
    const val BEFORE_RESTORE_NAME = "kitae_backup_before_restore.zip"

    /** Bigger files than this in a zip aren't Kitae's. */
    private const val LARGEST_ENTRY = 1L shl 30

    /**
     * The versions a backup's database can be at, with the identity hash Room keeps in it at each:
     * 8 is Kitae for Android 2.0.0's, as older ones are Kenko's, and 10 is this app's. An older
     * one is brought up to date when it opens.
     */
    internal val identityHashes = mapOf(
        8 to "ed136697240555e41abaa05a57ab6667",
        9 to "5d1b3eb2bd3f9bcf00e2d5627becb694",
        10 to "a8dd8c0661148678f11cce771546f37e",
    )

    /**
     * "kitae_backup_10_10.zip" on 10 October ([today] in days since 1970): named like Kitae for
     * Android's backups, by day and month, so there's one a day and a day's backup replaces the
     * one from a year before.
     */
    fun fileName(today: Long): String {
        val date = LocalDate.fromEpochDays(today)
        return "kitae_backup_${twoDigits(date.day)}_${twoDigits(date.month.number)}.zip"
    }

    private fun twoDigits(number: Int) = number.toString().padStart(2, '0')

    /**
     * Makes the backup zip of [database] with [preferences] in [work], and returns where it is. The
     * database is copied as it is now, in one file without a log, while workouts can still be saved.
     */
    suspend fun makeArchive(
        database: KitaeDatabase,
        preferences: Map<String, PreferenceValue>,
        work: String,
        archive: BackupArchive,
    ): String {
        val databaseFile = Path(work, DATABASE_ENTRY)
        DatabaseFiles.delete(databaseFile.toString())
        database.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") { statement ->
                statement.bindText(1, databaseFile.toString())
                statement.step()
            }
        }
        BundledSQLiteDriver().open(databaseFile.toString()).use { it.execSQL("PRAGMA journal_mode = DELETE") }

        val settingsFile = Path(work, SETTINGS_ENTRY)
        SystemFileSystem.sink(settingsFile).buffered().use { it.write(PreferencesFile.encode(preferences)) }

        val zip = Path(work, "backup.zip").toString()
        archive.write(
            zip,
            listOf(ArchiveFile(DATABASE_ENTRY, databaseFile.toString()), ArchiveFile(SETTINGS_ENTRY, settingsFile.toString())),
        )
        return zip
    }

    /**
     * Takes the zip at [zip] apart in [work] and checks its database: Kitae's, at a version this
     * app opens, and whole. Throws [BackupException] when it isn't.
     */
    fun open(zip: String, work: String, archive: BackupArchive): BackupContents {
        val entries = try {
            archive.files(zip)
        } catch (error: Exception) {
            throw BackupException(BackupProblem.NotKitae, error)
        }
        val folder = Path(work, "contents")
        SystemFileSystem.createDirectories(folder)

        // Only the files Kitae writes, by name, wherever they are in the zip. The database's
        // shared-memory file is left behind: SQLite makes a new one from the log.
        val wanted = setOf(DATABASE_ENTRY, LOG_ENTRY, SETTINGS_ENTRY)
        val found = mutableSetOf<String>()
        for (entry in entries) {
            val name = entry.path.trimEnd('/').substringAfterLast('/')
            if (name !in wanted || name in found) continue
            if (entry.size > LARGEST_ENTRY) throw BackupException(BackupProblem.NotKitae)
            try {
                archive.extract(zip, entry.path, Path(folder, name).toString())
            } catch (error: Exception) {
                throw BackupException(BackupProblem.Damaged, error)
            }
            found += name
        }
        if (DATABASE_ENTRY !in found) throw BackupException(BackupProblem.NotKitae)

        val databaseFile = Path(folder, DATABASE_ENTRY).toString()
        check(databaseFile)

        val preferences = if (SETTINGS_ENTRY in found) {
            try {
                PreferencesFile.decode(SystemFileSystem.source(Path(folder, SETTINGS_ENTRY)).buffered().use { it.readByteArray() })
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return BackupContents(databaseFile, preferences)
    }

    /**
     * Puts a checked backup's database in place of the one at [database], which the app has closed.
     * When it opens again, a backup from an older version is brought up to date, and gets Kitae's
     * own tables and any of Kitae's exercises it hasn't had.
     */
    fun install(contents: BackupContents, database: String) {
        DatabaseFiles.replace(database, contents.database)
    }

    /**
     * Takes in the log Kitae for Android may have left beside its database, and checks the database
     * is Kitae's, at a version this app opens, and not damaged.
     */
    private fun check(file: String) {
        try {
            BundledSQLiteDriver().open(file).use { connection ->
                connection.execSQL("PRAGMA journal_mode = DELETE")
                if (!connection.hasRow("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table'")) {
                    throw BackupException(BackupProblem.NotKitae)
                }
                val version = connection.number("PRAGMA user_version")
                if (version < identityHashes.keys.min()) throw BackupException(BackupProblem.OlderVersion)
                if (version > identityHashes.keys.max()) throw BackupException(BackupProblem.NewerVersion)
                val hash = connection.texts("SELECT identity_hash FROM room_master_table WHERE id = 42").firstOrNull()
                if (hash != identityHashes[version]) throw BackupException(BackupProblem.NotKitae)
                if (connection.texts("PRAGMA quick_check").firstOrNull() != "ok") throw BackupException(BackupProblem.Damaged)
            }
        } catch (error: SQLiteException) {
            throw when (error.resultCode) {
                SQLITE_CORRUPT -> BackupException(BackupProblem.Damaged, error)
                SQLITE_NOTADB -> BackupException(BackupProblem.NotKitae, error)
                else -> error
            }
        }
    }

    private const val SQLITE_CORRUPT = 11
    private const val SQLITE_NOTADB = 26

    /** SQLite's primary result code, which androidx.sqlite puts in the message. */
    private val SQLiteException.resultCode: Int?
        get() = Regex("""[Ee]rror code: (\d+)""").find(message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.and(0xFF)

    private fun SQLiteConnection.number(sql: String): Int = prepare(sql).use { statement ->
        statement.step()
        statement.getLong(0).toInt()
    }
}
