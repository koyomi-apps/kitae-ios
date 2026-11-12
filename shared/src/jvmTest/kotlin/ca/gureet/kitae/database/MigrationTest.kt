// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Databases and backups from versions 8 and 9 are brought up to 10 when Room opens them, as on
 * the iPhone (`LibraryTests` in `ios/KitaeTests`). Adding the library's later exercises as a
 * database opens comes with Kitae's own tables.
 */
class MigrationTest {
    private val seed = Seed.decode(File(System.getProperty("kitae.seed")).readText())
    private val folder = createTempDirectory("kitae")
    private val file = folder.resolve("kenko_database")

    @AfterTest
    fun deleteFiles() {
        folder.toFile().deleteRecursively()
    }

    @Test
    fun theSeedHasKitaesExercises() {
        assertEquals((1L..75L).toSet(), seed.starterKeys.keys)
        assertEquals("barbell-bench-press", seed.starterKeys[21])
        assertEquals(seed.exercises.size, seed.exercises.map { it.key }.toSet().size)
        assertTrue("sit-ups" in seed.weightlessKeys)
    }

    @Test
    fun aVersion8DatabaseIsBroughtUpToDate() = runBlocking {
        makeDatabase(version = 8) { connection ->
            // Leg Press was deleted, Curls renamed, and someone made an exercise of their own.
            connection.execSQL("DELETE FROM exercises WHERE id = 12")
            connection.execSQL("UPDATE exercises SET name = 'Dumbbell Curls' WHERE id = 1")
            connection.execSQL("INSERT INTO exercises (name, target, isIsometric) VALUES ('Example Press', 'Shoulders', 0)")
        }
        val database = open()
        assertEquals(10, database.number("PRAGMA user_version"))
        assertEquals(VERSION_10_HASH, database.texts("SELECT identity_hash FROM room_master_table WHERE id = 42").single())
        assertEquals(1, database.number("SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_exercises_libraryKey'"))
        // The starters have their keys, renamed or not, and Leg Press stays deleted.
        val keys = database.keys()
        assertEquals(seed.starterKeys - 12L, keys)
        assertEquals("dumbbell-curls", keys[1])
        // Sit-ups and the other starters done with the body alone don't use weight.
        assertEquals(seed.weightlessKeys.intersect(keys.values.toSet()), database.weightless())
        assertEquals(13, database.weightless().size)
        // The exercise people made stays theirs, and every row stays.
        assertEquals(
            1,
            database.number("SELECT COUNT(*) FROM exercises WHERE name = 'Example Press' AND libraryKey IS NULL AND usesWeight = 1"),
        )
        assertEquals(75, database.number("SELECT COUNT(*) FROM exercises"))
        database.close()

        // Opening it again changes nothing.
        val again = open()
        assertEquals(keys, again.keys())
        assertEquals(75, again.number("SELECT COUNT(*) FROM exercises"))
        again.close()
    }

    @Test
    fun aVersion9DatabaseLearnsWhichExercisesUseWeight() = runBlocking {
        makeDatabase(version = 9) { connection ->
            // Leg raises done with the 20 kg of Kitae for Android's starter plan, and someone's
            // own exercise.
            connection.execSQL("INSERT INTO sessions (date, isFinished) VALUES (20000, 1)")
            connection.execSQL(
                "INSERT INTO sets (reps, weight, type, `order`, sessionId, exerciseId, rir) " +
                    "VALUES (12, 20, 'Standard', 0, last_insert_rowid(), 20, 2)",
            )
            connection.execSQL("INSERT INTO exercises (name, target, isIsometric) VALUES ('Weighted Dips', 'Triceps', 0)")
        }
        val database = open()
        assertEquals(10, database.number("PRAGMA user_version"))
        assertEquals(VERSION_10_HASH, database.texts("SELECT identity_hash FROM room_master_table WHERE id = 42").single())
        assertEquals(seed.weightlessKeys.intersect(seed.starterKeys.values.toSet()), database.weightless())
        assertEquals(1, database.number("SELECT COUNT(*) FROM exercises WHERE name = 'Weighted Dips' AND usesWeight = 1"))
        // The set keeps its 20 kg, which Kitae leaves out while leg raises don't use weight.
        assertEquals(1, database.number("SELECT COUNT(*) FROM sets WHERE exerciseId = 20 AND weight = 20"))
        database.close()
    }

    /**
     * Makes the file a database as Kitae made it at [version], 8 or 9: Room's tables from the
     * iPhone's schema file for that version, with the starter exercises (and from version 9
     * their keys), then [changes].
     */
    private suspend fun makeDatabase(version: Int, changes: (SQLiteConnection) -> Unit) {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Path(System.getProperty("kitae.schemas")),
            databasePath = file,
            driver = BundledSQLiteDriver(),
            databaseClass = KitaeDatabase::class,
            databaseFactory = { KitaeDatabaseConstructor.initialize() },
        )
        helper.createDatabase(version).use { connection ->
            val starters = seed.exercises.filter { it.id != null }
            connection.prepare(
                "INSERT INTO exercises (id, name, target, isIsometric, instructions) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                for (exercise in starters) {
                    statement.bindLong(1, exercise.id!!)
                    statement.bindText(2, exercise.name)
                    statement.bindText(3, exercise.target)
                    statement.bindBoolean(4, exercise.isIsometric)
                    exercise.instructions?.let { statement.bindText(5, it) } ?: statement.bindNull(5)
                    statement.step()
                    statement.reset()
                }
            }
            if (version >= 9) {
                connection.prepare("UPDATE exercises SET libraryKey = ? WHERE id = ?").use { statement ->
                    for (exercise in starters) {
                        statement.bindText(1, exercise.key)
                        statement.bindLong(2, exercise.id!!)
                        statement.step()
                        statement.reset()
                    }
                }
            }
            changes(connection)
        }
    }

    private fun open(): KitaeDatabase =
        Room.databaseBuilder(file.toString()) { KitaeDatabaseConstructor.initialize() }.buildKitaeDatabase(seed)

    private suspend fun KitaeDatabase.number(sql: String): Long = useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            statement.step()
            statement.getLong(0)
        }
    }

    private suspend fun KitaeDatabase.texts(sql: String): List<String> = useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
    }

    private suspend fun KitaeDatabase.keys(): Map<Long, String> = useReaderConnection { connection ->
        connection.usePrepared("SELECT id, libraryKey FROM exercises WHERE libraryKey IS NOT NULL") { statement ->
            buildMap { while (statement.step()) put(statement.getLong(0), statement.getText(1)) }
        }
    }

    private suspend fun KitaeDatabase.weightless(): Set<String> =
        texts("SELECT libraryKey FROM exercises WHERE usesWeight = 0").toSet()

    private companion object {
        const val VERSION_10_HASH = "a8dd8c0661148678f11cce771546f37e"
    }
}
