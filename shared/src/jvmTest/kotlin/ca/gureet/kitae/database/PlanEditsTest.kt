// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Changing plans, as the iPhone's `PlanEditTests` check it. */
class PlanEditsTest : DatabaseFileTest() {
    private lateinit var database: KitaeDatabase
    private lateinit var plan: PlanWithDays
    private val edits get() = database.planEdits()

    @BeforeTest
    fun openDatabase() = runBlocking {
        database = openFollowingStarterPlan()
        plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    /** The plan as it's saved now. */
    private suspend fun saved(): PlanWithDays = assertNotNull(database.plans().plan(plan.plan.id))

    @Test
    fun plansNeedANameOfTheirOwn() = runBlocking {
        assertEquals(NameProblem.Empty, assertFailsWith<NameProblemException> { edits.create("   ") }.problem)
        assertEquals(NameProblem.Taken, assertFailsWith<NameProblemException> { edits.create("Push Pull Leg") }.problem)
        val id = edits.create("  Upper Lower ")
        assertEquals(listOf("Upper Lower"), database.texts("SELECT name FROM plans WHERE id = $id"))
        // Keeping its own name is fine; taking another plan's isn't.
        edits.rename(id, "Upper Lower")
        assertEquals(NameProblem.Taken, assertFailsWith<NameProblemException> { edits.rename(id, "Push Pull Leg") }.problem)
    }

    @Test
    fun newDaysGoAtTheEnd() = runBlocking {
        val id = edits.addDay(plan.plan.id, " Arms ")
        val days = saved().days
        assertEquals(listOf("Push A", "Pull A", "Legs", "Push B", "Pull B", "Arms"), days.map { it.day.name })
        assertEquals(id, days.last().day.id)
        assertEquals(NameProblem.Empty, assertFailsWith<NameProblemException> { edits.addDay(plan.plan.id, "") }.problem)
    }

    @Test
    fun daysCanBeRenamedAndReordered() = runBlocking {
        edits.renameDay(plan.days[2].day.id, "Leg Day")
        edits.orderDays(plan.days.map { it.day.id }.reversed())
        assertEquals(listOf("Pull B", "Push B", "Leg Day", "Pull A", "Push A"), saved().days.map { it.day.name })
    }

    @Test
    fun deletingADayKeepsItsWorkouts() = runBlocking {
        val pushA = plan.days[0]
        val workoutId = database.workouts().start(pushA.day.id, TODAY, now = 0)
        edits.deleteDay(pushA.day.id)
        assertEquals(listOf("Pull A", "Legs", "Push B", "Pull B"), saved().days.map { it.day.name })

        val workout = assertNotNull(database.workouts().workout(workoutId)).workout
        assertNull(workout.dayId)
        assertEquals(plan.plan.id, workout.planId)
        assertEquals(15, database.number("SELECT COUNT(*) FROM sets WHERE sessionId = $workoutId"))
        assertEquals(0, database.number("SELECT COUNT(*) FROM sets WHERE sessionId = $workoutId AND routineSetId IS NOT NULL"))
        assertEquals(72L - 15, database.number("SELECT COUNT(*) FROM routine_sets"))
    }

    @Test
    fun newExercisesStartFromTheLastWorkout() = runBlocking {
        val fallback = SetDraft(reps = 12, weightKg = 20.0)
        // Squats were never done: three sets like the fallback.
        val squats = plan.days[2].exercises[0].exercise.id
        assertEquals(List(3) { fallback }, edits.suggestedSets(squats, fallback))

        // Two sets of Incline Bench done in a workout of Push A…
        val pushA = plan.days[0]
        val bench = pushA.exercises[0].exercise.id
        val workouts = database.workouts()
        val workoutId = workouts.start(pushA.day.id, TODAY, now = 0)
        val sets = workouts.exercises(workoutId, pushA.day.id).first().sets
        workouts.update(sets[0].id, SetDraft(reps = 8, weightKg = 25.0, type = sets[0].setType, restSeconds = sets[0].restSeconds))
        workouts.setDone(sets[0].id, true)
        workouts.setDone(sets[1].id, true)
        workouts.finish(workoutId, FinishMode.Discard, addNewSets = false, now = 1)
        val suggested = edits.suggestedSets(bench, fallback)
        assertEquals(listOf(8, 12), suggested.map { it.reps })
        assertEquals(listOf(25.0, 20.0), suggested.map { it.weightKg })

        // …are what it starts with in Pull A.
        val pullA = plan.days[1]
        val dayExerciseId = edits.addExercise(pullA.day.id, bench, suggested)
        val added = saved().days[1].exercises.last()
        assertEquals(dayExerciseId, added.dayExercise.id)
        assertEquals("Incline Bench", added.exercise.name)
        assertEquals(listOf(8, 12), added.sets.map { it.reps })
    }

    @Test
    fun plannedSetsKeepWarmupsFirst() = runBlocking {
        val dayExercise = plan.days[0].exercises[0].dayExercise
        val warmup = edits.addSet(dayExercise.id, SetDraft(reps = 15, weightKg = 10.0, type = SetType.Warmup))
        var sets = saved().days[0].exercises[0].sets
        assertEquals(warmup, sets.first().id)
        assertEquals(listOf(SetType.Warmup, SetType.Standard, SetType.Standard, SetType.Standard), sets.map { it.setType })

        // Turning the last set into a warm-up moves it up as well.
        val last = sets.last()
        edits.updateSet(last.id, SetDraft(reps = 10, weightKg = 12.5, type = SetType.Warmup, restSeconds = 45))
        sets = saved().days[0].exercises[0].sets
        assertEquals(listOf(SetType.Warmup, SetType.Warmup, SetType.Standard, SetType.Standard), sets.map { it.setType })
        assertEquals(last.id, sets[1].id)
        assertEquals(10, sets[1].reps)
        assertEquals(45, sets[1].restSeconds)

        edits.deleteSet(warmup)
        assertEquals(3, saved().days[0].exercises[0].sets.size)
    }

    @Test
    fun plannedSetsWithoutWeightGetNone() = runBlocking {
        // Leg raises, in Push A, are done with the body alone.
        val legRaises = assertNotNull(plan.days[0].exercises.firstOrNull { it.exercise.id == 20L })
        val id = edits.addSet(legRaises.dayExercise.id, SetDraft(reps = 10, weightKg = 20.0))
        assertEquals(0L, database.number("SELECT weight FROM routine_sets WHERE id = $id"))
    }

    @Test
    fun exercisesCanBeReorderedAndRemoved() = runBlocking {
        val ids = plan.days[0].exercises.map { it.dayExercise.id }
        edits.orderExercises(listOf(ids[4], ids[0], ids[1], ids[2], ids[3]))
        edits.removeExercise(ids[2])
        assertEquals(
            listOf("Tricep Push Down", "Incline Bench", "Chest Fly", "Overhead Extensions"),
            saved().days[0].exercises.map { it.exercise.name },
        )
        assertEquals(0, database.number("SELECT COUNT(*) FROM routine_sets WHERE routineExerciseId = ${ids[2]}"))
    }

    @Test
    fun deletingAPlanKeepsItsWorkouts() = runBlocking {
        val workoutId = database.workouts().start(plan.days[0].day.id, TODAY, now = 0)
        edits.delete(plan.plan.id)
        assertNull(database.plans().currentId())
        assertTrue(database.plans().all().isEmpty())
        assertEquals(15, database.number("SELECT COUNT(*) FROM sets WHERE sessionId = $workoutId"))
        val workout = assertNotNull(database.workouts().workout(workoutId)).workout
        assertNull(workout.planId)
        assertNull(workout.dayId)
    }

    @Test
    fun aPlanNeedsAnExercise() = runBlocking {
        val empty = edits.create("Empty")
        assertFalse(edits.hasExercises(empty))
        assertTrue(edits.hasExercises(plan.plan.id))
    }
}
