// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.muscles

import ca.gureet.kitae.database.DatabaseFileTest
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseSetCount
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.bundledSeed
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The muscle map's data and the counting of muscles, as the iPhone's `ExerciseMusclesTests` check them. */
class MusclesTest : DatabaseFileTest() {
    private val muscles = ExerciseMuscles.decode(File(System.getProperty("kitae.muscles")).readText())

    @Test
    fun everyExerciseHasItsMusclesByItsName() {
        val names = bundledSeed.exercises.associate { it.key to it.name }
        val map = muscles.map
        assertEquals(names.keys, map.exercises.map { it.key }.toSet())
        for (entry in map.exercises) {
            assertEquals(names[entry.key], entry.name, entry.key)
            // Every name read as a muscle.
            assertEquals(entry.primary.size + entry.secondary.size, entry.work.primary.size + entry.work.secondary.size, entry.key)
            assertTrue(entry.work.primary.isNotEmpty(), entry.name)
            assertTrue(entry.work.primary.intersect(entry.work.secondary.toSet()).isEmpty(), entry.name)
        }
        assertEquals(names.size, muscles.byKey.size)
    }

    @Test
    fun everyMuscleIsOnTheMap() {
        val map = muscles.map
        val shown = (map.muscles(MuscleMap.Side.Front) + map.muscles(MuscleMap.Side.Back)).map { it.first }.toSet()
        assertEquals(Muscle.entries.toSet(), shown)
        assertTrue(map.body.all { it.size >= 8 && (it.size - 2) % 6 == 0 })
    }

    @Test
    fun peoplesOwnExercisesShowTheirGroup() {
        for (group in MuscleGroup.entries) assertTrue(group.muscles.isNotEmpty(), group.name)
        assertEquals(MuscleWork(listOf(Muscle.Chest), emptyList()), muscles.work(Exercise(name = "Cable Press", target = "Chest")))
        assertNull(muscles.work(Exercise(name = "Odd", target = "Elsewhere")))
    }

    @Test
    fun aSetCountsOnceForPrimaryMusclesAndHalfForSecondary() {
        val bench = Exercise(id = 21, name = "Bench Press", target = "Chest", libraryKey = "barbell-bench-press")
        val curls = Exercise(id = 1, name = "Curls", target = "Biceps", libraryKey = "dumbbell-curls")
        val volume = MuscleVolume(listOf(ExerciseSetCount(bench, 6), ExerciseSetCount(curls, 2)), muscles)
        assertEquals(8, volume.totalSets)
        assertEquals(6.0, volume.sets[Muscle.Chest])
        assertEquals(3.0, volume.sets[Muscle.FrontDelts])
        assertEquals(3.0, volume.sets[Muscle.Triceps])
        assertEquals(2.0, volume.sets[Muscle.Biceps])
        assertEquals(1.0, volume.sets[Muscle.Forearms])
        // Half the top muscle's sets or more is "most".
        assertEquals(
            MuscleWork(listOf(Muscle.Chest, Muscle.FrontDelts, Muscle.Triceps), listOf(Muscle.Biceps, Muscle.Forearms)),
            volume.shading,
        )
        assertEquals(MuscleWork(emptyList(), emptyList()), MuscleVolume().shading)
    }

    @Test
    fun aWeeksSetsLeaveOutWarmUpsUndoneSetsAndOtherDays() = runBlocking {
        val database = openFollowingStarterPlan()
        val day = assertNotNull(database.plans().plan(assertNotNull(database.plans().currentId()))).days.first()
        val workouts = database.workouts()
        // Long ago.
        val old = workouts.start(day.day.id, TODAY - 10, (TODAY - 10) * 86_400_000)
        for (set in workouts.exercises(old, day.day.id).flatMap { it.sets }) workouts.setDone(set.id, true)
        workouts.finish(old, FinishMode.KeepSkipped, false, (TODAY - 10) * 86_400_000 + 3_600_000)
        // Two days ago: the first exercise's sets, one of them a warm-up.
        val recent = workouts.start(day.day.id, TODAY - 2, (TODAY - 2) * 86_400_000)
        val first = workouts.exercises(recent, day.day.id).first()
        for (set in first.sets) workouts.setDone(set.id, true)
        val warmUp = first.sets.first()
        workouts.update(warmUp.id, SetDraft(warmUp.reps, warmUp.weightKg, SetType.Warmup, warmUp.restSeconds))

        val counts = database.stats().exerciseSets(TODAY - 6, TODAY)
        assertEquals(1, counts.size)
        assertEquals(first.sets.size - 1, counts.single().sets)
        database.close()
    }
}
