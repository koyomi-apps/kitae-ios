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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
        database.notes.save(benchId, "Seat on 4")
        exercises.delete(benchId)
        assertEquals(0, database.number("SELECT COUNT(*) FROM exercise_notes"))
        assertTrue(database.notes.all().isEmpty())
    }

    @Test
    fun notesAreTrimmedAndGoWhenEmptied() = runBlocking {
        val seen = mutableListOf<Map<Long, String>>()
        val watching = launch(Dispatchers.Unconfined) { database.notes.observeAll().collect { seen += it } }
        database.notes.save(benchId, "  Seat on 4\n")
        assertEquals(mapOf(benchId to "Seat on 4"), database.notes.all())
        database.notes.save(benchId, "Seat on 5")
        assertEquals(mapOf(benchId to "Seat on 5"), database.notes.all())
        database.notes.save(benchId, " \n ")
        assertTrue(database.notes.all().isEmpty())
        // Each change reaches whoever follows the notes.
        withTimeout(5_000) { while (seen.lastOrNull()?.isEmpty() != true || seen.size < 2) delay(10) }
        assertTrue(mapOf(benchId to "Seat on 5") in seen)
        watching.cancel()
    }

    @Test
    fun recentExercisesAreTheNewestDone() = runBlocking {
        workout(TODAY - 7, listOf(SetDraft(reps = 10, weightKg = 20.0)))
        val day = plan.days[1]
        val workouts = database.workouts()
        val date = TODAY - 3
        val id = workouts.start(day.day.id, date, date * 86_400_000)
        val dayExercises = workouts.exercises(id, day.day.id)
        workouts.setDone(dayExercises[1].sets.first().id, true)
        workouts.finish(id, FinishMode.KeepSkipped, addNewSets = false, now = date * 86_400_000 + 3_600_000)
        val done = dayExercises[1].exercise.id
        val recent = exercises.recentIds(5)
        // The day's first exercise had no set done, so it isn't recent.
        assertEquals(listOf(done, benchId), recent)
        assertTrue(dayExercises[0].exercise.id !in recent)
        assertEquals(listOf(done), exercises.recentIds(1))
    }

    @Test
    fun aWorkoutsNewBestsAreReadFromItsSets() = runBlocking {
        val first = workout(TODAY - 7, listOf(SetDraft(reps = 10, weightKg = 20.0)))
        val second = workout(TODAY - 3, listOf(SetDraft(reps = 10, weightKg = 22.5)))
        assertTrue(exercises.newBests(first).isEmpty())
        val bests = exercises.newBests(second)
        assertEquals(listOf(benchId), bests.map { it.exercise.id })
        assertEquals(listOf(NewBest.Record.Heaviest), bests.map { it.record })
        assertEquals(22.5, bests.first().set.weightKg)
        assertEquals(20.0, bests.first().previous.weightKg)
    }

    @Test
    fun newBestsBeatEveryWorkoutBefore() {
        val bench = Exercise(id = 1, name = "Bench Press", target = "Chest")
        val history = listOf(
            ExerciseSession(id = 4, date = 40, sets = listOf(SetDraft(reps = 5, weightKg = 90.0))),
            ExerciseSession(
                id = 3,
                date = 30,
                sets = listOf(
                    SetDraft(reps = 15, weightKg = 40.0, type = SetType.Warmup),
                    SetDraft(reps = 6, weightKg = 100.0),
                    SetDraft(reps = 9, weightKg = 80.0),
                ),
            ),
            ExerciseSession(id = 2, date = 20, sets = listOf(SetDraft(reps = 5, weightKg = 100.0), SetDraft(reps = 8, weightKg = 80.0))),
            ExerciseSession(id = 1, date = 10, sets = listOf(SetDraft(reps = 5, weightKg = 95.0), SetDraft(reps = 8, weightKg = 80.0))),
        )
        fun records(id: Long) = NewBest.find(bench, history, id).map { it.record }
        // The first time there's nothing to beat, and a heavier set with the same reps counts once.
        assertTrue(records(1).isEmpty())
        assertEquals(listOf(NewBest.Record.Heaviest), records(2))
        val third = NewBest.find(bench, history, 3)
        assertEquals(listOf(NewBest.Record.Heaviest, NewBest.Record.MostReps), third.map { it.record })
        assertEquals(SetDraft(reps = 6, weightKg = 100.0), third[0].set)
        assertEquals(SetDraft(reps = 5, weightKg = 100.0), third[0].previous)
        // The warm-up's 15 reps don't count.
        assertEquals(SetDraft(reps = 9, weightKg = 80.0), third[1].set)
        assertEquals(SetDraft(reps = 8, weightKg = 80.0), third[1].previous)
        assertTrue(records(4).isEmpty())
    }

    @Test
    fun newBestsWithoutWeightOrWithHelp() {
        // A weight left from before sit-ups stopped using weight doesn't count.
        val situps = Exercise(id = 2, name = "Sit-ups", target = "Core", usesWeight = false)
        val reps = listOf(
            ExerciseSession(id = 2, date = 20, sets = listOf(SetDraft(reps = 21, weightKg = 0.0))),
            ExerciseSession(id = 1, date = 10, sets = listOf(SetDraft(reps = 20, weightKg = 10.0))),
        )
        assertEquals(listOf(NewBest.Record.MostReps), NewBest.find(situps, reps, 2).map { it.record })
        val plank = Exercise(id = 3, name = "Plank", target = "Core", isIsometric = true, usesWeight = false)
        val holds = listOf(
            ExerciseSession(id = 2, date = 20, sets = listOf(SetDraft(reps = 75, weightKg = 0.0))),
            ExerciseSession(id = 1, date = 10, sets = listOf(SetDraft(reps = 60, weightKg = 0.0))),
        )
        assertEquals(listOf(NewBest.Record.LongestHold), NewBest.find(plank, holds, 2).map { it.record })
        val assisted = Exercise(id = 4, name = "Assisted Pull-ups", target = "Lats", libraryKey = "assisted-pull-ups")
        val help = listOf(
            ExerciseSession(id = 2, date = 20, sets = listOf(SetDraft(reps = 8, weightKg = 25.0))),
            ExerciseSession(id = 1, date = 10, sets = listOf(SetDraft(reps = 8, weightKg = 30.0))),
        )
        val bests = NewBest.find(assisted, help, 2)
        assertEquals(listOf(NewBest.Record.LeastHelp), bests.map { it.record })
        assertEquals(30.0, bests.first().previous.weightKg)
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
        // Kitae's assisted dips and pull-ups are assisted; its dips and people's own exercises aren't.
        assertTrue(Exercise(id = 1, name = "Assisted Dips", target = "Triceps", libraryKey = "assisted-dips").isAssisted)
        assertTrue(Exercise(id = 4, name = "Assisted Pull-ups", target = "Lats", libraryKey = "assisted-pull-ups").isAssisted)
        assertFalse(Exercise(id = 2, name = "Dips", target = "Triceps", libraryKey = "dips").isAssisted)
        assertFalse(Exercise(id = 3, name = "Assisted Dips", target = "Triceps").isAssisted)
    }
}
