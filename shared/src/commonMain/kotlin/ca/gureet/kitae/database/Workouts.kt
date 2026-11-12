// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * Workouts and their sets, read and written as the iPhone does (`Sessions.swift`), which does
 * it the way Kitae for Android does. A workout is a session in the database. Dates are in days
 * since 1970 where the phone is, and times in milliseconds since 1970.
 */
@Dao
abstract class Workouts(private val database: KitaeDatabase) {
    // Starting

    /**
     * Starts a workout of a day, with the day's planned sets not done yet, and returns its id. A
     * workout of the same day that was started [today] and isn't finished carries on instead.
     */
    @Transaction
    open suspend fun start(dayId: Long, today: Long, now: Long): Long {
        unfinished(dayId, today)?.let { return it }
        val planId = planOfDay(dayId) ?: throw IllegalArgumentException("There's no day $dayId.")
        val workoutId = insertWorkout(
            Workout(date = today, planId = planId, dayId = dayId, isFinished = false, startedAt = now),
        )
        plannedSetsToStart(dayId).forEachIndexed { order, planned ->
            insertSet(
                WorkoutSet(
                    reps = planned.set.reps,
                    weightKg = if (planned.usesWeight) planned.set.weightKg else 0.0,
                    type = planned.set.type,
                    order = order,
                    workoutId = workoutId,
                    exerciseId = planned.exerciseId,
                    rir = DEFAULT_RIR,
                    isDone = false,
                    plannedSetId = planned.set.id,
                    restSeconds = planned.set.restSeconds,
                ),
            )
        }
        return workoutId
    }

    // Reading

    /**
     * The newest workout that isn't finished, if it was started [today] or the day before, so a
     * workout that went past midnight still counts.
     */
    @Query(
        """
        SELECT `sessions`.*, `routines`.`name` AS `dayName`
        FROM `sessions`
        LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
        WHERE `sessions`.`date` >= :today - 1
        AND `sessions`.`isFinished` = 0
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC
        LIMIT 1
        """,
    )
    abstract suspend fun active(today: Long): WorkoutWithDay?

    @Query(
        """
        SELECT `sessions`.*, `routines`.`name` AS `dayName`
        FROM `sessions`
        LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
        WHERE `sessions`.`id` = :id
        """,
    )
    abstract suspend fun workout(id: Long): WorkoutWithDay?

    /** The day of the newest workout that had something done. */
    @Query(
        """
        SELECT `routineId`
        FROM `sessions`
        WHERE `routineId` IS NOT NULL
        AND EXISTS
        (SELECT 1
        FROM `sets`
        WHERE `sets`.`sessionId` = `sessions`.`id`
        AND `sets`.`isCompleted` = 1)
        ORDER BY `date` DESC, `id` DESC
        LIMIT 1
        """,
    )
    abstract suspend fun lastDoneDayId(): Long?

    /** Whether any set was ever done, so Today can tell a first workout apart. */
    @Query("SELECT EXISTS (SELECT 1 FROM `sets` WHERE `isCompleted` = 1)")
    abstract suspend fun hasDoneSets(): Boolean

    /**
     * A workout's exercises and sets. While it's going on, the exercises of its day ([dayId])
     * come first in the plan's order, even ones without sets, then any others in the order
     * they were added.
     */
    @Transaction
    open suspend fun exercises(workoutId: Long, dayId: Long?): List<WorkoutExercise> {
        val exercises = LinkedHashMap<Long, Exercise>()
        if (dayId != null) {
            for (exercise in exercisesOfDay(dayId)) exercises.getOrPut(exercise.id) { exercise }
        }
        val withSets = exercisesOfWorkout(workoutId).associateBy { it.id }
        val sets = HashMap<Long, MutableList<WorkoutSet>>()
        for (set in setsOfWorkout(workoutId)) {
            val exercise = withSets[set.exerciseId] ?: continue
            exercises.getOrPut(exercise.id) { exercise }
            sets.getOrPut(exercise.id) { mutableListOf() }.add(set)
        }
        return exercises.values.map { WorkoutExercise(it, sets[it.id].orEmpty()) }
    }

    /**
     * What a new set of an exercise starts as when there's nothing to copy in the workout: the
     * day's last planned set of it, or the last set of it ever done, or null.
     */
    @Transaction
    open suspend fun lastKnownSet(exerciseId: Long, dayId: Long?): SetDraft? {
        if (dayId != null) {
            lastPlannedSet(dayId, exerciseId)?.let { return SetDraft(it.reps, it.weightKg, it.setType, it.restSeconds) }
        }
        return lastDoneSet(exerciseId)?.let { SetDraft(it.reps, it.weightKg, it.setType, it.restSeconds) }
    }

    // Changing sets

    @Query("UPDATE `sets` SET `isCompleted` = :isDone WHERE `id` = :setId")
    abstract suspend fun setDone(setId: Long, isDone: Boolean)

    /** Saves a set's reps, weight, type and rest, keeping its exercise's warm-ups first. */
    @Transaction
    open suspend fun update(setId: Long, set: SetDraft) {
        updateSet(setId, set.reps, set.weightKg, set.type.stored, set.restSeconds)
        setRow(setId)?.let { groupWarmups(it.workoutId, it.exerciseId) }
    }

    /**
     * Adds a set that isn't done yet after the workout's other sets, without weight if the
     * exercise doesn't use any, and returns its id.
     */
    @Transaction
    open suspend fun addSet(workoutId: Long, exerciseId: Long, set: SetDraft): Long {
        val usesWeight = usesWeight(exerciseId) ?: true
        val setId = insertSetAtEnd(
            workoutId = workoutId,
            exerciseId = exerciseId,
            reps = set.reps,
            weightKg = if (usesWeight) set.weightKg else 0.0,
            type = set.type.stored,
            restSeconds = set.restSeconds,
        )
        groupWarmups(workoutId, exerciseId)
        return setId
    }

    @Query("DELETE FROM `sets` WHERE `id` = :setId")
    abstract suspend fun deleteSet(setId: Long)

    // Finishing

    /**
     * Finishes a workout: what was done goes back into the day's plan (with [addNewSets], the
     * sets added during the workout too), and the sets that weren't done are kept or removed as
     * [mode] says. A workout left with no sets is removed.
     */
    @Transaction
    open suspend fun finish(workoutId: Long, mode: FinishMode, addNewSets: Boolean, now: Long) {
        database.plans().applyWorkout(workoutId, addNewSets)
        if (mode != FinishMode.KeepSkipped) {
            if (mode == FinishMode.RemoveFromPlan) {
                val dayExercises = dayExercisesNotDone(workoutId)
                deletePlannedSetsNotDone(workoutId)
                if (dayExercises.isNotEmpty()) deleteEmptyDayExercises(dayExercises)
            }
            deleteSetsNotDone(workoutId)
        }
        markFinished(workoutId, now)
        deleteIfEmpty(workoutId)
    }

    /** Removes a workout and all its sets. The plan stays as it was. */
    @Query("DELETE FROM `sessions` WHERE `id` = :workoutId")
    abstract suspend fun discard(workoutId: Long)

    /** Moves an exercise's warm-ups in front of its other sets in a workout. */
    private suspend fun groupWarmups(workoutId: Long, exerciseId: Long) {
        val moves = warmupsFirst(setsOfExercise(workoutId, exerciseId), { it.order }, { it.setType == SetType.Warmup })
        for ((set, order) in moves) setOrder(set.id, order)
    }

    @Query(
        """
        SELECT `id`
        FROM `sessions`
        WHERE `date` = :today
        AND `routineId` = :dayId
        AND `isFinished` = 0
        ORDER BY `id` DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun unfinished(dayId: Long, today: Long): Long?

    @Query("SELECT `planId` FROM `routines` WHERE `id` = :dayId")
    protected abstract suspend fun planOfDay(dayId: Long): Long?

    @Insert
    protected abstract suspend fun insertWorkout(workout: Workout): Long

    @Insert
    protected abstract suspend fun insertSet(set: WorkoutSet): Long

    @Query(
        """
        SELECT `routine_sets`.*, `routine_exercises`.`exerciseId`, `exercises`.`usesWeight`
        FROM `routine_sets`
        INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
        INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
        WHERE `routine_exercises`.`routineId` = :dayId
        ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
        `routine_sets`.`position` ASC, `routine_sets`.`id` ASC
        """,
    )
    protected abstract suspend fun plannedSetsToStart(dayId: Long): List<PlannedSetToStart>

    @Query(
        """
        SELECT `exercises`.*
        FROM `routine_exercises`
        INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
        WHERE `routine_exercises`.`routineId` = :dayId
        ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC
        """,
    )
    protected abstract suspend fun exercisesOfDay(dayId: Long): List<Exercise>

    @Query("SELECT * FROM `exercises` WHERE `id` IN (SELECT `exerciseId` FROM `sets` WHERE `sessionId` = :workoutId)")
    protected abstract suspend fun exercisesOfWorkout(workoutId: Long): List<Exercise>

    @Query("SELECT * FROM `sets` WHERE `sessionId` = :workoutId ORDER BY `order` ASC, `id` ASC")
    protected abstract suspend fun setsOfWorkout(workoutId: Long): List<WorkoutSet>

    @Query(
        """
        SELECT `routine_sets`.*
        FROM `routine_sets`
        INNER JOIN `routine_exercises` ON `routine_exercises`.`id` = `routine_sets`.`routineExerciseId`
        WHERE `routine_exercises`.`routineId` = :dayId
        AND `routine_exercises`.`exerciseId` = :exerciseId
        ORDER BY `routine_exercises`.`position` ASC, `routine_exercises`.`id` ASC,
        `routine_sets`.`position` DESC, `routine_sets`.`id` DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun lastPlannedSet(dayId: Long, exerciseId: Long): PlannedSet?

    @Query(
        """
        SELECT `sets`.*
        FROM `sets`
        INNER JOIN `sessions` ON `sets`.`sessionId` = `sessions`.`id`
        WHERE `sets`.`exerciseId` = :exerciseId
        AND `sets`.`isCompleted` = 1
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` DESC, `sets`.`id` DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun lastDoneSet(exerciseId: Long): WorkoutSet?

    @Query(
        """
        UPDATE `sets`
        SET `reps` = :reps, `weight` = :weightKg, `type` = :type, `restSeconds` = :restSeconds
        WHERE `id` = :setId
        """,
    )
    protected abstract suspend fun updateSet(setId: Long, reps: Int, weightKg: Double, type: String, restSeconds: Int?)

    @Query("SELECT * FROM `sets` WHERE `id` = :setId")
    protected abstract suspend fun setRow(setId: Long): WorkoutSet?

    @Query("SELECT `usesWeight` FROM `exercises` WHERE `id` = :exerciseId")
    protected abstract suspend fun usesWeight(exerciseId: Long): Boolean?

    @Query(
        """
        INSERT INTO `sets` (`reps`, `weight`, `type`, `order`, `sessionId`, `exerciseId`, `rir`, `isCompleted`, `restSeconds`)
        VALUES (:reps, :weightKg, :type, (SELECT COALESCE(MAX(`order`), -1) + 1 FROM `sets` WHERE `sessionId` = :workoutId),
        :workoutId, :exerciseId, $DEFAULT_RIR, 0, :restSeconds)
        """,
    )
    protected abstract suspend fun insertSetAtEnd(
        workoutId: Long,
        exerciseId: Long,
        reps: Int,
        weightKg: Double,
        type: String,
        restSeconds: Int?,
    ): Long

    @Query(
        """
        SELECT * FROM `sets`
        WHERE `sessionId` = :workoutId
        AND `exerciseId` = :exerciseId
        ORDER BY `order` ASC, `id` ASC
        """,
    )
    protected abstract suspend fun setsOfExercise(workoutId: Long, exerciseId: Long): List<WorkoutSet>

    @Query("UPDATE `sets` SET `order` = :order WHERE `id` = :setId")
    protected abstract suspend fun setOrder(setId: Long, order: Int)

    @Query(
        """
        SELECT DISTINCT `routine_sets`.`routineExerciseId`
        FROM `routine_sets`
        WHERE `routine_sets`.`id` IN
        (SELECT `routineSetId`
        FROM `sets`
        WHERE `sessionId` = :workoutId
        AND `isCompleted` = 0)
        """,
    )
    protected abstract suspend fun dayExercisesNotDone(workoutId: Long): List<Long>

    @Query(
        """
        DELETE FROM `routine_sets`
        WHERE `id` IN
        (SELECT `routineSetId`
        FROM `sets`
        WHERE `sessionId` = :workoutId
        AND `isCompleted` = 0
        AND `routineSetId` IS NOT NULL)
        """,
    )
    protected abstract suspend fun deletePlannedSetsNotDone(workoutId: Long)

    @Query(
        """
        DELETE FROM `routine_exercises`
        WHERE `id` IN (:dayExerciseIds)
        AND NOT EXISTS
        (SELECT 1
        FROM `routine_sets`
        WHERE `routine_sets`.`routineExerciseId` = `routine_exercises`.`id`)
        """,
    )
    protected abstract suspend fun deleteEmptyDayExercises(dayExerciseIds: List<Long>)

    @Query("DELETE FROM `sets` WHERE `sessionId` = :workoutId AND `isCompleted` = 0")
    protected abstract suspend fun deleteSetsNotDone(workoutId: Long)

    @Query(
        """
        UPDATE `sessions`
        SET `isFinished` = 1, `finishedAt` = COALESCE(`finishedAt`, :now)
        WHERE `id` = :workoutId
        """,
    )
    protected abstract suspend fun markFinished(workoutId: Long, now: Long)

    @Query(
        """
        DELETE FROM `sessions`
        WHERE `id` = :workoutId
        AND NOT EXISTS
        (SELECT 1
        FROM `sets`
        WHERE `sets`.`sessionId` = :workoutId)
        """,
    )
    protected abstract suspend fun deleteIfEmpty(workoutId: Long)

    companion object {
        /** Reps in reserve, which Kitae for Android stores with each set but doesn't ask for yet. */
        const val DEFAULT_RIR = 2
    }
}

/** How Finish treats the sets that weren't done. */
enum class FinishMode {
    /** They stay in the workout as not done, and the plan stays the same. */
    KeepSkipped,

    /**
     * They're removed from the workout and from the day's plan, along with exercises that had
     * nothing done.
     */
    RemoveFromPlan,

    /** They're removed from this workout only. */
    Discard,
}

/** A workout with its day's name (the iPhone's `ActiveSession`). */
data class WorkoutWithDay(
    @Embedded val workout: Workout,
    /** Null when the day has been deleted. */
    val dayName: String?,
)

/** An exercise of a workout with its sets in order. */
data class WorkoutExercise(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
)

/** A planned set as a workout starts with it. */
data class PlannedSetToStart(
    @Embedded val set: PlannedSet,
    @ColumnInfo(name = "exerciseId") val exerciseId: Long,
    @ColumnInfo(name = "usesWeight") val usesWeight: Boolean,
)
