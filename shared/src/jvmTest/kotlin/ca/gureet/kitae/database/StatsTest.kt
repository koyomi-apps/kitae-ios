// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The You tab's totals and grid, as the iPhone's `ActivityWeeksTests` and `StatsTests` check them. */
class StatsTest : DatabaseFileTest() {
    @Test
    fun weekdaysOfEpochDays() {
        assertEquals(5, EpochDay.weekday(0)) // Thursday 1 January 1970
        assertEquals(1, EpochDay.weekday(3)) // Sunday
        assertEquals(4, EpochDay.weekday(-1)) // Wednesday 31 December 1969
        assertEquals(7, EpochDay.weekday(TODAY))
        assertEquals(2, EpochDay.weekday(TODAY + 2))
    }

    @Test
    fun weeksStartingOnMondayEndWithThisWeek() {
        val weeks = ActivityWeeks(TODAY, firstWeekday = 2)
        // Monday 22 June to Sunday 11 October, as in the design.
        assertEquals(TODAY - 110, weeks.first)
        assertEquals(2, EpochDay.weekday(weeks.first))
        assertEquals(TODAY + 1, weeks.last)
        assertEquals(ActivityWeeks.COUNT * 7L, weeks.last - weeks.first + 1)
        assertEquals(TODAY, weeks.day(week = 15, row = 5))
        assertEquals(weeks.first, weeks.day(week = 0, row = 0))
    }

    @Test
    fun weeksStartingOnSundayEndToday() {
        val weeks = ActivityWeeks(TODAY, firstWeekday = 1)
        assertEquals(TODAY - 111, weeks.first)
        assertEquals(1, EpochDay.weekday(weeks.first))
        assertEquals(TODAY, weeks.last)
        assertEquals(TODAY, weeks.day(week = 15, row = 6))
    }

    @Test
    fun onlyDaysInTheGridUpToTodayAreCounted() {
        val weeks = ActivityWeeks(
            TODAY,
            firstWeekday = 2,
            trained = setOf(TODAY - 200, TODAY - 110, TODAY - 3, TODAY, TODAY + 1),
        )
        assertEquals(3, weeks.trainedCount)
    }

    @Test
    fun aNewDatabaseHasOnlyExercises() = runBlocking {
        val database = openFollowingStarterPlan()
        assertEquals(TrainingTotals(lifts = 0, days = 0, exercises = bundledSeed.exercises.size), database.stats().totals())
        assertTrue(database.stats().activity(TODAY, firstWeekday = 2).trained.isEmpty())
        database.close()
    }

    @Test
    fun countsDoneSetsAndTheDaysTheyWereDone() = runBlocking {
        val database = openFollowingStarterPlan()
        val plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
        // Long ago, before the grid.
        database.workout(plan.days[0], on = TODAY - 120, done = 3)
        // Two workouts on the same day count as one day.
        database.workout(plan.days[0], on = TODAY - 3, done = 2)
        database.workout(plan.days[1], on = TODAY - 3, done = 1)
        // A workout with nothing done isn't a day trained.
        database.workout(plan.days[2], on = TODAY - 1, done = 0)
        // Sets done in the workout in progress count straight away.
        database.workout(plan.days[3], on = TODAY, done = 1, finish = null)

        val totals = database.stats().totals()
        assertEquals(7, totals.lifts)
        assertEquals(3, totals.days)
        assertEquals(bundledSeed.exercises.size, totals.exercises)

        val weeks = database.stats().activity(TODAY, firstWeekday = 2)
        assertEquals(setOf(TODAY - 3, TODAY), weeks.trained)
        assertEquals(2, weeks.trainedCount)

        // The sets of each exercise done in the last few days, warm-ups left out.
        val counts = database.stats().exerciseSets(TODAY - 3, TODAY).associate { it.exercise.id to it.sets }
        assertEquals(4, counts.values.sum())
        database.close()
    }
}
