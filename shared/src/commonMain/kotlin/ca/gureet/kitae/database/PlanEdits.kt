// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * Changing plans, as the iPhone's plan editor does (`PlanEdits.swift`), which does it the way
 * Kitae for Android's does. Days are routines in the database, and an exercise's place in a day
 * is a routine exercise.
 */
@Dao
abstract class PlanEdits(private val database: KitaeDatabase) {
    // Plans

    /** Makes a plan without days and returns its id. Plan names are unique. */
    @Transaction
    open suspend fun create(name: String): Long = insertPlan(usableName(name, excludingPlanId = null))

    @Transaction
    open suspend fun rename(planId: Long, name: String) {
        updatePlanName(planId, usableName(name, excludingPlanId = planId))
    }

    /** Removes a plan and its days. Past workouts stay, without their plan. */
    @Query("DELETE FROM `plans` WHERE `id` = :planId")
    abstract suspend fun delete(planId: Long)

    @Query(
        """
        SELECT EXISTS
        (SELECT 1
        FROM `routine_exercises`
        INNER JOIN `routines` ON `routines`.`id` = `routine_exercises`.`routineId`
        WHERE `routines`.`planId` = :planId)
        """,
    )
    abstract suspend fun hasExercises(planId: Long): Boolean

    // Days

    /** Adds a day at the end of a plan and returns its id. */
    @Transaction
    open suspend fun addDay(planId: Long, name: String): Long = insertDayAtEnd(planId, dayName(name))

    @Transaction
    open suspend fun renameDay(dayId: Long, name: String) {
        updateDayName(dayId, dayName(name))
    }

    /** Removes a day with its exercises and planned sets. Past workouts of it stay. */
    @Query("DELETE FROM `routines` WHERE `id` = :dayId")
    abstract suspend fun deleteDay(dayId: Long)

    /** Puts days in this order. */
    @Transaction
    open suspend fun orderDays(dayIds: List<Long>) {
        dayIds.forEachIndexed { position, id -> setDayPosition(id, position) }
    }

    // Exercises in a day

    /** Adds an exercise at the end of a day with these sets, and returns its place in the day. */
    @Transaction
    open suspend fun addExercise(dayId: Long, exerciseId: Long, sets: List<SetDraft>): Long {
        val dayExerciseId = insertDayExerciseAtEnd(dayId, exerciseId)
        sets.forEachIndexed { position, set -> insertSet(dayExerciseId, set, position) }
        return dayExerciseId
    }

    /** Takes an exercise out of a day, with its planned sets. */
    @Query("DELETE FROM `routine_exercises` WHERE `id` = :dayExerciseId")
    abstract suspend fun removeExercise(dayExerciseId: Long)

    /** Puts a day's exercises in this order. */
    @Transaction
    open suspend fun orderExercises(dayExerciseIds: List<Long>) {
        dayExerciseIds.forEachIndexed { position, id -> setDayExercisePosition(id, position) }
    }

    /**
     * What an exercise starts with when it's added to a day: the sets done the last time it was
     * trained, or three of [fallback].
     */
    @Transaction
    open suspend fun suggestedSets(exerciseId: Long, fallback: SetDraft): List<SetDraft> {
        val done = setsDoneLastTime(exerciseId)
        if (done.isEmpty()) return List(3) { fallback }
        return done.map { SetDraft(it.reps, it.weightKg, it.setType, it.restSeconds) }
    }

    // Planned sets

    /** Adds a set after an exercise's other planned sets, keeping warm-ups first, and returns its id. */
    @Transaction
    open suspend fun addSet(dayExerciseId: Long, set: SetDraft): Long {
        val id = insertSet(dayExerciseId, set, nextSetPosition(dayExerciseId))
        database.plans().groupWarmups(dayExerciseId)
        return id
    }

    /** Saves a planned set's reps, weight, type and rest, keeping warm-ups first. */
    @Transaction
    open suspend fun updateSet(plannedSetId: Long, set: SetDraft) {
        updatePlannedSet(plannedSetId, set.reps, set.weightKg, set.type.stored, set.restSeconds)
        dayExerciseOfSet(plannedSetId)?.let { database.plans().groupWarmups(it) }
    }

    @Query("DELETE FROM `routine_sets` WHERE `id` = :plannedSetId")
    abstract suspend fun deleteSet(plannedSetId: Long)

    /** The name, trimmed, if no other plan has it. */
    private suspend fun usableName(name: String, excludingPlanId: Long?): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw NameProblemException(NameProblem.Empty)
        if (planNamed(trimmed, excludingPlanId)) throw NameProblemException(NameProblem.Taken)
        return trimmed
    }

    private fun dayName(name: String): String =
        name.trim().ifEmpty { throw NameProblemException(NameProblem.Empty) }

    /** Plans a set, without weight if the exercise doesn't use any. */
    private suspend fun insertSet(dayExerciseId: Long, set: SetDraft, position: Int): Long {
        val usesWeight = usesWeight(dayExerciseId) ?: true
        return insertPlannedSet(
            PlannedSet(
                dayExerciseId = dayExerciseId,
                reps = set.reps,
                weightKg = if (usesWeight) set.weightKg else 0.0,
                type = set.type.stored,
                position = position,
                restSeconds = set.restSeconds,
            ),
        )
    }

    @Query("SELECT EXISTS (SELECT 1 FROM `plans` WHERE `name` = :name AND `id` IS NOT :excludingPlanId)")
    protected abstract suspend fun planNamed(name: String, excludingPlanId: Long?): Boolean

    @Query("INSERT INTO `plans` (`name`) VALUES (:name)")
    protected abstract suspend fun insertPlan(name: String): Long

    @Query("UPDATE `plans` SET `name` = :name WHERE `id` = :planId")
    protected abstract suspend fun updatePlanName(planId: Long, name: String)

    @Query(
        """
        INSERT INTO `routines` (`planId`, `name`, `position`)
        VALUES (:planId, :name, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routines` WHERE `planId` = :planId))
        """,
    )
    protected abstract suspend fun insertDayAtEnd(planId: Long, name: String): Long

    @Query("UPDATE `routines` SET `name` = :name WHERE `id` = :dayId")
    protected abstract suspend fun updateDayName(dayId: Long, name: String)

    @Query("UPDATE `routines` SET `position` = :position WHERE `id` = :dayId")
    protected abstract suspend fun setDayPosition(dayId: Long, position: Int)

    @Query(
        """
        INSERT INTO `routine_exercises` (`routineId`, `exerciseId`, `position`)
        VALUES (:dayId, :exerciseId, (SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_exercises` WHERE `routineId` = :dayId))
        """,
    )
    protected abstract suspend fun insertDayExerciseAtEnd(dayId: Long, exerciseId: Long): Long

    @Query("UPDATE `routine_exercises` SET `position` = :position WHERE `id` = :dayExerciseId")
    protected abstract suspend fun setDayExercisePosition(dayExerciseId: Long, position: Int)

    @Query(
        """
        SELECT *
        FROM `sets`
        WHERE `exerciseId` = :exerciseId
        AND `isCompleted` = 1
        AND `sessionId` =
        (SELECT `sets`.`sessionId`
        FROM `sets`
        INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
        WHERE `sets`.`exerciseId` = :exerciseId
        AND `sets`.`isCompleted` = 1
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC
        LIMIT 1)
        ORDER BY `order` ASC, `id` ASC
        """,
    )
    protected abstract suspend fun setsDoneLastTime(exerciseId: Long): List<WorkoutSet>

    @Query("SELECT COALESCE(MAX(`position`), -1) + 1 FROM `routine_sets` WHERE `routineExerciseId` = :dayExerciseId")
    protected abstract suspend fun nextSetPosition(dayExerciseId: Long): Int

    @Query(
        """
        UPDATE `routine_sets`
        SET `reps` = :reps, `weight` = :weightKg, `type` = :type, `restSeconds` = :restSeconds
        WHERE `id` = :plannedSetId
        """,
    )
    protected abstract suspend fun updatePlannedSet(
        plannedSetId: Long,
        reps: Int,
        weightKg: Double,
        type: String,
        restSeconds: Int?,
    )

    @Query("SELECT `routineExerciseId` FROM `routine_sets` WHERE `id` = :plannedSetId")
    protected abstract suspend fun dayExerciseOfSet(plannedSetId: Long): Long?

    @Query(
        """
        SELECT `exercises`.`usesWeight`
        FROM `routine_exercises`
        INNER JOIN `exercises` ON `exercises`.`id` = `routine_exercises`.`exerciseId`
        WHERE `routine_exercises`.`id` = :dayExerciseId
        """,
    )
    protected abstract suspend fun usesWeight(dayExerciseId: Long): Boolean?

    @Insert
    protected abstract suspend fun insertPlannedSet(set: PlannedSet): Long
}

/** Why a name can't be used. */
enum class NameProblem {
    Empty,

    /** Another plan has it. Plan names are unique, as in Kitae for Android. */
    Taken,

    /** Another exercise has it, as Kitae for Android checks too. */
    ExerciseTaken,
}

/** Thrown when a name can't be used, saying why. */
class NameProblemException(val problem: NameProblem) : IllegalArgumentException("The name can't be used: $problem.")
