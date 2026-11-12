// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.EpochDay
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSummary

/**
 * An estimate of the energy a workout used on top of resting, its active energy as Apple Health
 * counts it, so a calorie tracker can add it to the day without counting resting energy twice, as
 * the iPhone works it out (`WorkoutEnergy.swift`).
 *
 * It's worked out from the sets done, not from how long the workout took: each set by its
 * exercise, reps, weight and type (see [ExerciseEnergy]), counting the body's own weight where the
 * exercise moves it and the recovery after the set. Height, when it's known, scales how far each
 * rep moves: longer limbs move the weight further. On top comes being up and about between sets,
 * half a MET over resting, for at most three minutes a set, so a workout left running or a long
 * chat between sets adds little. Without its length, that's two and a half minutes a set.
 *
 * Sex, age and body fat aren't used: they mainly change resting energy, which active energy leaves
 * out. Scott et al. 2014 fitted men's and women's sets with one line of energy against work, the
 * weight times how far the bar moved, and found no difference between them in where that energy
 * came from.
 */
object WorkoutEnergy {
    const val BETWEEN_SETS_METS = 0.5
    const val LONGEST_MINUTES_PER_SET = 3.0
    const val USUAL_MINUTES_PER_SET = 2.5

    /** About how tall the lifters were whose sets give the costs per kilogram. */
    const val REFERENCE_HEIGHT_CM = 175.0

    /**
     * How much further than the reference a rep moves the weight for someone [heightCm] tall, as
     * limbs grow with height: 1 when the height isn't known, and from 0.8 to 1.2.
     */
    fun reach(heightCm: Double?): Double {
        if (heightCm == null || heightCm <= 0) return 1.0
        return (heightCm / REFERENCE_HEIGHT_CM).coerceIn(0.8, 1.2)
    }

    /** Active kcal for a workout's done sets. */
    fun active(sets: List<DoneSet>, durationMilliseconds: Long?, bodyKg: Double, heightCm: Double? = null): Double {
        if (sets.isEmpty() || bodyKg <= 0) return 0.0
        val scale = reach(heightCm)
        val lifting = sets.sumOf { set ->
            val energy = ExerciseEnergy.of(set.libraryKey, set.target, set.isIsometric)
            energy.kilocalories(set.reps, set.weightKg, bodyKg, scale) * set.type.energyFactor
        }
        val count = sets.size.toDouble()
        val length = durationMilliseconds?.let { it / 60_000.0 } ?: (count * USUAL_MINUTES_PER_SET)
        val minutes = minOf(length, count * LONGEST_MINUTES_PER_SET)
        return lifting + BETWEEN_SETS_METS * bodyKg * minutes / 60
    }
}

/**
 * A finished workout's active energy from its done sets, or null when Kitae can't tell: no weight
 * yet, or no set done.
 */
fun BodyLog.activeKilocalories(workout: WorkoutSummary, sets: List<DoneSet>): Double? {
    if (!workout.workout.isFinished || sets.isEmpty()) return null
    val weight = weightKg(workout.workout.date) ?: return null
    return WorkoutEnergy.active(sets, workout.durationMilliseconds, weight, profile.heightCm)
}

/**
 * The workouts of a stretch of days, their minutes, and their active energy when a weight is
 * known. [sets] are the done sets by workout.
 */
fun BodyLog.totals(workouts: List<WorkoutSummary>, sets: Map<Long, List<DoneSet>>): WorkoutTotals {
    val done = workouts.filter { it.workout.isFinished && it.doneCount > 0 }
    val energies = done.map { activeKilocalories(it, sets[it.workout.id].orEmpty()) }
    return WorkoutTotals(
        workouts = done.size,
        minutes = done.mapNotNull { it.minutes }.sum(),
        kilocalories = if (latestWeight == null) null else energies.filterNotNull().sum(),
    )
}

/** What the You tab counts for this week. */
data class WorkoutTotals(
    val workouts: Int = 0,
    val minutes: Int = 0,
    /** Null until a weight is known. */
    val kilocalories: Double? = null,
)

/** The first day of the week [today] is in, on the phone's first day of the week (1 for Sunday). */
fun startOfWeek(today: Long, firstWeekday: Int): Long = today - (EpochDay.weekday(today) - firstWeekday + 7) % 7

/** Its done sets, for the energy estimate. */
val WorkoutExercise.doneSets: List<DoneSet>
    get() = sets.filter { it.isDone }.map {
        DoneSet(
            exerciseId = exercise.id,
            libraryKey = exercise.libraryKey,
            target = exercise.target,
            isIsometric = exercise.isIsometric,
            reps = it.reps,
            weightKg = exercise.weight(it.weightKg),
            type = SetType.fromStored(it.type),
        )
    }
