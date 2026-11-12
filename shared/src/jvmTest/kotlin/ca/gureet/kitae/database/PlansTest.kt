// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** Plans, and which one is followed, as the iPhone's `SeedTests` and `WorkoutTests` check them. */
class PlansTest : DatabaseFileTest() {
    @Test
    fun noPlanIsFollowedAtFirst() = runBlocking {
        val database = open()
        assertNull(database.plans().currentId())
        assertEquals(0, database.number("SELECT COUNT(*) FROM plan_history"))
        val plans = database.plans().all()
        assertEquals(listOf("Push Pull Leg"), plans.map { it.name })
        assertEquals(5, plans.first().dayCount)
        // Tricep Push Down is in two of the days.
        assertEquals(23, plans.first().exerciseCount)
        database.close()
    }

    @Test
    fun followsTheStarterPlanOnceChosen() = runBlocking {
        val database = openFollowingStarterPlan()
        val plan = assertNotNull(database.plans().currentId()?.let { database.plans().plan(it) })
        assertEquals("Push Pull Leg", plan.plan.name)
        assertEquals(listOf("Push A", "Pull A", "Legs", "Push B", "Pull B"), plan.days.map { it.day.name })
        assertEquals(24, plan.days.sumOf { it.exercises.size })
        assertEquals(23, plan.exerciseCount)
        assertEquals(72, plan.days.sumOf { it.setCount })
        assertEquals(
            listOf("Incline Bench", "Chest Fly", "Leg Raises", "Overhead Extensions", "Tricep Push Down"),
            plan.days[0].exercises.map { it.exercise.name },
        )
        val history = database.rows("SELECT start, `end` FROM plan_history") {
            it.getLong(0) to (if (it.isNull(1)) null else it.getLong(1))
        }
        assertEquals(listOf(TODAY to null), history)
        database.close()
    }

    @Test
    fun switchingPlansEndsTheCurrentOne() = runBlocking {
        val database = openFollowingStarterPlan()
        database.change("INSERT INTO plans (name) VALUES ('Upper Lower')")
        val upperLower = database.number("SELECT id FROM plans WHERE name = 'Upper Lower'")
        database.plans().setCurrent(upperLower, TODAY + 3)
        val rows = database.rows("SELECT planId, start, `end` FROM plan_history ORDER BY id") {
            Triple(it.getLong(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2))
        }
        assertEquals(2, rows.size)
        assertEquals(TODAY + 3, rows[0].third)
        assertEquals(Triple(upperLower, TODAY + 3, null), rows[1])
        assertEquals(upperLower, database.plans().currentId())
        // Following it again changes nothing.
        database.plans().setCurrent(upperLower, TODAY + 4)
        assertEquals(2, database.number("SELECT COUNT(*) FROM plan_history"))
        database.close()
    }

    @Test
    fun upNextIsTheDayAfterTheLastOneDone() = runBlocking {
        val database = openFollowingStarterPlan()
        val plan = assertNotNull(database.plans().plan(database.plans().all().first().id))
        assertEquals("Push A", plan.dayAfter(null)?.day?.name)
        assertEquals("Legs", plan.dayAfter(plan.days[1].day.id)?.day?.name)
        assertEquals("Push A", plan.dayAfter(plan.days[4].day.id)?.day?.name)
        database.close()
    }

    @Test
    fun aMissingPlanIsNull() = runBlocking {
        val database = open()
        assertNull(database.plans().plan(99))
        database.close()
    }
}
