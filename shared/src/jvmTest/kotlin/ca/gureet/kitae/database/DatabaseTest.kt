// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A new database, as the iPhone's `SchemaTests`, `SeedTests` and `LibraryTests` check it. */
class DatabaseTest : DatabaseFileTest() {
    private val all = bundledSeed.exercises.size.toLong()

    @Test
    fun everySetTypeHasARating() = runBlocking {
        val database = open()
        val modifiers = database.rows("SELECT type, modifier FROM set_type") { it.getText(0) to it.getDouble(1) }.toMap()
        assertEquals(setOf("Warmup", "Standard", "Failure", "Drop", "RestPause"), modifiers.keys)
        assertEquals(1.35f.toDouble(), modifiers["Drop"])
        assertEquals(0.0, modifiers["Warmup"])
        database.close()
    }

    @Test
    fun startsWithKitaesExercises() = runBlocking {
        val database = open()
        // Kitae for Android's 75, then the library's later exercises with the next ids.
        assertEquals(all, database.number("SELECT COUNT(*) FROM exercises"))
        assertEquals(1, database.number("SELECT MIN(id) FROM exercises"))
        assertEquals(all, database.number("SELECT MAX(id) FROM exercises"))
        assertEquals(all, database.number("SELECT COUNT(*) FROM exercises WHERE instructions IS NOT NULL"))
        database.close()
    }

    @Test
    fun kitaesExercisesHaveTheirKeys() = runBlocking {
        val database = open()
        val seed = bundledSeed
        val keys = seed.exercises.map { it.key }.toSet()
        assertEquals((1L..75L).toSet(), seed.starterKeys.keys)
        assertEquals(seed.exercises.size, keys.size)
        // The starters have Kitae for Android's ids, and the library's later exercises the next ones.
        val stored = database.keys()
        assertEquals(seed.starterKeys, stored.filterKeys { it <= 75 })
        assertEquals(keys, stored.values.toSet())
        assertEquals(keys.size, stored.size)
        assertEquals("barbell-bench-press", stored[21])
        assertEquals(keys, database.texts("SELECT key FROM exercise_library").toSet())
        // Sit-ups and the other exercises done with the body alone don't use weight, and the
        // starter plan doesn't give their sets any.
        assertEquals(seed.weightlessKeys, database.weightless())
        assertTrue("sit-ups" in seed.weightlessKeys)
        assertEquals(
            0,
            database.number(
                """
                SELECT COUNT(*) FROM routine_sets
                JOIN routine_exercises ON routine_exercises.id = routine_sets.routineExerciseId
                JOIN exercises ON exercises.id = routine_exercises.exerciseId
                WHERE exercises.usesWeight = 0 AND routine_sets.weight > 0
                """.trimIndent(),
            ),
        )
        database.close()
    }

    @Test
    fun startsWithTheStarterPlanNotYetFollowed() = runBlocking {
        val database = open()
        assertEquals(listOf("Push Pull Leg"), database.texts("SELECT name FROM plans"))
        assertEquals(
            bundledSeed.plans.single().days.map { it.name },
            database.texts("SELECT name FROM routines ORDER BY position"),
        )
        assertEquals(
            bundledSeed.plans.single().days.sumOf { day -> day.exercises.sumOf { it.sets.size } }.toLong(),
            database.number("SELECT COUNT(*) FROM routine_sets"),
        )
        assertEquals(0, database.number("SELECT COUNT(*) FROM plan_history"))
        database.close()
    }

    @Test
    fun hasKitaesOwnTables() = runBlocking {
        val database = open()
        assertEquals(
            setOf("body_measurements", "index_body_measurements_date", "body_profile", "exercise_notes", "exercise_library"),
            database.texts(
                "SELECT name FROM sqlite_master WHERE name IN ('body_measurements', 'index_body_measurements_date', " +
                    "'body_profile', 'exercise_notes', 'exercise_library')",
            ).toSet(),
        )
        database.close()
    }

    @Test
    fun opensItsOwnDatabaseAgain() = runBlocking {
        open().close()
        val again = open()
        assertEquals(all, again.number("SELECT COUNT(*) FROM exercises"))
        assertEquals(1, again.number("SELECT COUNT(*) FROM plans"))
        again.close()
    }
}
