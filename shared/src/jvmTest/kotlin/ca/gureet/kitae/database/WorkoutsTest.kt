// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Starting, changing and finishing workouts in the database, as the iPhone's `SessionTests`
 * check them against what Kitae for Android stores.
 */
class WorkoutsTest : DatabaseFileTest() {
    private lateinit var database: KitaeDatabase
    private lateinit var plan: PlanWithDays
    private val workouts get() = database.workouts()
    private val pushA get() = plan.days[0]
    private val pullA get() = plan.days[1]

    @BeforeTest
    fun openDatabase() = runBlocking {
        database = openFollowingStarterPlan()
        plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private suspend fun start(day: DayWithExercises, on: Long = TODAY): Long = workouts.start(day.day.id, on, NOW)

    private suspend fun sets(workoutId: Long): List<SetRow> =
        database.rows("SELECT id, `order`, routineSetId, isCompleted, rir, exerciseId, weight FROM sets WHERE sessionId = $workoutId ORDER BY `order`, id") {
            SetRow(
                id = it.getLong(0),
                order = it.getInt(1),
                plannedSetId = if (it.isNull(2)) null else it.getLong(2),
                isDone = it.getBoolean(3),
                rir = it.getInt(4),
                exerciseId = it.getLong(5),
                weight = it.getDouble(6),
            )
        }

    private suspend fun exercises(workoutId: Long, day: DayWithExercises): List<WorkoutExercise> =
        workouts.exercises(workoutId, day.day.id)

    private suspend fun plannedSets(day: DayWithExercises): List<DayExerciseWithSets> =
        database.plans().dayExercises(day.day.id)

    @Test
    fun startCopiesTheDaysSets() = runBlocking {
        val id = start(pushA)
        val workout = assertNotNull(workouts.workout(id)).workout
        assertEquals(TODAY, workout.date)
        assertEquals(plan.plan.id, workout.planId)
        assertEquals(pushA.day.id, workout.dayId)
        assertFalse(workout.isFinished)
        assertEquals(NOW, workout.startedAt)
        assertNull(workout.finishedAt)

        val rows = sets(id)
        val planned = pushA.exercises.flatMap { it.sets }
        assertEquals(planned.size, rows.size)
        assertEquals(planned.indices.toList(), rows.map { it.order })
        assertEquals(planned.map { it.id }, rows.map { it.plannedSetId })
        assertTrue(rows.all { !it.isDone && it.rir == 2 })
    }

    @Test
    fun exercisesWithoutWeightGetNone() = runBlocking {
        // A day planned before leg raises stopped using weight, with Kitae for Android's 20 kg.
        database.change(
            "UPDATE routine_sets SET weight = 20 WHERE routineExerciseId IN (SELECT id FROM routine_exercises WHERE exerciseId = 20)",
        )
        val id = start(pushA)
        val legRaises = assertNotNull(exercises(id, pushA).firstOrNull { it.exercise.libraryKey == "lying-leg-raises" })
        assertFalse(legRaises.exercise.usesWeight)
        assertTrue(legRaises.sets.isNotEmpty() && legRaises.sets.all { it.weightKg == 0.0 })

        // Nor do sets added during the workout.
        workouts.addSet(id, 20, SetDraft(reps = 12, weightKg = 20.0))
        assertTrue(sets(id).filter { it.exerciseId == 20L }.all { it.weight == 0.0 })
        // Exercises that use weight keep theirs.
        val bench = assertNotNull(exercises(id, pushA).firstOrNull { it.exercise.usesWeight })
        assertTrue(bench.sets.all { it.weightKg == 20.0 })
    }

    @Test
    fun startingTheSameDayAgainCarriesOn() = runBlocking {
        val first = start(pushA)
        assertEquals(first, start(pushA))
        assertNotEquals(first, start(pushA, on = TODAY + 1))
    }

    @Test
    fun aWorkoutFromYesterdayIsStillGoing() = runBlocking {
        val id = start(pushA, on = TODAY - 1)
        val active = workouts.active(TODAY)
        assertEquals(id, active?.workout?.id)
        assertEquals("Push A", active?.dayName)
        assertNull(workouts.active(TODAY + 1))
    }

    @Test
    fun workoutListsTheDaysExercisesInOrder() = runBlocking {
        val id = start(pushA)
        val list = exercises(id, pushA)
        assertEquals(pushA.exercises.map { it.exercise.name }, list.map { it.exercise.name })
        assertTrue(list.all { exercise -> exercise.sets.size == 3 && exercise.sets.all { !it.isDone && !it.isAdded } })
    }

    @Test
    fun addedWarmupsGoFirst() = runBlocking {
        val id = start(pushA)
        val exerciseId = pushA.exercises[0].exercise.id
        val warmup = workouts.addSet(id, exerciseId, SetDraft(reps = 15, weightKg = 10.0, type = SetType.Warmup))
        val first = exercises(id, pushA).first()
        assertEquals(warmup, first.sets.first().id)
        assertEquals(
            listOf(SetType.Warmup, SetType.Standard, SetType.Standard, SetType.Standard),
            first.sets.map { it.setType },
        )
        assertTrue(first.sets.first().isAdded)
    }

    @Test
    fun changingASetToAWarmupMovesIt() = runBlocking {
        val id = start(pushA)
        val last = exercises(id, pushA).first().sets.last()
        workouts.update(last.id, SetDraft(last.reps, last.weightKg, SetType.Warmup, last.restSeconds))
        val sets = exercises(id, pushA).first().sets
        assertEquals(last.id, sets.first().id)
        assertEquals(listOf(SetType.Warmup, SetType.Standard, SetType.Standard), sets.map { it.setType })
    }

    @Test
    fun finishingTakesWhatWasDoneIntoThePlan() = runBlocking {
        val id = start(pushA)
        val set = exercises(id, pushA).first().sets.first()
        workouts.update(set.id, SetDraft(reps = 8, weightKg = 22.5, type = set.setType, restSeconds = 120))
        workouts.setDone(set.id, true)
        workouts.finish(id, FinishMode.KeepSkipped, addNewSets = false, now = NOW + 60_000)

        val planned = plannedSets(pushA).first().sets.first()
        assertEquals(8, planned.reps)
        assertEquals(22.5, planned.weightKg)
        assertEquals(120, planned.restSeconds)

        val workout = assertNotNull(workouts.workout(id)).workout
        assertTrue(workout.isFinished)
        assertEquals(NOW + 60_000, workout.finishedAt)
        // Keep as skipped: the sets that weren't done stay, not done.
        val rows = sets(id)
        assertEquals(15, rows.size)
        assertEquals(1, rows.count { it.isDone })
    }

    @Test
    fun removeFromPlanDropsWhatWasntDone() = runBlocking {
        val id = start(pushA)
        val list = exercises(id, pushA)
        // Everything of the first exercise done, nothing of the others.
        for (set in list[0].sets) workouts.setDone(set.id, true)
        workouts.finish(id, FinishMode.RemoveFromPlan, addNewSets = false, now = NOW)
        assertEquals(listOf("Incline Bench"), plannedSets(pushA).map { it.exercise.name })
        assertEquals(3, sets(id).size)
    }

    @Test
    fun discardModeRemovesAWorkoutWithNothingDone() = runBlocking {
        val id = start(pushA)
        workouts.finish(id, FinishMode.Discard, addNewSets = false, now = NOW)
        assertNull(workouts.workout(id))
        assertEquals(15, plannedSets(pushA).sumOf { it.sets.size })
    }

    @Test
    fun addedSetsCanJoinTheDay() = runBlocking {
        val id = start(pushA)
        val firstExercise = pushA.exercises[0].exercise.id
        // Face Pulls, from Pull A, isn't in Push A yet.
        val facePulls = assertNotNull(pullA.exercises.firstOrNull { it.exercise.name == "Face Pulls" }).exercise.id
        val extra = workouts.addSet(id, firstExercise, SetDraft(reps = 6, weightKg = 25.0, type = SetType.Failure))
        val other = workouts.addSet(id, facePulls, SetDraft(reps = 15, weightKg = 12.5, restSeconds = 60))
        workouts.setDone(extra, true)
        workouts.setDone(other, true)
        workouts.finish(id, FinishMode.KeepSkipped, addNewSets = true, now = NOW)

        val day = plannedSets(pushA)
        assertEquals(4, day[0].sets.size)
        assertEquals(SetType.Failure, day[0].sets.last().setType)
        assertEquals(6, day[0].sets.last().reps)
        assertEquals("Face Pulls", day.last().exercise.name)
        assertEquals(listOf<Int?>(60), day.last().sets.map { it.restSeconds })
        // The added sets now point at the plan's new sets.
        assertEquals(0, sets(id).count { it.isDone && it.plannedSetId == null })
    }

    @Test
    fun onlyThisWorkoutLeavesTheDayAlone() = runBlocking {
        val id = start(pushA)
        val extra = workouts.addSet(id, pushA.exercises[0].exercise.id, SetDraft(reps = 6, weightKg = 25.0))
        workouts.setDone(extra, true)
        workouts.finish(id, FinishMode.KeepSkipped, addNewSets = false, now = NOW)
        assertEquals(3, plannedSets(pushA)[0].sets.size)
    }

    @Test
    fun discardRemovesTheWorkoutButNotThePlan() = runBlocking {
        val id = start(pushA)
        workouts.setDone(sets(id).first().id, true)
        workouts.discard(id)
        assertTrue(sets(id).isEmpty())
        assertNull(workouts.workout(id))
        assertEquals(15, plannedSets(pushA).sumOf { it.sets.size })
    }

    @Test
    fun theLastDayDoneIsTheLastOneWithASetDone() = runBlocking {
        val id = start(pullA)
        // A workout with nothing done doesn't count.
        assertNull(workouts.lastDoneDayId())
        assertFalse(workouts.hasDoneSets())
        workouts.setDone(sets(id).first().id, true)
        assertEquals(pullA.day.id, workouts.lastDoneDayId())
        assertTrue(workouts.hasDoneSets())
    }

    @Test
    fun newSetsCopyThePlanWhenTheWorkoutHasNone() = runBlocking {
        val exercise = pushA.exercises[1].exercise.id
        val like = workouts.lastKnownSet(exercise, pushA.day.id)
        assertEquals(12, like?.reps)
        assertEquals(20.0, like?.weightKg)
        assertNull(workouts.lastKnownSet(75, pushA.day.id))
    }

    private data class SetRow(
        val id: Long,
        val order: Int,
        val plannedSetId: Long?,
        val isDone: Boolean,
        val rir: Int,
        val exerciseId: Long,
        val weight: Double,
    )

    private companion object {
        const val NOW = 1_791_640_000_000L
    }
}
