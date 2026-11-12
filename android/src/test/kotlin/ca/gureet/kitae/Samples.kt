// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import ca.gureet.kitae.app.AppActions
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.app.WorkoutState
import ca.gureet.kitae.database.DayExercise
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.Plan
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.PlannedSet
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.TrainingDay
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSet
import ca.gureet.kitae.model.RestTimer

/** Example data like the Kamae canvas's: the starter plan, with Push A in progress. */
object Samples {
    /** Saturday 10 October 2026, 18:00 UTC. */
    const val NOW = 1_791_741_600_000L

    private val names = listOf("Incline Bench", "Chest Fly", "Leg Raises", "Overhead Extensions", "Tricep Push Down")
    private val exercises = names.mapIndexed { index, name ->
        Exercise(name = name, target = "Chest", id = index + 1L, usesWeight = name != "Leg Raises")
    }

    private fun day(id: Long, name: String, position: Int) = DayWithExercises(
        TrainingDay(planId = 1, name = name, position = position, id = id),
        if (id != 1L) emptyList() else exercises.mapIndexed { index, exercise ->
            DayExerciseWithSets(
                DayExercise(dayId = id, exerciseId = exercise.id, position = index, id = index + 1L),
                exercise,
                List(3) { set -> PlannedSet(dayExerciseId = index + 1L, reps = 10, weightKg = 20.0, type = "Standard", position = set, id = index * 3L + set) },
            )
        },
    )

    val plan = PlanWithDays(
        Plan(name = "Push Pull Leg", id = 1),
        listOf(day(1, "Push A", 0), day(2, "Pull A", 1), day(3, "Legs", 2), day(4, "Push B", 3), day(5, "Pull B", 4)),
    )

    val plans = listOf(
        PlanSummary(1, "Push Pull Leg", 5, 23),
        PlanSummary(2, "Upper Lower", 4, 18),
        PlanSummary(3, "Full Body", 3, 12),
    )

    val today = AppState(loaded = true, plans = plans, plan = plan, upNextDayId = 1, hadDoneSets = true)

    private var setId = 100L

    private fun sets(exercise: Exercise, vararg values: Triple<Int, Double, Boolean>) = WorkoutExercise(
        exercise,
        values.mapIndexed { index, (reps, kg, done) ->
            WorkoutSet(reps, kg, "Standard", index, workoutId = 7, exerciseId = exercise.id, rir = 0, isDone = done, plannedSetId = setId, id = setId++)
        },
    )

    val workout = WorkoutState(
        Workout(date = 20_736, planId = 1, dayId = 1, isFinished = false, startedAt = NOW - (23 * 60 + 40) * 1000L, id = 7),
        "Push A",
        listOf(
            sets(exercises[0], Triple(10, 60.0, true), Triple(10, 60.0, true), Triple(10, 60.0, true)),
            sets(exercises[1], Triple(12, 20.0, true), Triple(12, 20.0, true), Triple(12, 20.0, true)),
            sets(exercises[2], Triple(12, 0.0, false), Triple(12, 0.0, false), Triple(12, 0.0, false)),
            sets(exercises[3], Triple(12, 15.0, false), Triple(12, 15.0, false), Triple(12, 15.0, false)),
            sets(exercises[4], Triple(12, 25.0, false), Triple(12, 25.0, false), Triple(12, 25.0, false)),
        ),
        RestTimer(endsAt = NOW + 91_000, total = 120_000),
    )

    val inProgress = today.copy(workout = workout, isWorkoutPresented = true)
}

/** Actions that remember what was asked of them. */
class RecordedActions : AppActions {
    val calls = mutableListOf<String>()
    override fun minimize() { calls += "minimize" }
    override fun discard() { calls += "discard" }
    override fun finish(mode: FinishMode, addNewSets: Boolean) { calls += "finish $mode $addNewSets" }
    override fun toggleDone(setId: Long) { calls += "toggleDone $setId" }
    override fun updateSet(setId: Long, draft: SetDraft) { calls += "updateSet $setId $draft" }
    override fun addSet(exerciseId: Long) { calls += "addSet $exerciseId" }
    override fun deleteSet(setId: Long) { calls += "deleteSet $setId" }
    override fun pauseRest() { calls += "pauseRest" }
    override fun resumeRest() { calls += "resumeRest" }
    override fun addRest(seconds: Int) { calls += "addRest $seconds" }
    override fun skipRest() { calls += "skipRest" }
    override fun pickDay(id: Long) { calls += "pickDay $id" }
    override fun start(day: DayWithExercises) { calls += "start ${day.day.name}" }
    override fun selectPlan(id: Long) { calls += "selectPlan $id" }
    override fun choosePlan(open: Boolean) { calls += "choosePlan $open" }
    override fun presentWorkout(open: Boolean) { calls += "presentWorkout $open" }
    override fun finishAndStart(mode: FinishMode, addNewSets: Boolean, day: DayWithExercises) { calls += "finishAndStart $mode ${day.day.name}" }
}
