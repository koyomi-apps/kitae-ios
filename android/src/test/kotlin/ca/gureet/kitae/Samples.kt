// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import ca.gureet.kitae.app.AppActions
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.app.BackupPlace
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.ExerciseDetails
import ca.gureet.kitae.app.ExerciseList
import ca.gureet.kitae.app.PageSource
import ca.gureet.kitae.app.WorkoutRecord
import ca.gureet.kitae.app.WorkoutState
import ca.gureet.kitae.app.YouData
import ca.gureet.kitae.database.ActivityWeeks
import ca.gureet.kitae.database.DayExercise
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.ExerciseSession
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.HistoryMonth
import ca.gureet.kitae.database.Plan
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.PlannedSet
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.TrainingDay
import ca.gureet.kitae.database.TrainingTotals
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSet
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.database.historyMonths
import ca.gureet.kitae.model.AppSettings
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

    /** Backups going to a folder picked on the phone, the last one an hour ago. */
    val backedUp = today.copy(
        backup = BackupState(BackupPlace("content://com.android.externalstorage.documents/tree/primary%3AKamae", NOW / 1000 - 3600), "Kamae"),
    )

    // History, as the canvas has it on Thursday 22 October 2026.

    private const val OCTOBER_22 = 20_748L

    private fun summary(id: Long, daysBefore: Int, name: String, minutes: Int?, sets: Int, done: Int) = WorkoutSummary(
        Workout(
            date = OCTOBER_22 - daysBefore,
            planId = 1,
            dayId = 1,
            isFinished = id != 7L,
            startedAt = minutes?.let { NOW },
            finishedAt = minutes?.let { NOW + it * 60_000L },
            id = id,
        ),
        name,
        sets,
        done,
    )

    val history = listOf(
        summary(7, 0, "Push A", null, 15, 6),
        summary(6, 1, "Pull A", 44, 15, 15),
        summary(5, 3, "Legs", 64, 15, 15),
        summary(4, 5, "Push B", 47, 12, 12),
        summary(3, 6, "Pull B", 54, 15, 15),
        summary(2, 8, "Push A", 0, 15, 0),
        summary(1, 10, "Legs", 61, 15, 15),
        summary(0, 12, "Pull A", 50, 15, 15),
    )

    private val chest = listOf(
        "Bench Press" to SetDraft(8, 60.0),
        "Incline Bench" to SetDraft(12, 26.25),
        "Pec Dec" to SetDraft(12, 45.0),
        "Chest Fly" to SetDraft(12, 20.0),
        "Dumbbell Bench Press" to SetDraft(10, 24.0),
        "Incline Dumbbell Press" to SetDraft(10, 22.0),
        "Push-ups" to SetDraft(15, 0.0),
        "Cable Crossovers" to null,
    )

    private val library = chest.mapIndexed { index, (name, _) ->
        Exercise(
            name = name,
            target = "Chest",
            id = 100L + index,
            restSeconds = if (name == "Incline Bench") 90 else null,
            instructions = if (name == "Incline Bench") {
                "Set the bench to about 30 degrees.\nLower the bar to your upper chest.\nPress it back up over your shoulders."
            } else {
                null
            },
            reference = if (name == "Incline Bench") "https://example.org/incline-bench" else null,
            usesWeight = name != "Push-ups",
        )
    } + listOf(
        Exercise(name = "Bent-over Rows", target = "UpperBack", id = 120),
        Exercise(name = "Face Pulls", target = "UpperBack", id = 121),
        Exercise(name = "Lat Pull-down", target = "Lats", id = 122),
    )

    val exerciseList = ExerciseList(
        library,
        chest.mapIndexedNotNull { index, (_, set) -> set?.let { 100L + index to ExerciseSession(6, OCTOBER_22 - 1, "Push A", listOf(it)) } }.toMap(),
        recentIds = listOf(120, 101, 122),
    )

    /** Incline Bench's heaviest sets from September 16 to October 21. */
    val inclineBench = ExerciseDetails(
        library[1],
        listOf(26.25, 25.0, 23.75, 22.5, 21.25).mapIndexed { index, kg ->
            ExerciseSession(20L - index, OCTOBER_22 - 1 - index * 9L, "Push A", listOf(SetDraft(12, kg), SetDraft(12, kg), SetDraft(10, kg)))
        },
    )

    val pastWorkout = WorkoutRecord(
        history[1],
        listOf(
            sets(exercises[0], Triple(10, 60.0, true), Triple(10, 60.0, true), Triple(9, 60.0, true)),
            sets(exercises[1], Triple(12, 20.0, true), Triple(12, 20.0, true), Triple(12, 20.0, false)),
            sets(exercises[2], Triple(15, 0.0, true), Triple(15, 0.0, true)),
        ),
        previousId = 0,
    )
}

/** The pages' data, from the samples above. */
object SamplePages : PageSource {
    override suspend fun historyMonths(): List<HistoryMonth> = historyMonths(Samples.history, activeId = 7)

    override suspend fun workoutRecord(id: Long): WorkoutRecord? = Samples.pastWorkout.takeIf { id == it.summary.workout.id }

    override suspend fun exerciseList(recent: Int): ExerciseList = Samples.exerciseList

    override suspend fun exerciseDetails(id: Long): ExerciseDetails? = Samples.inclineBench.takeIf { id == it.exercise.id }

    override suspend fun plan(id: Long): PlanWithDays? = Samples.plan.takeIf { id == it.plan.id }

    /** Three or four days a week for the last 16 weeks. */
    override suspend fun youData(today: Long, firstWeekday: Int): YouData = YouData(
        TrainingTotals(lifts = 288, days = 20, exercises = 169),
        ActivityWeeks(today, firstWeekday, (0L until 112L).filter { it % 7 in setOf(0L, 2L, 4L) || it % 11 == 3L }.map { today - it }.toSet()),
    )
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
    override fun deleteWorkout(id: Long) { calls += "deleteWorkout $id" }
    override suspend fun saveExercise(draft: ExerciseDraft, id: Long?): Long = 999L.also { calls += "saveExercise ${draft.name} $id" }
    override fun deleteExercise(id: Long) { calls += "deleteExercise $id" }
    override suspend fun createPlan(name: String): Long = 9L.also { calls += "createPlan $name" }
    override suspend fun renamePlan(planId: Long, name: String) { calls += "renamePlan $planId $name" }
    override fun deletePlan(planId: Long) { calls += "deletePlan $planId" }
    override suspend fun addDay(planId: Long, name: String): Long = 99L.also { calls += "addDay $planId $name" }
    override suspend fun renameDay(dayId: Long, name: String) { calls += "renameDay $dayId $name" }
    override fun deleteDay(dayId: Long) { calls += "deleteDay $dayId" }
    override fun orderDays(dayIds: List<Long>) { calls += "orderDays $dayIds" }
    override suspend fun addExerciseToDay(dayId: Long, exerciseId: Long): Long = 77L.also { calls += "addExerciseToDay $dayId $exerciseId" }
    override fun removeFromDay(dayExerciseId: Long) { calls += "removeFromDay $dayExerciseId" }
    override fun orderExercises(dayExerciseIds: List<Long>) { calls += "orderExercises $dayExerciseIds" }
    override fun addPlannedSet(dayExerciseId: Long, last: SetDraft?) { calls += "addPlannedSet $dayExerciseId $last" }
    override fun updatePlannedSet(plannedSetId: Long, set: SetDraft) { calls += "updatePlannedSet $plannedSetId $set" }
    override fun deletePlannedSet(plannedSetId: Long) { calls += "deletePlannedSet $plannedSetId" }
    override fun updateSettings(change: (AppSettings) -> AppSettings) { calls += "updateSettings ${change(AppSettings())}" }
    override fun setBackupFolder(uri: String) { calls += "setBackupFolder $uri" }
    override fun backUpNow() { calls += "backUpNow" }
    override fun openBackup(uri: String) { calls += "openBackup $uri" }
    override fun restoreBackup() { calls += "restoreBackup" }
    override fun cancelRestore() { calls += "cancelRestore" }
    override fun dismissBackupProblem() { calls += "dismissBackupProblem" }
    override fun resetKamae() { calls += "resetKamae" }
}
