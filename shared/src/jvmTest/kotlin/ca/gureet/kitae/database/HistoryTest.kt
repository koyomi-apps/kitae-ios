// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import ca.gureet.kitae.model.doneSets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Past workouts, as the iPhone's `HistoryTests` check them. */
class HistoryTest : DatabaseFileTest() {
    private lateinit var database: KitaeDatabase
    private lateinit var plan: PlanWithDays

    @BeforeTest
    fun openDatabase() = runBlocking {
        database = openFollowingStarterPlan()
        plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun listsDoneAndSkippedWorkoutsNewestFirst() = runBlocking {
        val done = database.workout(plan.days[0], on = TODAY - 3, done = 2, minutes = 48)
        val skipped = database.workout(plan.days[1], on = TODAY - 2, done = 0)
        // Discarding a workout with nothing done removes it.
        database.workout(plan.days[2], on = TODAY - 1, done = 0, finish = FinishMode.Discard)
        // A workout left unfinished days ago with nothing done isn't listed, like on Android.
        database.workout(plan.days[2], on = TODAY - 5, done = 0, finish = null)
        val active = database.workout(plan.days[3], on = TODAY, done = 0, finish = null)

        assertEquals(listOf(skipped, done), database.history().workouts(activeId = null).map { it.workout.id })
        val list = database.history().workouts(activeId = active)
        assertEquals(listOf(active, skipped, done), list.map { it.workout.id })

        val first = list[2]
        assertEquals("Push A", first.dayName)
        assertEquals(TODAY - 3, first.workout.date)
        assertEquals(2, first.doneCount)
        assertEquals(15, first.setCount)
        assertEquals(48, first.minutes)
        assertFalse(first.isSkipped)

        assertTrue(list[1].isSkipped)
        assertEquals("Pull A", list[1].dayName)
        assertFalse(list[0].workout.isFinished)
    }

    @Test
    fun lengthsRoundLikeAndroid() {
        assertEquals(1, workoutMinutes(0))
        assertEquals(1, workoutMinutes(89_999))
        assertEquals(2, workoutMinutes(90_000))
        assertEquals(48, workoutMinutes(48 * 60_000L + 29_000))
        assertNull(summary(1, TODAY, done = 12, minutes = null).minutes)
    }

    @Test
    fun monthsCountTheWorkoutsDone() {
        val workouts = listOf(
            summary(5, 20_733, done = 6, minutes = null, finished = false),
            summary(4, 20_731, done = 15, minutes = 48),
            summary(3, 20_729, done = 0, minutes = 50),
            summary(2, 20_728, done = 14, minutes = 61),
            summary(1, 20_726, done = 16, minutes = 55),
        )
        val months = historyMonths(workouts, activeId = 5)
        assertEquals(listOf(10, 9), months.map { it.month })
        assertEquals(listOf(2026, 2026), months.map { it.year })
        assertEquals(listOf(5L, 4, 3, 2), months[0].workouts.map { it.workout.id })
        assertEquals(2 to 109, months[0].doneCount to months[0].minutes)
        assertEquals(1 to 55, months[1].doneCount to months[1].minutes)
        assertEquals(1 to 0, historyMonths(listOf(summary(9, 20_700, done = 3, minutes = null)), null).single().let { it.doneCount to it.minutes })
    }

    @Test
    fun previousWorkoutIsOfTheSameDay() = runBlocking {
        val pushA = plan.days[0]
        val older = database.workout(pushA, on = TODAY - 10, done = 1)
        // A skipped workout doesn't count as the one before.
        database.workout(pushA, on = TODAY - 6, done = 0)
        val newer = database.workout(pushA, on = TODAY - 3, done = 3)
        database.workout(plan.days[1], on = TODAY - 2, done = 3)

        assertEquals(older, database.history().previousId(newer, pushA.day.id, TODAY - 3))
        assertNull(database.history().previousId(older, pushA.day.id, TODAY - 10))
    }

    @Test
    fun workoutsWithoutADayFollowTheWeekday() = runBlocking {
        suspend fun dayless(date: Long): Long {
            database.change("INSERT INTO sessions (date, isFinished) VALUES ($date, 1)")
            val id = database.number("SELECT MAX(id) FROM sessions")
            database.change(
                "INSERT INTO sets (reps, weight, type, `order`, sessionId, exerciseId, rir, isCompleted) " +
                    "VALUES (10, 20, 'Standard', 0, $id, 1, 2, 1)",
            )
            return id
        }
        val twoWeeksAgo = dayless(TODAY - 14)
        dayless(TODAY - 8)
        val lastWeek = dayless(TODAY - 7)
        assertEquals(twoWeeksAgo, database.history().previousId(lastWeek, dayId = null, date = TODAY - 7))

        val summary = assertNotNull(database.history().summary(lastWeek))
        assertNull(summary.dayName)
        assertNull(summary.minutes)
    }

    @Test
    fun finishedWorkoutsInARangeHaveASetDone() = runBlocking {
        val done = database.workout(plan.days[0], on = TODAY - 3, done = 2)
        database.workout(plan.days[1], on = TODAY - 2, done = 0)
        database.workout(plan.days[2], on = TODAY, done = 1, finish = null)
        database.workout(plan.days[3], on = TODAY - 9, done = 1)
        assertEquals(listOf(done), database.history().finished(TODAY - 6, TODAY).map { it.workout.id })
    }

    private fun summary(id: Long, date: Long, done: Int, minutes: Long?, finished: Boolean = true): WorkoutSummary {
        val start = 1_000_000L
        return WorkoutSummary(
            workout = Workout(
                date = date,
                planId = null,
                dayId = null,
                isFinished = finished,
                startedAt = minutes?.let { start },
                finishedAt = minutes?.let { start + it * MINUTE },
                id = id,
            ),
            dayName = null,
            setCount = 15,
            doneCount = done,
        )
    }

    @Test
    fun doneSetsComeByWorkoutForTheirEnergy() = runBlocking {
        val first = database.workout(plan.days[0], TODAY - 3, done = 2)
        val second = database.workout(plan.days[1], TODAY - 1, done = 1)
        database.workout(plan.days[2], TODAY - 9, done = 3)
        val sets = database.history().doneSets(TODAY - 7, TODAY)
        assertEquals(setOf(first, second), sets.keys)
        assertEquals(2, sets.getValue(first).size)
        // They're the workout's done sets as the workout itself has them.
        val exercises = database.workouts().exercises(first, plan.days[0].day.id)
        assertEquals(exercises.flatMap { it.doneSets }.toSet(), sets.getValue(first).toSet())
        // Exercises without weight count none.
        database.change("UPDATE exercises SET usesWeight = 0")
        assertTrue(database.history().doneSets(TODAY - 7, TODAY).values.flatten().all { it.weightKg == 0.0 })
    }
}

