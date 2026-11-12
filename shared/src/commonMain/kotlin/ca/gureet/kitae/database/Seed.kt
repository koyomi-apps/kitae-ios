// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

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
    )

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

    companion object {
        // Like the iPhone's decoder, it skips what it doesn't know, so a later Seed.json reads.
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the text of `Seed.json`. */
        fun decode(text: String): Seed = json.decodeFromString(serializer(), text)
    }
}
