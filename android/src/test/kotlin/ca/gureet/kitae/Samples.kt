// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import ca.gureet.kitae.app.AppActions
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.app.BackupPlace
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.ExerciseDetails
import ca.gureet.kitae.app.ExerciseList
import ca.gureet.kitae.app.HistoryPage
import ca.gureet.kitae.app.Library
import ca.gureet.kitae.app.PageSource
import ca.gureet.kitae.app.TextSize
import ca.gureet.kitae.app.WorkoutDone
import ca.gureet.kitae.app.WorkoutRecord
import ca.gureet.kitae.app.WorkoutState
import ca.gureet.kitae.app.YouData
import ca.gureet.kitae.database.ActivityWeeks
import ca.gureet.kitae.database.DayExercise
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.ExerciseSession
import ca.gureet.kitae.database.ExerciseSetCount
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.NewBest
import ca.gureet.kitae.database.Plan
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.PlannedSet
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.TrainingDay
import ca.gureet.kitae.database.TrainingTotals
import ca.gureet.kitae.database.Workout
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSet
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.database.historyMonths
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.BodyEntry
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.BodyProfile
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.motion.ExerciseMotions
import ca.gureet.kitae.muscles.ExerciseMuscles
import ca.gureet.kitae.muscles.MuscleVolume
import java.io.File

/** Example data like the Kamae canvas's: the starter plan, with Push A in progress. */
object Samples {
    /** Saturday 10 October 2026, 18:00 UTC. */
    const val NOW = 1_791_741_600_000L

    /** The muscle maps and animations, from the iPhone's files, as the app's assets have them. */
    val library: Library by lazy {
        Library(
            ExerciseMuscles.decode(File("../ios/Kitae/Exercises/Muscles/ExerciseMuscles.json").readText()),
            ExerciseMotions.decode(File("../ios/Kitae/Exercises/Motion/ExerciseMotions.json").readText()),
        )
    }

    const val NOTE = "Seat on 4, grip just outside the rings."

    private val names = listOf(
        "Incline Bench" to "barbell-incline-bench-press",
        "Chest Fly" to "dumbbell-flyes",
        "Leg Raises" to "lying-leg-raises",
        "Overhead Extensions" to "dumbbell-overhead-tricep-extensions",
        "Tricep Push Down" to "tricep-pushdowns",
    )
    private val exercises = names.mapIndexed { index, (name, key) ->
        Exercise(name = name, target = if (index < 2) "Chest" else if (index == 2) "Abs" else "Triceps", id = index + 1L, usesWeight = name != "Leg Raises", libraryKey = key)
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

    // History, as the canvas has it on Thursday 22 October 2026.

    private const val OCTOBER_22 = 20_748L

    /** Weigh-ins most weeks since early September, with body fat now and then, and a height. */
    val bodyLog = BodyLog(
        BodyProfile(heightCm = 178.0, birthDate = OCTOBER_22 - 30 * 365 - 120),
        listOf(80.6 to 19.4, 80.2 to null, 79.9 to null, 79.5 to 18.9, 79.4 to null, 78.9 to null, 78.4 to 18.5).mapIndexed { index, (kg, fat) ->
            BodyEntry(index + 1L, OCTOBER_22 - 42 + index * 7L, kg, fat)
        },
    )

    val today = AppState(
        loaded = true,
        plans = plans,
        plan = plan,
        upNextDayId = 1,
        hadDoneSets = true,
        library = library,
        notes = mapOf(1L to NOTE, 101L to NOTE),
        bodyLog = bodyLog,
    )

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

    private val chestAndBack = chest.mapIndexed { index, (name, _) ->
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
            libraryKey = if (name == "Incline Bench") "barbell-incline-bench-press" else null,
        )
    } + listOf(
        Exercise(name = "Bent-over Rows", target = "UpperBack", id = 120),
        Exercise(name = "Face Pulls", target = "UpperBack", id = 121),
        Exercise(name = "Lat Pull-down", target = "Lats", id = 122),
    )

    val exerciseList = ExerciseList(
        chestAndBack,
        chest.mapIndexedNotNull { index, (_, set) -> set?.let { 100L + index to ExerciseSession(6, OCTOBER_22 - 1, "Push A", listOf(it)) } }.toMap(),
        recentIds = listOf(120, 101, 122),
    )

    /** Incline Bench's heaviest sets from September 16 to October 21. */
    val inclineBench = ExerciseDetails(
        chestAndBack[1],
        listOf(26.25, 25.0, 23.75, 22.5, 21.25).mapIndexed { index, kg ->
            ExerciseSession(20L - index, OCTOBER_22 - 1 - index * 9L, "Push A", listOf(SetDraft(12, kg), SetDraft(12, kg), SetDraft(10, kg)))
        },
    )

    /** A pull day's done sets: rows, curls, pull-downs and face pulls, 15 in all. */
    fun pullSets(): List<DoneSet> = listOf(
        Triple("barbell-bent-over-rows", 12, 25.0),
        Triple("ez-bar-preacher-curls", 12, 25.0),
        Triple("lat-pulldowns", 10, 45.0),
        Triple("face-pulls", 12, 25.0),
        Triple("dumbbell-curls", 10, 12.5),
    ).flatMap { (key, reps, kg) -> List(3) { DoneSet(120L, key, "UpperBack", false, reps, kg, SetType.Standard) } }

    /** Pull A's summary, with the four records the canvas has. */
    val workoutDone = WorkoutDone(
        history[1],
        pullSets(),
        listOf(
            NewBest(Exercise(name = "Bent-over Rows", target = "UpperBack"), NewBest.Record.Heaviest, SetDraft(12, 25.0), SetDraft(12, 23.75)),
            NewBest(Exercise(name = "Preacher Curls", target = "Biceps"), NewBest.Record.Heaviest, SetDraft(12, 25.0), SetDraft(12, 23.75)),
            NewBest(Exercise(name = "Lat Pull-down", target = "Lats"), NewBest.Record.MostReps, SetDraft(10, 45.0), SetDraft(8, 45.0)),
            NewBest(Exercise(name = "Face Pulls", target = "UpperBack"), NewBest.Record.Heaviest, SetDraft(12, 25.0), SetDraft(12, 23.75)),
        ),
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
    override suspend fun history(): HistoryPage = HistoryPage(
        historyMonths(Samples.history, activeId = 7),
        Samples.history.filter { it.doneCount > 0 }.associate { it.workout.id to Samples.pullSets() },
    )

    override suspend fun workoutDone(id: Long, withBests: Boolean): WorkoutDone? =
        Samples.workoutDone.takeIf { id == it.summary.workout.id }?.let { if (withBests) it else it.copy(bests = emptyList()) }

    override suspend fun workoutRecord(id: Long): WorkoutRecord? = Samples.pastWorkout.takeIf { id == it.summary.workout.id }

    override suspend fun exerciseList(recent: Int): ExerciseList = Samples.exerciseList

    override suspend fun exerciseDetails(id: Long): ExerciseDetails? = Samples.inclineBench.takeIf { id == it.exercise.id }

    override suspend fun plan(id: Long): PlanWithDays? = Samples.plan.takeIf { id == it.plan.id }

    /** Three or four days a week for the last 16 weeks; a push day and a pull day in the last 7. */
    override suspend fun youData(today: Long, firstWeekday: Int): YouData = YouData(
        TrainingTotals(lifts = 288, days = 20, exercises = 169),
        ActivityWeeks(today, firstWeekday, (0L until 112L).filter { it % 7 in setOf(0L, 2L, 4L) || it % 11 == 3L }.map { today - it }.toSet()),
        MuscleVolume(
            listOf(
                "barbell-incline-bench-press" to 6,
                "dumbbell-flyes" to 3,
                "tricep-pushdowns" to 3,
                "lat-pulldowns" to 6,
                "barbell-bent-over-rows" to 6,
                "dumbbell-curls" to 3,
            ).map { (key, sets) -> ExerciseSetCount(Exercise(name = key, target = "Chest", libraryKey = key), sets) },
            Samples.library.muscles,
        ),
        // The pull day and the push day this week.
        Samples.history.filter { it.workout.id == 6L || it.workout.id == 4L },
        mapOf(6L to Samples.pullSets(), 4L to Samples.pullSets().take(12)),
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
    override fun saveNote(exerciseId: Long, note: String) { calls += "saveNote $exerciseId $note" }
    override fun closeSummary() { calls += "closeSummary" }
    override fun saveWeighIn(date: Long, weightKg: Double?, bodyFatPercent: Double?, replacingId: Long?) {
        calls += "saveWeighIn $date $weightKg $bodyFatPercent $replacingId"
    }
    override fun deleteWeighIn(id: Long) { calls += "deleteWeighIn $id" }
    override fun saveProfile(profile: BodyProfile) { calls += "saveProfile $profile" }
    override fun finishWelcome() { calls += "finishWelcome" }
    override fun setTextSize(size: TextSize?) { calls += "setTextSize $size" }
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
