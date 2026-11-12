// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.ExerciseProgress
import ca.gureet.kitae.database.ExerciseRecords
import ca.gureet.kitae.database.ExerciseSession
import ca.gureet.kitae.database.HistoryMonth
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSummary

/** A past workout as its page shows it (the iPhone's `WorkoutRecord`). */
data class WorkoutRecord(
    val summary: WorkoutSummary,
    val exercises: List<WorkoutExercise>,
    /** The workout of the same day before it that had a set done. */
    val previousId: Long?,
)

/** Every exercise, with the sets of each one's last workout. */
data class ExerciseList(
    val exercises: List<Exercise>,
    val lastSessions: Map<Long, ExerciseSession> = emptyMap(),
    /** The exercises done most recently, newest first, for the picker. */
    val recentIds: List<Long> = emptyList(),
)

/** What an exercise's page shows (the iPhone's `ExercisePage`). */
data class ExerciseDetails(
    val exercise: Exercise,
    /** Newest first. */
    val history: List<ExerciseSession>,
) {
    val progress = ExerciseProgress(history, exercise.isIsometric, exercise.usesWeight, exercise.isAssisted)
    val records = ExerciseRecords(history, exercise.isAssisted)
}

/** Where the pages' data comes from: the database, or samples in the screen tests. */
interface PageSource {
    suspend fun historyMonths(): List<HistoryMonth>

    suspend fun workoutRecord(id: Long): WorkoutRecord?

    /** Every exercise; [recent] of them also as done most recently. */
    suspend fun exerciseList(recent: Int = 0): ExerciseList

    suspend fun exerciseDetails(id: Long): ExerciseDetails?

    suspend fun plan(id: Long): PlanWithDays?

    /** You's totals and the last 16 weeks, with weeks starting on [firstWeekday] (1 for Sunday). */
    suspend fun youData(today: Long, firstWeekday: Int): YouData
}

/** Settings, and this phone's backups. Folders and files are Android's content URIs. */
interface SettingsActions {
    fun updateSettings(change: (ca.gureet.kitae.model.AppSettings) -> ca.gureet.kitae.model.AppSettings)

    fun setBackupFolder(uri: String)

    fun backUpNow()

    /** Checks a backup, then asks whether to restore it. */
    fun openBackup(uri: String)

    fun restoreBackup()

    fun cancelRestore()

    fun dismissBackupProblem()

    fun resetKamae()
}

/**
 * Changes made outside the workout: past workouts, exercises and plans. Names that can't be used
 * are thrown as the shared module's `NameProblemException`, to show beside the name.
 */
interface EditActions {
    fun deleteWorkout(id: Long)

    /** Saves a new exercise, or changes exercise [id]. Returns its id. */
    suspend fun saveExercise(draft: ExerciseDraft, id: Long?): Long

    fun deleteExercise(id: Long)

    suspend fun createPlan(name: String): Long

    suspend fun renamePlan(planId: Long, name: String)

    fun deletePlan(planId: Long)

    suspend fun addDay(planId: Long, name: String): Long

    suspend fun renameDay(dayId: Long, name: String)

    fun deleteDay(dayId: Long)

    fun orderDays(dayIds: List<Long>)

    /** Adds an exercise to a day with the sets done last time, or three new ones. */
    suspend fun addExerciseToDay(dayId: Long, exerciseId: Long): Long

    fun removeFromDay(dayExerciseId: Long)

    fun orderExercises(dayExerciseIds: List<Long>)

    /** Adds a set like [last], the exercise's last one; a warm-up is followed by a standard set. */
    fun addPlannedSet(dayExerciseId: Long, last: SetDraft?)

    fun updatePlannedSet(plannedSetId: Long, set: SetDraft)

    fun deletePlannedSet(plannedSetId: Long)
}

/** The order of the muscle chips, and of the sections of exercises (`ExerciseGroups.swift`). */
val muscleOrder = listOf(
    MuscleGroup.Chest, MuscleGroup.UpperBack, MuscleGroup.Lats, MuscleGroup.Shoulders,
    MuscleGroup.Biceps, MuscleGroup.Triceps, MuscleGroup.Traps, MuscleGroup.Core,
    MuscleGroup.Quads, MuscleGroup.Hamstrings, MuscleGroup.Glutes, MuscleGroup.Calves,
)

/** The exercises of one muscle, as a section of a list. [muscle] is null for ones Kamae doesn't know. */
data class ExerciseGroup(val muscle: MuscleGroup?, val exercises: List<Exercise>)

/** The exercises that match a search and a muscle, in sections by muscle. */
fun exerciseGroups(exercises: List<Exercise>, muscle: MuscleGroup?, search: String): List<ExerciseGroup> {
    val query = search.trim()
    val found = exercises.filter { exercise ->
        (muscle == null || exercise.muscle == muscle) && (query.isEmpty() || exercise.name.contains(query, ignoreCase = true))
    }
    val groups = muscleOrder.mapNotNull { group ->
        found.filter { it.muscle == group }.takeIf { it.isNotEmpty() }?.let { ExerciseGroup(group, it) }
    }
    val others = found.filter { it.muscle == null }
    return if (others.isEmpty()) groups else groups + ExerciseGroup(null, others)
}
