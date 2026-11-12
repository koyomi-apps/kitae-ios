// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * What the You tab counts, as the iPhone counts it (`Stats.swift`), which counts it the way
 * Kitae for Android's profile does.
 */
@Dao
abstract class Stats {
    @Transaction
    open suspend fun totals(): TrainingTotals = TrainingTotals(doneSets(), daysTrained(), exerciseCount())

    /** The days from [first] to [last] with a set done. */
    @Query(
        """
        SELECT DISTINCT `date`
        FROM `sessions`
        WHERE `date` BETWEEN :first AND :last
        AND EXISTS
        (SELECT 1 FROM `sets`
        WHERE `sets`.`sessionId` = `sessions`.`id`
        AND `sets`.`isCompleted` = 1)
        """,
    )
    abstract suspend fun trainedDays(first: Long, last: Long): List<Long>

    /** The last 16 weeks, with the days trained in them. See [ActivityWeeks]. */
    open suspend fun activity(today: Long, firstWeekday: Int): ActivityWeeks {
        val weeks = ActivityWeeks(today, firstWeekday)
        return weeks.copy(trained = trainedDays(weeks.first, weeks.last).toSet())
    }

    /**
     * Each exercise done from day [first] to day [last], with its done sets, warm-ups left out.
     * Sets done in the workout in progress count straight away.
     */
    @Query(
        """
        SELECT `exercises`.*, COUNT(*) AS `doneSets`
        FROM `sets`
        JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
        JOIN `exercises` ON `exercises`.`id` = `sets`.`exerciseId`
        WHERE `sets`.`isCompleted` = 1
        AND `sets`.`type` != 'Warmup'
        AND `sessions`.`date` BETWEEN :first AND :last
        GROUP BY `exercises`.`id`
        """,
    )
    abstract suspend fun exerciseSets(first: Long, last: Long): List<ExerciseSetCount>

    @Query("SELECT COUNT(*) FROM `sets` WHERE `isCompleted` = 1")
    protected abstract suspend fun doneSets(): Int

    @Query(
        """
        SELECT COUNT(DISTINCT `date`)
        FROM `sessions`
        WHERE EXISTS
        (SELECT 1 FROM `sets`
        WHERE `sets`.`sessionId` = `sessions`.`id`
        AND `sets`.`isCompleted` = 1)
        """,
    )
    protected abstract suspend fun daysTrained(): Int

    @Query("SELECT COUNT(*) FROM `exercises`")
    protected abstract suspend fun exerciseCount(): Int
}

/** The totals on the You tab. */
data class TrainingTotals(
    /** Sets done, in every workout. */
    val lifts: Int = 0,
    /** Days with a set done. */
    val days: Int = 0,
    val exercises: Int = 0,
)

/**
 * The last 16 weeks a day at a time, as the You tab's grid shows them: a column for each week,
 * starting on the phone's first day of the week ([firstWeekday], 1 for Sunday to 7 for
 * Saturday, like [EpochDay.weekday]), with this week last.
 */
data class ActivityWeeks(
    val today: Long,
    val firstWeekday: Int,
    /** Days with a set done. */
    val trained: Set<Long> = emptySet(),
) {
    /** The start of the oldest week. */
    val first: Long = today - (EpochDay.weekday(today) - firstWeekday + 7) % 7 - (COUNT - 1) * 7

    /** The end of this week. */
    val last: Long
        get() = first + COUNT * 7 - 1

    /** The day in [week], 0 being the oldest, and [row], 0 being the first day of the week. */
    fun day(week: Int, row: Int): Long = first + week * 7 + row

    /** Days with a set done, up to today. */
    val trainedCount: Int
        get() = trained.count { it in first..today }

    companion object {
        const val COUNT = 16
    }
}

/** An exercise and how many sets of it were done. */
data class ExerciseSetCount(
    @Embedded val exercise: Exercise,
    @ColumnInfo(name = "doneSets") val sets: Int,
)
