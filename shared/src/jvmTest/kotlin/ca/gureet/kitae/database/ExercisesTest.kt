// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.useWriterConnection
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

/** Exercises: their details, making and changing them, and how they've gone, as the iPhone's `ExerciseTests` check them. */
class ExercisesTest : DatabaseFileTest() {
    private lateinit var database: KitaeDatabase
    private lateinit var plan: PlanWithDays
    private val exercises get() = database.exercises()
    private val benchId get() = plan.days[0].exercises[0].exercise.id

    @BeforeTest
    fun openDatabase() = runBlocking {
        database = openFollowingStarterPlan()
        plan = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId())))
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    /** Does a workout of Push A on [date] with only bench's first sets changed to [bench] and done, then finishes it. */
    private suspend fun workout(date: Long, bench: List<SetDraft>): Long {
        val day = plan.days[0]
        val workouts = database.workouts()
        val id = workouts.start(day.day.id, date, date * 86_400_000)
        val planned = workouts.exercises(id, day.day.id).first().sets
        for ((set, draft) in planned.zip(bench)) {
            workouts.update(set.id, draft)
            workouts.setDone(set.id, true)
        }
        workouts.finish(id, FinishMode.KeepSkipped, addNewSets = false, now = date * 86_400_000 + 3_600_000)
        return id
    }

    @Test
    fun stepsLeaveOutTypedNumbers() {
        val exercise = Exercise(name = "Bench", target = "Chest", instructions = "1. Lie back\n2) Press it up\n\n   Keep your feet down  \n10. Rack it")
        assertEquals(listOf("Lie back", "Press it up", "Keep your feet down", "Rack it"), exercise.steps)
        assertTrue(Exercise(name = "Plank", target = "Core").steps.isEmpty())
    }

    @Test
    fun referencesHaveToBeWebAddresses() {
        assertEquals("https://youtu.be/abc", Exercises.savedReference("  youtu.be/abc "))
        assertEquals("http://example.com", Exercises.savedReference("http://example.com"))
        assertNull(Exercises.savedReference("   "))
        fun link(reference: String) = Exercise(name = "", target = "", reference = Exercises.savedReference(reference)).referenceLink
        assertEquals("https://youtu.be/abc", link("youtu.be/abc"))
        assertNull(link("not a link"))
        assertNull(link("ftp://example.com"))
        assertNull(link("localhost"))
    }

    @Test
    fun newExercisesNeedANameOfTheirOwn() = runBlocking {
        val id = exercises.create(
            ExerciseDraft(
                name = "  Cable Fly ",
                muscle = MuscleGroup.Chest,
                restSeconds = 60,
                instructions = "Pull the handles together.\n",
                reference = "example.com/fly",
            ),
        )
        val saved = assertNotNull(exercises.exercise(id))
        assertEquals("Cable Fly", saved.name)
        assertEquals(MuscleGroup.Chest, saved.muscle)
        assertTrue(saved.usesWeight)
        assertEquals(60, saved.restSeconds)
        assertEquals("Pull the handles together.", saved.instructions)
        assertEquals("https://example.com/fly", saved.reference)

        assertEquals(NameProblem.ExerciseTaken, assertFailsWith<NameProblemException> { exercises.create(ExerciseDraft(name = "cable fly")) }.problem)
        assertEquals(NameProblem.Empty, assertFailsWith<NameProblemException> { exercises.create(ExerciseDraft(name = "  ")) }.problem)
        assertEquals(
            NameProblem.ExerciseTaken,
            assertFailsWith<NameProblemException> { exercises.update(id, ExerciseDraft(name = "Bench Press")) }.problem,
        )

        val renamed = ExerciseDraft(saved).copy(
            name = "Cable Crossover Fly",
            muscle = MuscleGroup.Shoulders,
            isIsometric = true,
            usesWeight = false,
            reference = "",
        )
        exercises.update(id, renamed)
        val updated = assertNotNull(exercises.exercise(id))
        assertEquals("Cable Crossover Fly", updated.name)
        assertEquals("Shoulders", updated.target)
        assertTrue(updated.isIsometric)
        assertFalse(updated.usesWeight)
        assertFalse(ExerciseDraft(updated).usesWeight)
        assertNull(updated.reference)
        assertEquals(bundledSeed.exercises.size + 1, exercises.all().size)
    }

    @Test
    fun exercisesAreListedByName() = runBlocking {
        val all = exercises.all()
        assertEquals(bundledSeed.exercises.size, all.size)
        // SQLite's NOCASE only folds ASCII letters, like lowercase() does for these names.
        val names = all.map { it.name.lowercase() }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun deletingAnExerciseTakesItOutOfPlansAndWorkouts() = runBlocking {
        val id = workout(TODAY - 2, listOf(SetDraft(reps = 10, weightKg = 22.5)))
        exercises.delete(benchId)
        assertEquals(
            listOf("Chest Fly", "Leg Raises", "Overhead Extensions", "Tricep Push Down"),
            database.plans().dayExercises(plan.days[0].day.id).map { it.exercise.name },
        )
        assertEquals(0, database.number("SELECT COUNT(*) FROM sets WHERE sessionId = $id AND exerciseId = $benchId"))
        assertEquals(bundledSeed.exercises.size - 1, exercises.all().size)
    }

    @Test
    fun deletingAnExerciseDeletesItsNote() = runBlocking {
        database.useWriterConnection { connection ->
            connection.usePrepared("INSERT INTO exercise_notes (exerciseId, note) VALUES ($benchId, 'Seat on 4')") { it.step() }
        }
        exercises.delete(benchId)
        assertEquals(0, database.number("SELECT COUNT(*) FROM exercise_notes"))
    }

    @Test
    fun historyIsNewestFirstAndLeavesOutSetsNotDone() = runBlocking {
        val older = workout(TODAY - 7, listOf(SetDraft(reps = 12, weightKg = 20.0), SetDraft(reps = 10, weightKg = 20.0)))
        val newer = workout(
            TODAY - 3,
            listOf(
                SetDraft(reps = 15, weightKg = 10.0, type = SetType.Warmup),
                SetDraft(reps = 10, weightKg = 22.5),
                SetDraft(reps = 8, weightKg = 22.5),
            ),
        )
        val history = exercises.history(benchId)
        assertEquals(listOf(newer, older), history.map { it.id })
        assertEquals("Push A", history.first().dayName)
        assertEquals(listOf(15, 10, 8), history.first().sets.map { it.reps })
        // The third set of the older workout wasn't done.
        assertEquals(2, history.last().sets.size)

        val newest = history.first()
        assertEquals(SetDraft(reps = 10, weightKg = 22.5), newest.heaviest)
        assertEquals(10, newest.mostReps)

        val last = exercises.lastSessions()
        assertEquals(newer, last[benchId]?.id)
        assertEquals(3, last[benchId]?.sets?.size)
        assertNull(last[plan.days[1].exercises[0].exercise.id])
    }

    @Test
    fun warmupsCountOnlyWhenNothingElseWasDone() {
        val warmups = ExerciseSession(
            id = 1,
            date = 0,
            sets = listOf(
                SetDraft(reps = 15, weightKg = 10.0, type = SetType.Warmup),
                SetDraft(reps = 12, weightKg = 12.5, type = SetType.Warmup),
            ),
        )
        assertEquals(12.5, warmups.heaviest?.weightKg)
        assertEquals(15, warmups.mostReps)
    }

    @Test
    fun progressFollowsTheHeaviestSetOrTheReps() {
        val sessions = (0 until 14).map { index ->
            ExerciseSession(id = index.toLong(), date = 20_000L + index, sets = listOf(SetDraft(reps = 8 + index % 3, weightKg = 20 + index * 2.5)))
        }.reversed()
        val progress = ExerciseProgress(sessions, isIsometric = false)
        assertEquals(ExerciseProgress.Measure.Weight, progress.measure)
        assertEquals(12, progress.points.size)
        assertEquals(2, progress.points.first().id)
        assertEquals(52.5, progress.points.last().value)
        assertEquals(27.5, progress.change)

        val bodyweight = listOf(
            ExerciseSession(id = 2, date = 2, sets = listOf(SetDraft(reps = 12, weightKg = 0.0))),
            ExerciseSession(id = 1, date = 1, sets = listOf(SetDraft(reps = 9, weightKg = 0.0))),
        )
        val reps = ExerciseProgress(bodyweight, isIsometric = false)
        assertEquals(ExerciseProgress.Measure.Reps, reps.measure)
        assertEquals(listOf(9.0, 12.0), reps.points.map { it.value })
        assertEquals(ExerciseProgress.Measure.Seconds, ExerciseProgress(bodyweight, isIsometric = true).measure)

        // An exercise that doesn't use weight follows its reps, even with weight from before.
        val weightless = ExerciseProgress(sessions, isIsometric = false, usesWeight = false)
        assertEquals(ExerciseProgress.Measure.Reps, weightless.measure)
        assertEquals(9.0, weightless.points.last().value)
        assertEquals(SetDraft(reps = 9, weightKg = 52.5), sessions.first().best(usesWeight = false))

        val records = ExerciseRecords(sessions)
        assertEquals(52.5, records.best?.weightKg)
        assertEquals(SetDraft(reps = 10, weightKg = 47.5), records.mostReps)
        assertEquals(14, records.workoutCount)
    }

    @Test
    fun lessHelpIsProgressOnAnAssistedMachine() {
        // Assisted dips with 30, then 25, then 20 kg of help.
        val sessions = listOf(
            ExerciseSession(id = 3, date = 3, sets = listOf(SetDraft(reps = 8, weightKg = 20.0), SetDraft(reps = 10, weightKg = 25.0))),
            ExerciseSession(id = 2, date = 2, sets = listOf(SetDraft(reps = 8, weightKg = 25.0))),
            ExerciseSession(id = 1, date = 1, sets = listOf(SetDraft(reps = 8, weightKg = 30.0))),
        )
        assertEquals(SetDraft(reps = 8, weightKg = 20.0), sessions.first().best(usesWeight = true, assisted = true))
        val progress = ExerciseProgress(sessions, isIsometric = false, assisted = true)
        assertEquals(ExerciseProgress.Measure.Help, progress.measure)
        assertEquals(listOf(30.0, 25.0, 20.0), progress.points.map { it.value })
        assertEquals(-10.0, progress.change)
        val records = ExerciseRecords(sessions, assisted = true)
        assertEquals(SetDraft(reps = 8, weightKg = 20.0), records.best)
        assertEquals(3, records.bestDate)
        assertEquals(SetDraft(reps = 10, weightKg = 25.0), records.mostReps)
    }
}
