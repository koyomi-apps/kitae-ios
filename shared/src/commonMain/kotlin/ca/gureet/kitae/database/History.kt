// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query

/**
 * Past workouts, as History and a workout's page read them on the iPhone (`History.swift`),
 * which reads them the way Kitae for Android's sessions list and session page do.
 */
@Dao
abstract class History {
    /**
     * The workouts History lists, newest first: the ones with a set done, and finished ones that
     * kept their sets as skipped. The workout in progress ([activeId]) is listed even before a
     * set is done.
     */
    open suspend fun workouts(activeId: Long?): List<WorkoutSummary> =
        allSummaries().filter {
            it.doneCount > 0 || (it.workout.isFinished && it.setCount > 0) || it.workout.id == activeId
        }

    /** Finished workouts with a set done, from day [first] to day [last], newest first. */
    open suspend fun finished(first: Long, last: Long): List<WorkoutSummary> =
        finishedSummaries(first, last).filter { it.doneCount > 0 }

    @Query("$SUMMARIES WHERE `sessions`.`id` = :id")
    abstract suspend fun summary(id: Long): WorkoutSummary?

    /**
     * The last workout before this one of the same day that had a set done. For a workout
     * without a day, the last one on the same weekday.
     */
    @Query(
        """
        SELECT `id`
        FROM `sessions`
        WHERE `id` != :workoutId
        AND (
            (:dayId IS NOT NULL
            AND `routineId` = :dayId
            AND (`date` < :date OR (`date` = :date AND `id` < :workoutId)))
            OR
            (:dayId IS NULL
            AND `routineId` IS NULL
            AND `date` < :date
            AND (:date - `date`) % 7 = 0)
        )
        AND EXISTS
        (SELECT 1
        FROM `sets`
        WHERE `sets`.`sessionId` = `sessions`.`id`
        AND `sets`.`isCompleted` = 1)
        ORDER BY `date` DESC, `id` DESC
        LIMIT 1
        """,
    )
    abstract suspend fun previousId(workoutId: Long, dayId: Long?, date: Long): Long?

    @Query("$SUMMARIES ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC")
    protected abstract suspend fun allSummaries(): List<WorkoutSummary>

    @Query(
        """
        $SUMMARIES
        WHERE `sessions`.`isFinished` = 1 AND `sessions`.`date` BETWEEN :first AND :last
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC
        """,
    )
    protected abstract suspend fun finishedSummaries(first: Long, last: Long): List<WorkoutSummary>

    /** The done sets of the workouts from [first] to [last], by workout, for their energy and muscles. */
    open suspend fun doneSets(first: Long, last: Long): Map<Long, List<DoneSet>> =
        doneSetRows(first, last).groupBy({ it.workoutId }, { it.doneSet })

    @Query(
        """
        SELECT `sets`.`sessionId` AS `workoutId`, `sets`.`reps`, `sets`.`weight`, `sets`.`type`,
        `exercises`.`id` AS `exerciseId`, `exercises`.`libraryKey`, `exercises`.`target`, `exercises`.`isIsometric`,
        `exercises`.`usesWeight`
        FROM `sets`
        JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
        JOIN `exercises` ON `exercises`.`id` = `sets`.`exerciseId`
        WHERE `sets`.`isCompleted` = 1
        AND `sessions`.`date` BETWEEN :first AND :last
        """,
    )
    protected abstract suspend fun doneSetRows(first: Long, last: Long): List<WorkoutDoneSetRow>

    private companion object {
        const val SUMMARIES = """
            SELECT `sessions`.*, `routines`.`name` AS `dayName`,
            (SELECT COUNT(*)
            FROM `sets`
            WHERE `sets`.`sessionId` = `sessions`.`id`) AS `setCount`,
            (SELECT COUNT(*)
            FROM `sets`
            WHERE `sets`.`sessionId` = `sessions`.`id`
            AND `sets`.`isCompleted` = 1) AS `doneCount`
            FROM `sessions`
            LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
        """
    }
}

/** A workout as History lists it. */
data class WorkoutSummary(
    @Embedded val workout: Workout,
    /** The day's name, while the day still exists. */
    val dayName: String?,
    val setCount: Int,
    val doneCount: Int,
) {
    /** How long it took, when both ends are known. */
    val durationMilliseconds: Long?
        get() {
            val startedAt = workout.startedAt ?: return null
            val finishedAt = workout.finishedAt ?: return null
            return maxOf(finishedAt - startedAt, 0)
        }

    val minutes: Int?
        get() = durationMilliseconds?.let(::workoutMinutes)

    /** Finished without doing a set. */
    val isSkipped: Boolean
        get() = workout.isFinished && doneCount == 0
}

/** A workout's length in whole minutes, rounded, and at least one, as Kitae for Android counts it. */
fun workoutMinutes(milliseconds: Long): Int = maxOf(((milliseconds + 30_000) / 60_000).toInt(), 1)

/** One month of History, newest first. */
data class HistoryMonth(
    val year: Int,
    /** 1 for January. */
    val month: Int,
    val workouts: List<WorkoutSummary>,
    /** Workouts with a set done, apart from the one in progress. */
    val doneCount: Int,
    /** Their minutes, for the ones whose length is known. */
    val minutes: Int,
)

/**
 * Splits [workouts] into months, keeping their order. [activeId] is the workout in progress,
 * which isn't counted yet.
 */
fun historyMonths(workouts: List<WorkoutSummary>, activeId: Long?): List<HistoryMonth> {
    val months = mutableListOf<HistoryMonth>()
    for (summary in workouts) {
        val date = EpochDay.date(summary.workout.date)
        val year = date.year
        val month = date.month.ordinal + 1
        val last = months.lastOrNull()
        if (last == null || last.year != year || last.month != month) {
            months += HistoryMonth(year, month, emptyList(), doneCount = 0, minutes = 0)
        }
        val current = months.last()
        val counts = summary.doneCount > 0 && summary.workout.id != activeId
        months[months.lastIndex] = current.copy(
            workouts = current.workouts + summary,
            doneCount = current.doneCount + if (counts) 1 else 0,
            minutes = current.minutes + if (counts) summary.minutes ?: 0 else 0,
        )
    }
    return months
}

/** A done set, as a workout's energy estimate and its muscles need it. */
data class DoneSet(
    val exerciseId: Long,
    /** The exercise's library key, when it's one of Kitae's. */
    val libraryKey: String?,
    /** The exercise's muscle group, as it's stored, like "Chest". */
    val target: String,
    val isIsometric: Boolean,
    val reps: Int,
    /** None for exercises without weight. */
    val weightKg: Double,
    val type: SetType,
)

/** A done set as [History.doneSets] reads it. */
data class WorkoutDoneSetRow(
    val workoutId: Long,
    val reps: Int,
    val weight: Double,
    val type: String,
    val exerciseId: Long,
    val libraryKey: String?,
    val target: String,
    val isIsometric: Boolean,
    val usesWeight: Boolean,
) {
    val doneSet: DoneSet
        get() = DoneSet(exerciseId, libraryKey, target, isIsometric, reps, if (usesWeight) weight else 0.0, SetType.fromStored(type))
}
