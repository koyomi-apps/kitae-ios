// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.sqlite.SQLiteConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a new database starts with, read from the iPhone's `Seed.json`
 * (`ios/Kitae/Database/Seed.json`), which each app hands in: Kitae for Android's starter
 * exercises with their ids, the exercises Kitae's library added later, and the starter plan.
 * Databases from before library keys get their keys from it too.
 */
@Serializable
class Seed(
    val exercises: List<Exercise>,
    val plans: List<Plan>,
) {
    /** One of Kitae's exercises. */
    @Serializable
    class Exercise(
        /**
         * The starter exercises' ids, as in Kitae for Android. Exercises added to the library
         * later have none: a database gives them the next free one.
         */
        val id: Long? = null,
        /** Its library key, which never changes. */
        val key: String,
        val name: String,
        val target: String,
        val isIsometric: Boolean,
        /** False for exercises done with the body alone, like sit-ups. */
        val usesWeight: Boolean,
        val reference: String? = null,
        val restSeconds: Int? = null,
        val instructions: String? = null,
    ) {
        /** Adds it to the `exercises` table, with its id if it has one. */
        internal fun insert(connection: SQLiteConnection) {
            connection.execute(
                "INSERT INTO `exercises` (`id`, `name`, `target`, `reference`, `isIsometric`, `restSeconds`, " +
                    "`instructions`, `libraryKey`, `usesWeight`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, target, reference, isIsometric, restSeconds, instructions, key, usesWeight,
            )
        }
    }

    @Serializable
    class Plan(
        val name: String,
        val description: String? = null,
        val difficulty: String? = null,
        val focus: String? = null,
        val equipment: String? = null,
        val time: String? = null,
        val days: List<Day>,
    )

    @Serializable
    class Day(
        val name: String,
        val exercises: List<DayExercise>,
    )

    @Serializable
    class DayExercise(
        /** The starter exercise's id. */
        val exercise: Long,
        val sets: List<PlannedSet>,
    )

    @Serializable
    class PlannedSet(
        val reps: Int,
        val weight: Double,
        val type: String,
        val restSeconds: Int? = null,
    )

    /** The starter exercises' library keys, by id. */
    val starterKeys: Map<Long, String>
        get() = exercises
            .mapNotNull { exercise -> exercise.id?.let { it to exercise.key } }
            .distinctBy { it.first }
            .toMap()

    /** The library keys of the exercises that don't use weight. */
    val weightlessKeys: Set<String>
        get() = exercises.filterNot { it.usesWeight }.map { it.key }.toSet()

    /**
     * Adds the starter exercises and plan to a new database. The library's later exercises come
     * with [ExerciseLibrary.sync].
     */
    internal fun insert(connection: SQLiteConnection) {
        for (exercise in exercises) {
            if (exercise.id != null) exercise.insert(connection)
        }
        // Kitae for Android's starter plan gives every set 20 kg, leg raises and pull-ups too.
        val weightless = exercises.filterNot { it.usesWeight }.mapNotNull { it.id }.toSet()
        for (plan in plans) {
            val planId = connection.insert(
                "INSERT INTO `plans` (`name`, `description`, `difficulty`, `focus`, `equipment`, `time`) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                plan.name, plan.description, plan.difficulty, plan.focus, plan.equipment, plan.time,
            )
            plan.days.forEachIndexed { dayPosition, day ->
                val dayId = connection.insert(
                    "INSERT INTO `routines` (`planId`, `name`, `position`) VALUES (?, ?, ?)",
                    planId, day.name, dayPosition,
                )
                day.exercises.forEachIndexed { exercisePosition, item ->
                    val dayExerciseId = connection.insert(
                        "INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`) VALUES (?, ?, ?)",
                        dayId, item.exercise, exercisePosition,
                    )
                    item.sets.forEachIndexed { setPosition, set ->
                        connection.execute(
                            "INSERT INTO `routine_sets` (`routineExerciseId`, `reps`, `weight`, `type`, `position`, " +
                                "`restSeconds`) VALUES (?, ?, ?, ?, ?, ?)",
                            dayExerciseId, set.reps, if (item.exercise in weightless) 0.0 else set.weight, set.type,
                            setPosition, set.restSeconds,
                        )
                    }
                }
            }
        }
    }

    companion object {
        // Like the iPhone's decoder, it skips what it doesn't know, so a later Seed.json reads.
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the text of `Seed.json`. */
        fun decode(text: String): Seed = json.decodeFromString(serializer(), text)
    }
}
