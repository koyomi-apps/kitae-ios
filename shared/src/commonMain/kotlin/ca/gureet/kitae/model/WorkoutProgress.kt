// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSet

/**
 * A workout's sets as its screen counts them, and the rest that ticking one off starts, as the
 * iPhone's `Workout` has them. [hasDay] is false when the workout's day has been deleted.
 */
class WorkoutProgress(val exercises: List<WorkoutExercise>, private val hasDay: Boolean = true) {
    val setCount: Int
        get() = exercises.sumOf { it.sets.size }

    val doneCount: Int
        get() = exercises.sumOf { exercise -> exercise.sets.count { it.isDone } }

    val notDoneCount: Int
        get() = setCount - doneCount

    /** Sets added during the workout and done, which Finish offers to add to the day. */
    val addedDoneCount: Int
        get() = if (!hasDay) 0 else exercises.sumOf { exercise -> exercise.sets.count { it.isAdded && it.isDone } }

    /** The first set that isn't done yet, in the order of the screen. */
    val nextSet: NextSet?
        get() {
            for (exercise in exercises) {
                exercise.sets.firstOrNull { !it.isDone }?.let { return NextSet(exercise.exercise, it) }
            }
            return null
        }

    /**
     * The rest that ticking off the set [setId] starts, in seconds: the set's own, the exercise's,
     * or [defaultRestSeconds]. Null when it starts none: when it's done already (ticking it undoes
     * it), when it's the workout's last set to do, or when rest is off.
     */
    fun restAfterTicking(setId: Long, defaultRestSeconds: Int): Int? {
        val exercise = exercises.firstOrNull { exercise -> exercise.sets.any { it.id == setId } } ?: return null
        val set = exercise.sets.first { it.id == setId }
        if (set.isDone || notDoneCount <= 1) return null
        val seconds = set.restSeconds ?: exercise.exercise.restSeconds ?: defaultRestSeconds
        return seconds.takeIf { it > 0 }
    }

    /** A set to do next, with its exercise. */
    data class NextSet(val exercise: Exercise, val set: WorkoutSet)
}
