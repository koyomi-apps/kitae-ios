// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * Plans and their days, read and written as the iPhone does (`Plans.swift`), which does it the
 * way Kitae for Android does. A day is a routine in the database.
 */
@Dao
abstract class Plans {
    /** Every plan with how many days and exercises it has, oldest first. */
    @Query(
        """
        SELECT `plans`.`id`, `plans`.`name`,
        (SELECT COUNT(*)
        FROM `routines`
        WHERE `routines`.`planId` = `plans`.`id`) AS `dayCount`,
        (SELECT COUNT(DISTINCT `routine_exercises`.`exerciseId`)
        FROM `routine_exercises`
        INNER JOIN `routines` ON `routines`.`id` = `routine_exercises`.`routineId`
        WHERE `routines`.`planId` = `plans`.`id`) AS `exerciseCount`
        FROM `plans`
        ORDER BY `plans`.`id` ASC
        """,
    )
    abstract suspend fun all(): List<PlanSummary>

    /** The plan being followed: the one whose plan history row hasn't ended. */
    @Query("SELECT `planId` FROM `plan_history` WHERE `end` IS NULL AND `start` IS NOT NULL")
    abstract suspend fun currentId(): Long?

    /** Ends the current plan [today] (in days since 1970) and starts following [planId]. */
    @Transaction
    open suspend fun setCurrent(planId: Long, today: Long) {
        val current = currentHistory()
        if (current != null) {
            if (current.planId == planId) return
            endHistory(current.id, today)
        }
        startHistory(PlanHistory(planId = planId, start = today))
    }

    /** A plan with its days in order, each with its exercises and planned sets. */
    @Transaction
    open suspend fun plan(id: Long): PlanWithDays? {
        val plan = planRow(id) ?: return null
        val days = days(id).map { day -> DayWithExercises(day, dayExercises(day.id)) }
        return PlanWithDays(plan, days)
    }

    /** A day's exercises in order, each with its planned sets in order. */
    @Transaction
    open suspend fun dayExercises(dayId: Long): List<DayExerciseWithSets> {
        val exercises = exercisesOfDay(dayId).associateBy { it.id }
        val sets = plannedSetsOfDay(dayId).groupBy { it.dayExerciseId }
        return dayExerciseRows(dayId).mapNotNull { slot ->
            val exercise = exercises[slot.exerciseId] ?: return@mapNotNull null
            DayExerciseWithSets(slot, exercise, sets[slot.id].orEmpty())
        }
    }

    // Taking a workout back into the plan

    /**
     * Copies what was done in a workout back into its day, so next time starts from there. With
     * [addNewSets], sets added during the workout join the day too.
     */
    @Transaction
    internal open suspend fun applyWorkout(workoutId: Long, addNewSets: Boolean) {
        copyDoneSets(workoutId)
        for (dayExerciseId in dayExercisesDone(workoutId)) groupWarmups(dayExerciseId)
        if (!addNewSets) return
        val dayId = dayOfWorkout(workoutId) ?: return
        for ((exerciseId, sets) in addedSetsDone(workoutId).groupBy { it.exerciseId }) {
            val dayExerciseId = dayExerciseFor(dayId, exerciseId)
            for (set in sets) {
                val plannedSetId = insertPlannedSetAtEnd(dayExerciseId, set.reps, set.weightKg, set.type, set.restSeconds)
                linkToPlannedSet(set.id, plannedSetId)
            }
            groupWarmups(dayExerciseId)
        }
    }

    /** Moves an exercise's planned warm-ups in front of its other sets. */
    internal suspend fun groupWarmups(dayExerciseId: Long) {
        val moves = warmupsFirst(plannedSetsOf(dayExerciseId), { it.position }, { it.setType == SetType.Warmup })
        for ((set, position) in moves) setPosition(set.id, position)
    }

    /** The exercise's first place in the day, added at the end if it isn't there yet. */
    private suspend fun dayExerciseFor(dayId: Long, exerciseId: Long): Long =
        firstDayExercise(dayId, exerciseId) ?: insertDayExerciseAtEnd(dayId, exerciseId)

    @Query(
        """
        UPDATE `routine_sets` SET
        `reps` =
        (SELECT `sets`.`reps`
        FROM `sets`
        WHERE `sets`.`routineSetId` = `routine_sets`.`id`
        AND `sets`.`sessionId` = :workoutId
        AND `sets`.`isCompleted` = 1),
        `weight` =
        (SELECT `sets`.`weight`
        FROM `sets`
        WHERE `sets`.`routineSetId` = `routine_sets`.`id`
        AND `sets`.`sessionId` = :workoutId
        AND `sets`.`isCompleted` = 1),
        `type` =
        (SELECT `sets`.`type`
        FROM `sets`
        WHERE `sets`.`routineSetId` = `routine_sets`.`id`
        AND `sets`.`sessionId` = :workoutId
        AND `sets`.`isCompleted` = 1),
        `restSeconds` =
        (SELECT `sets`.`restSeconds`
        FROM `sets`
        WHERE `sets`.`routineSetId` = `routine_sets`.`id`
        AND `sets`.`sessionId` = :workoutId
        AND `sets`.`isCompleted` = 1)
        WHERE `id` IN
        (SELECT `routineSetId`
        FROM `sets`
        WHERE `sessionId` = :workoutId
        AND `isCompleted` = 1
        AND `routineSetId` IS NOT NULL)
        """,
    )
    protected abstract suspend fun copyDoneSets(workoutId: Long)

    @Query(
        """
        SELECT DISTINCT `routine_sets`.`routineExerciseId`
        FROM `routine_sets`
        INNER JOIN `sets` ON `sets`.`routineSetId` = `routine_sets`.`id`
        WHERE `sets`.`sessionId` = :workoutId
        AND `sets`.`isCompleted` = 1
        """,
    )
    protected abstract suspend fun dayExercisesDone(workoutId: Long): List<Long>

    @Query("SELECT `routineId` FROM `sessions` WHERE `id` = :workoutId")
    protected abstract suspend fun dayOfWorkout(workoutId: Long): Long?

    @Query(
        """
        SELECT *
        FROM `sets`
        WHERE `sessionId` = :workoutId
        AND `isCompleted` = 1
        AND `routineSetId` IS NULL
        ORDER BY `order` ASC, `id` ASC
        """,
    )
    protected abstract suspend fun addedSetsDone(workoutId: Long): List<WorkoutSet>

    @Query(
        """
        SELECT `id`
        FROM `routine_exercises`
        WHERE `routineId` = :dayId
        AND `exerciseId` = :exerciseId
        ORDER BY `position` ASC, `id` ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun firstDayExercise(dayId: Long, exerciseId: Long): Long?

    @Query(
        """
        INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`)
        VALUES (:dayId, :exerciseId, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_exercises` WHERE `routineId` = :dayId))
        """,
    )
    protected abstract suspend fun insertDayExerciseAtEnd(dayId: Long, exerciseId: Long): Long

    @Query(
        """
        INSERT INTO `routine_sets` (`routineExerciseId`, `reps`, `weight`, `type`, `position`, `restSeconds`)
        VALUES (:dayExerciseId, :reps, :weightKg, :type,
        (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_sets` WHERE `routineExerciseId` = :dayExerciseId), :restSeconds)
        """,
    )
    protected abstract suspend fun insertPlannedSetAtEnd(
        dayExerciseId: Long,
        reps: Int,
        weightKg: Double,
        type: String,
        restSeconds: Int?,
    ): Long

    @Query("UPDATE `sets` SET `routineSetId` = :plannedSetId WHERE `id` = :setId")
    protected abstract suspend fun linkToPlannedSet(setId: Long, plannedSetId: Long)

    @Query("SELECT * FROM `routine_sets` WHERE `routineExerciseId` = :dayExerciseId ORDER BY `position` ASC, `id` ASC")
    protected abstract suspend fun plannedSetsOf(dayExerciseId: Long): List<PlannedSet>

    @Query("UPDATE `routine_sets` SET `position` = :position WHERE `id` = :plannedSetId")
    protected abstract suspend fun setPosition(plannedSetId: Long, position: Int)

    @Query("SELECT * FROM `plan_history` WHERE `end` IS NULL AND `start` IS NOT NULL")
    protected abstract suspend fun currentHistory(): PlanHistory?

    @Query("UPDATE `plan_history` SET `end` = :today WHERE `id` = :id")
    protected abstract suspend fun endHistory(id: Long, today: Long)

    @Insert
    protected abstract suspend fun startHistory(row: PlanHistory)

    @Query("SELECT * FROM `plans` WHERE `id` = :id")
    protected abstract suspend fun planRow(id: Long): Plan?

    @Query("SELECT * FROM `routines` WHERE `planId` = :planId ORDER BY `position` ASC, `id` ASC")
    protected abstract suspend fun days(planId: Long): List<TrainingDay>

    @Query("SELECT * FROM `routine_exercises` WHERE `routineId` = :dayId ORDER BY `position` ASC, `id` ASC")
    protected abstract suspend fun dayExerciseRows(dayId: Long): List<DayExercise>

    @Query(
        """
        SELECT `exercises`.*
        FROM `exercises`
        INNER JOIN `routine_exercises` ON `routine_exercises`.`exerciseId` = `exercises`.`id`
        WHERE `routine_exercises`.`routineId` = :dayId
        """,
    )
    protected abstract suspend fun exercisesOfDay(dayId: Long): List<Exercise>

    @Query(
        """
        SELECT `routine_sets`.*
        FROM `routine_sets`
        INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
        WHERE `routine_exercises`.`routineId` = :dayId
        ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
        `routine_sets`.`position` ASC, `routine_sets`.`id` ASC
        """,
    )
    protected abstract suspend fun plannedSetsOfDay(dayId: Long): List<PlannedSet>
}

/** A plan as the list of plans shows it. */
data class PlanSummary(
    val id: Long,
    val name: String,
    val dayCount: Int,
    val exerciseCount: Int,
)

/** A plan with its days in order, as [Plans.plan] reads it (the iPhone's `Plan`). */
data class PlanWithDays(
    val plan: Plan,
    val days: List<DayWithExercises>,
) {
    /** Different exercises across the plan's days. */
    val exerciseCount: Int
        get() = days.flatMap { day -> day.exercises.map { it.exercise.id } }.toSet().size

    /**
     * The day up next: the one after the day done last ([lastDayId]), going back to the first
     * after the last day, or the first when none is known.
     */
    fun dayAfter(lastDayId: Long?): DayWithExercises? {
        if (days.isEmpty()) return null
        val index = days.indexOfFirst { it.day.id == lastDayId }
        if (index < 0) return days.first()
        return days[(index + 1) % days.size]
    }
}

/** A training day with its exercises in order (the iPhone's `TrainingDay`). */
data class DayWithExercises(
    val day: TrainingDay,
    val exercises: List<DayExerciseWithSets>,
) {
    val setCount: Int
        get() = exercises.sumOf { it.sets.size }
}

/** An exercise of a training day, with its planned sets in order (the iPhone's `DayExercise`). */
data class DayExerciseWithSets(
    val dayExercise: DayExercise,
    val exercise: Exercise,
    val sets: List<PlannedSet>,
)
