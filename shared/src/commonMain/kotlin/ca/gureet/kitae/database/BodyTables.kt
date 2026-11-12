// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ca.gureet.kitae.model.ActivityLevel
import ca.gureet.kitae.model.BodyEntry
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.BodyProfile
import ca.gureet.kitae.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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

/**
 * Reads and writes the weigh-ins and the profile, as the iPhone's `BodyStore` does, reached as
 * [KitaeDatabase.body]. Room doesn't see these tables change, so [observeLog] follows the changes
 * made through this store.
 */
class BodyStore internal constructor(private val database: KitaeDatabase) {
    private val changes = MutableStateFlow(0)

    /** The profile and every weigh-in, now and after each change. */
    fun observeLog(): Flow<BodyLog> = changes.map { log() }

    suspend fun log(): BodyLog = database.withReadTransaction {
        val entries = usePrepared("SELECT `id`, `date`, `weight`, `bodyFat` FROM `body_measurements` ORDER BY `date`") { statement ->
            buildList {
                while (statement.step()) {
                    add(BodyEntry(statement.getLong(0), statement.getLong(1), statement.doubleOrNull(2), statement.doubleOrNull(3)))
                }
            }
        }
        val profile = usePrepared("SELECT `height`, `birthDate`, `sex`, `activityLevel` FROM `body_profile` WHERE `id` = 1") { statement ->
            if (!statement.step()) {
                null
            } else {
                BodyProfile(
                    heightCm = statement.doubleOrNull(0),
                    birthDate = statement.longOrNull(1),
                    sex = Sex.fromStored(statement.textOrNull(2)),
                    activityLevel = ActivityLevel.fromStored(statement.textOrNull(3)),
                )
            }
        }
        BodyLog(profile ?: BodyProfile(), entries)
    }

    /**
     * Saves a day's weight and body fat. A day has one weigh-in, so this replaces whatever that day
     * had, and [replacingId], the weigh-in being changed, which may have been on another day. With
     * neither a weight nor body fat, nothing is saved for the day.
     */
    suspend fun save(date: Long, weightKg: Double?, bodyFatPercent: Double?, replacingId: Long? = null) {
        database.withWriteTransaction {
            if (replacingId != null) execute("DELETE FROM `body_measurements` WHERE `id` = ?", replacingId)
            if (weightKg == null && bodyFatPercent == null) {
                execute("DELETE FROM `body_measurements` WHERE `date` = ?", date)
            } else {
                execute(
                    """
                    INSERT INTO `body_measurements` (`date`, `weight`, `bodyFat`) VALUES (?, ?, ?)
                    ON CONFLICT(`date`) DO UPDATE SET `weight` = excluded.`weight`, `bodyFat` = excluded.`bodyFat`
                    """,
                    date,
                    weightKg,
                    bodyFatPercent,
                )
            }
        }
        changes.update { it + 1 }
    }

    suspend fun delete(id: Long) {
        database.withWriteTransaction { execute("DELETE FROM `body_measurements` WHERE `id` = ?", id) }
        changes.update { it + 1 }
    }

    suspend fun save(profile: BodyProfile) {
        database.withWriteTransaction {
            execute(
                """
                INSERT OR REPLACE INTO `body_profile` (`height`, `birthDate`, `sex`, `activityLevel`, `id`)
                VALUES (?, ?, ?, ?, 1)
                """,
                profile.heightCm,
                profile.birthDate,
                profile.sex?.stored,
                profile.activityLevel?.stored,
            )
        }
        changes.update { it + 1 }
    }
}
