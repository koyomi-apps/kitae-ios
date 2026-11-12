// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Kitae's own exercises: how databases from before library keys get them, and how the exercises
 * a later version brings arrive, as the iPhone's `LibraryTests` check them.
 */
class LibraryTest : DatabaseFileTest() {
    private val seed = bundledSeed

    /** An exercise a later version could bring, made up so it's never one of Kitae's own. */
    private val examplePress = Seed.Exercise(
        key = "example-press",
        name = "Example Press",
        target = "Shoulders",
        isIsometric = false,
        usesWeight = true,
        instructions = "Hold the handle at your shoulder.\nPress it up and forward.",
    )

    /** The seed of a later version that brings [examplePress]. */
    private val laterSeed = Seed(seed.exercises + examplePress, seed.plans)

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
        // Curls keeps its key, Leg Press stays deleted, and the library's later exercises arrive.
        val keys = database.keys()
        assertEquals("dumbbell-curls", keys[1])
        assertEquals(null, keys[12])
        assertEquals(seed.exercises.size - 1, keys.size)
        assertEquals(seed.weightlessKeys.size.toLong(), database.number("SELECT COUNT(*) FROM exercises WHERE usesWeight = 0"))
        assertEquals(seed.exercises.size.toLong(), database.number("SELECT COUNT(*) FROM exercises"))
        assertEquals(seed.exercises.size.toLong(), database.number("SELECT COUNT(*) FROM exercise_library"))
        // The exercise people made stays theirs.
        assertEquals(
            1,
            database.number("SELECT COUNT(*) FROM exercises WHERE name = 'Example Press' AND libraryKey IS NULL"),
        )
        database.close()

        // Opening it again changes nothing.
        val again = open()
        assertEquals(keys, again.keys())
        assertEquals(seed.exercises.size.toLong(), again.number("SELECT COUNT(*) FROM exercises"))
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
        assertEquals(seed.weightlessKeys, database.weightless())
        assertEquals(1, database.number("SELECT COUNT(*) FROM exercises WHERE name = 'Weighted Dips' AND usesWeight = 1"))
        // The set keeps its 20 kg, which Kitae leaves out while leg raises don't use weight.
        assertEquals(1, database.number("SELECT COUNT(*) FROM sets WHERE exerciseId = 20 AND weight = 20"))
        database.close()
    }

    @Test
    fun exercisesALaterVersionBringsArriveOnce() = runBlocking {
        open().close()
        val database = open(laterSeed)
        val (id, name, steps) = database.rows("SELECT id, name, instructions FROM exercises WHERE libraryKey = 'example-press'") {
            Triple(it.getLong(0), it.getText(1), it.getText(2).lines().size)
        }.single()
        assertEquals(seed.exercises.size + 1L, id)
        assertEquals("Example Press", name)
        assertEquals(2, steps)
        // Once deleted, it doesn't come back.
        database.change("DELETE FROM exercises WHERE id = $id")
        database.close()
        val again = open(laterSeed)
        assertEquals(0, again.number("SELECT COUNT(*) FROM exercises WHERE name = 'Example Press'"))
        again.close()
    }

    @Test
    fun anExerciseWithItsNameBecomesKitaes() = runBlocking {
        val database = open()
        database.change("INSERT INTO exercises (name, target, isIsometric) VALUES ('example press', 'Shoulders', 0)")
        database.close()
        val again = open(laterSeed)
        assertEquals(
            listOf("example press"),
            again.texts("SELECT name FROM exercises WHERE libraryKey = 'example-press'"),
        )
        assertEquals(seed.exercises.size + 1L, again.number("SELECT COUNT(*) FROM exercises"))
        again.close()
    }

    private companion object {
        const val VERSION_10_HASH = "a8dd8c0661148678f11cce771546f37e"
    }
}
