// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ios

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ca.gureet.kitae.backup.Backups
import ca.gureet.kitae.database.ActivityWeeks
import ca.gureet.kitae.database.DatabaseFiles
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.DoneSet
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.ExerciseSession
import ca.gureet.kitae.database.ExerciseSetCount
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.KitaeDatabase
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.NewBest
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.Seed
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.TrainingTotals
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.database.WorkoutWithDay
import ca.gureet.kitae.database.buildKitaeDatabase
import ca.gureet.kitae.model.ActivityLevel
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.BodyProfile
import ca.gureet.kitae.model.Sex
import kotlin.concurrent.Volatile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Kitae's database as the iPhone's Swift uses it (`KitaeDatabase.swift`): the file at [path],
 * opened with Room and prepared as it opens (`buildKitaeDatabase`), with what [seed], the text of
 * the app's `Seed.json`, says. Tests and debug screens use files of their own.
 *
 * Each call waits for its answer, as the iPhone's calls into GRDB did, so the Swift that called
 * them stays as it was; each change is saved whole, in its own transaction where it needs one.
 * Restoring a backup and resetting close the database, change the file and open it again.
 */
class IPhoneDatabase(private val path: String, seed: String) {
    private val seed = Seed.decode(seed)

    @Volatile
    private var database: KitaeDatabase = build()

    private fun build(): KitaeDatabase = Room.databaseBuilder<KitaeDatabase>(path).buildKitaeDatabase(seed)

    /** Opens the database, which Room otherwise does on its first use, so a problem shows now. */
    @Throws(Exception::class)
    fun open() {
        blocking { database.plans().currentId() }
    }

    @Throws(Exception::class)
    fun close() = database.close()

    /** Makes this a new database again: the starter exercises and plan, no plan followed. */
    @Throws(Exception::class)
    fun reset() {
        database.close()
        DatabaseFiles.delete(path)
        database = build()
        open()
    }

    /** Puts the database file at [source] in this one's place, as a backup is restored. */
    @Throws(Exception::class)
    fun install(source: String) {
        database.close()
        DatabaseFiles.replace(path, source)
        database = build()
        open()
    }

    /** Copies the database as it is now to [file], in one file without a log, for a backup. */
    @Throws(Exception::class)
    fun snapshot(file: String) {
        DatabaseFiles.delete(file)
        blocking {
            database.useWriterConnection { connection ->
                connection.usePrepared("VACUUM INTO ?") { statement ->
                    statement.bindText(1, file)
                    statement.step()
                }
            }
        }
        BundledSQLiteDriver().open(file).use { it.execSQL("PRAGMA journal_mode = DELETE") }
    }

    // Plans

    @Throws(Exception::class)
    fun plans(): List<PlanSummary> = blocking { database.plans().all() }

    @Throws(Exception::class)
    fun currentPlanId(): Long? = blocking { database.plans().currentId() }

    @Throws(Exception::class)
    fun setCurrentPlan(planId: Long, today: Long) = blocking { database.plans().setCurrent(planId, today) }

    @Throws(Exception::class)
    fun plan(id: Long): PlanWithDays? = blocking { database.plans().plan(id) }

    @Throws(Exception::class)
    fun dayExercises(dayId: Long): List<DayExerciseWithSets> = blocking { database.plans().dayExercises(dayId) }

    // Changing plans

    @Throws(Exception::class)
    fun createPlan(name: String): Long = blocking { database.planEdits().create(name) }

    @Throws(Exception::class)
    fun renamePlan(planId: Long, name: String) = blocking { database.planEdits().rename(planId, name) }

    @Throws(Exception::class)
    fun deletePlan(planId: Long) = blocking { database.planEdits().delete(planId) }

    @Throws(Exception::class)
    fun planHasExercises(planId: Long): Boolean = blocking { database.planEdits().hasExercises(planId) }

    @Throws(Exception::class)
    fun addDay(planId: Long, name: String): Long = blocking { database.planEdits().addDay(planId, name) }

    @Throws(Exception::class)
    fun renameDay(dayId: Long, name: String) = blocking { database.planEdits().renameDay(dayId, name) }

    @Throws(Exception::class)
    fun deleteDay(dayId: Long) = blocking { database.planEdits().deleteDay(dayId) }

    @Throws(Exception::class)
    fun orderDays(dayIds: List<Long>) = blocking { database.planEdits().orderDays(dayIds) }

    @Throws(Exception::class)
    fun addExercise(dayId: Long, exerciseId: Long, sets: List<SetDraft>): Long =
        blocking { database.planEdits().addExercise(dayId, exerciseId, sets) }

    @Throws(Exception::class)
    fun removeExercise(dayExerciseId: Long) = blocking { database.planEdits().removeExercise(dayExerciseId) }

    @Throws(Exception::class)
    fun orderExercises(dayExerciseIds: List<Long>) = blocking { database.planEdits().orderExercises(dayExerciseIds) }

    @Throws(Exception::class)
    fun suggestedSets(exerciseId: Long, fallback: SetDraft): List<SetDraft> =
        blocking { database.planEdits().suggestedSets(exerciseId, fallback) }

    @Throws(Exception::class)
    fun addPlannedSet(dayExerciseId: Long, set: SetDraft): Long = blocking { database.planEdits().addSet(dayExerciseId, set) }

    @Throws(Exception::class)
    fun updatePlannedSet(plannedSetId: Long, set: SetDraft) = blocking { database.planEdits().updateSet(plannedSetId, set) }

    @Throws(Exception::class)
    fun deletePlannedSet(plannedSetId: Long) = blocking { database.planEdits().deleteSet(plannedSetId) }

    // Workouts

    @Throws(Exception::class)
    fun startWorkout(dayId: Long, today: Long, now: Long): Long = blocking { database.workouts().start(dayId, today, now) }

    @Throws(Exception::class)
    fun activeWorkout(today: Long): WorkoutWithDay? = blocking { database.workouts().active(today) }

    @Throws(Exception::class)
    fun workout(id: Long): WorkoutWithDay? = blocking { database.workouts().workout(id) }

    @Throws(Exception::class)
    fun lastDoneDayId(): Long? = blocking { database.workouts().lastDoneDayId() }

    @Throws(Exception::class)
    fun hasDoneSets(): Boolean = blocking { database.workouts().hasDoneSets() }

    @Throws(Exception::class)
    fun workoutExercises(workoutId: Long, dayId: Long?): List<WorkoutExercise> =
        blocking { database.workouts().exercises(workoutId, dayId) }

    @Throws(Exception::class)
    fun lastKnownSet(exerciseId: Long, dayId: Long?): SetDraft? = blocking { database.workouts().lastKnownSet(exerciseId, dayId) }

    @Throws(Exception::class)
    fun setDone(setId: Long, isDone: Boolean) = blocking { database.workouts().setDone(setId, isDone) }

    @Throws(Exception::class)
    fun updateSet(setId: Long, set: SetDraft) = blocking { database.workouts().update(setId, set) }

    @Throws(Exception::class)
    fun addSet(workoutId: Long, exerciseId: Long, set: SetDraft): Long =
        blocking { database.workouts().addSet(workoutId, exerciseId, set) }

    @Throws(Exception::class)
    fun deleteSet(setId: Long) = blocking { database.workouts().deleteSet(setId) }

    /** Finishes the workout: [mode] is a [FinishMode]'s name, for what happens to skipped sets. */
    @Throws(Exception::class)
    fun finishWorkout(workoutId: Long, mode: String, addNewSets: Boolean, now: Long) =
        blocking { database.workouts().finish(workoutId, FinishMode.valueOf(mode), addNewSets, now) }

    @Throws(Exception::class)
    fun discardWorkout(workoutId: Long) = blocking { database.workouts().discard(workoutId) }

    // History

    @Throws(Exception::class)
    fun workouts(activeId: Long?): List<WorkoutSummary> = blocking { database.history().workouts(activeId) }

    @Throws(Exception::class)
    fun finishedWorkouts(first: Long, last: Long): List<WorkoutSummary> = blocking { database.history().finished(first, last) }

    @Throws(Exception::class)
    fun workoutSummary(id: Long): WorkoutSummary? = blocking { database.history().summary(id) }

    @Throws(Exception::class)
    fun previousWorkoutId(workoutId: Long, dayId: Long?, date: Long): Long? =
        blocking { database.history().previousId(workoutId, dayId, date) }

    @Throws(Exception::class)
    fun doneSets(first: Long, last: Long): Map<Long, List<DoneSet>> = blocking { database.history().doneSets(first, last) }

    // Exercises

    @Throws(Exception::class)
    fun exercises(): List<Exercise> = blocking { database.exercises().all() }

    @Throws(Exception::class)
    fun exercise(id: Long): Exercise? = blocking { database.exercises().exercise(id) }

    /** Adds someone's own exercise: [muscle] is its muscle group as it's stored, like "Chest". */
    @Throws(Exception::class)
    fun createExercise(
        name: String,
        muscle: String,
        isIsometric: Boolean,
        usesWeight: Boolean,
        restSeconds: Int?,
        instructions: String,
        reference: String,
    ): Long = blocking {
        database.exercises().create(draft(name, muscle, isIsometric, usesWeight, restSeconds, instructions, reference))
    }

    @Throws(Exception::class)
    fun updateExercise(
        id: Long,
        name: String,
        muscle: String,
        isIsometric: Boolean,
        usesWeight: Boolean,
        restSeconds: Int?,
        instructions: String,
        reference: String,
    ) = blocking {
        database.exercises().update(id, draft(name, muscle, isIsometric, usesWeight, restSeconds, instructions, reference))
    }

    private fun draft(
        name: String,
        muscle: String,
        isIsometric: Boolean,
        usesWeight: Boolean,
        restSeconds: Int?,
        instructions: String,
        reference: String,
    ) = ExerciseDraft(
        name = name,
        muscle = MuscleGroup.fromStored(muscle) ?: MuscleGroup.Chest,
        isIsometric = isIsometric,
        usesWeight = usesWeight,
        restSeconds = restSeconds,
        instructions = instructions,
        reference = reference,
    )

    @Throws(Exception::class)
    fun deleteExercise(id: Long) = blocking { database.exercises().delete(id) }

    @Throws(Exception::class)
    fun exerciseHistory(exerciseId: Long): List<ExerciseSession> = blocking { database.exercises().history(exerciseId) }

    /** The workout's new bests (named so Objective-C doesn't take it for a "new" method). */
    @Throws(Exception::class)
    fun workoutBests(workoutId: Long): List<NewBest> = blocking { database.exercises().newBests(workoutId) }

    @Throws(Exception::class)
    fun recentExerciseIds(limit: Int): List<Long> = blocking { database.exercises().recentIds(limit) }

    @Throws(Exception::class)
    fun lastSessions(): Map<Long, ExerciseSession> = blocking { database.exercises().lastSessions() }

    // Stats

    @Throws(Exception::class)
    fun totals(): TrainingTotals = blocking { database.stats().totals() }

    @Throws(Exception::class)
    fun trainedDays(first: Long, last: Long): List<Long> = blocking { database.stats().trainedDays(first, last) }

    @Throws(Exception::class)
    fun activity(today: Long, firstWeekday: Int): ActivityWeeks = blocking { database.stats().activity(today, firstWeekday) }

    @Throws(Exception::class)
    fun exerciseSets(first: Long, last: Long): List<ExerciseSetCount> = blocking { database.stats().exerciseSets(first, last) }

    // The body

    @Throws(Exception::class)
    fun bodyLog(): BodyLog = blocking { database.body.log() }

    @Throws(Exception::class)
    fun saveWeighIn(date: Long, weightKg: Double?, bodyFatPercent: Double?, replacingId: Long?) =
        blocking { database.body.save(date, weightKg, bodyFatPercent, replacingId) }

    @Throws(Exception::class)
    fun deleteWeighIn(id: Long) = blocking { database.body.delete(id) }

    /** Saves the profile: [sex] and [activityLevel] as they're stored, like "Female". */
    @Throws(Exception::class)
    fun saveProfile(heightCm: Double?, birthDate: Long?, sex: String?, activityLevel: String?) = blocking {
        database.body.save(BodyProfile(heightCm, birthDate, Sex.fromStored(sex), ActivityLevel.fromStored(activityLevel)))
    }

    // Notes

    @Throws(Exception::class)
    fun notes(): Map<Long, String> = blocking { database.notes.all() }

    @Throws(Exception::class)
    fun saveNote(exerciseId: Long, note: String) = blocking { database.notes.save(exerciseId, note) }

    @Throws(Exception::class)
    fun deleteNote(exerciseId: Long) = blocking { database.notes.delete(exerciseId) }

    // For tests, which check what's stored as Kitae's tests on the iPhone did with GRDB.

    /**
     * Runs [sql] as it is, with [arguments], a JSON array of texts and nulls, bound as text, which
     * SQLite turns into each column's type.
     */
    @Throws(Exception::class)
    fun execute(sql: String, arguments: String) = blocking {
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                statement.bindAll(arguments)
                while (statement.step()) Unit
            }
        }
    }

    /** The id of the row inserted last. */
    @Throws(Exception::class)
    fun lastInsertRowId(): Long = blocking {
        database.useWriterConnection { connection ->
            connection.usePrepared("SELECT last_insert_rowid()") { statement ->
                statement.step()
                statement.getLong(0)
            }
        }
    }

    /**
     * The rows [sql] gives with [arguments] (as in [execute]): a JSON array whose first row is the
     * columns' names, then each row's values as texts and nulls.
     */
    @Throws(Exception::class)
    fun query(sql: String, arguments: String): String = blocking {
        database.useWriterConnection { connection ->
            connection.usePrepared(sql) { statement ->
                statement.bindAll(arguments)
                val count = statement.getColumnCount()
                buildJsonArray {
                    add(buildJsonArray { for (column in 0 until count) add(statement.getColumnName(column)) })
                    while (statement.step()) {
                        add(buildJsonArray { for (column in 0 until count) add(if (statement.isNull(column)) null else statement.getText(column)) })
                    }
                }.toString()
            }
        }
    }

    companion object {
        /** Runs [statements] on the SQLite file at [file], made if it isn't there: for tests. */
        @Throws(Exception::class)
        fun executeIn(file: String, statements: List<String>) {
            BundledSQLiteDriver().open(file).use { connection ->
                for (sql in statements) connection.execSQL(sql)
            }
        }

        /**
         * Checks a backup's database file: Kitae's, at a version this app opens, and whole.
         * Throws `BackupException` when it isn't.
         */
        @Throws(Exception::class)
        fun checkBackup(file: String) = Backups.check(file)
    }
}

private fun SQLiteStatement.bindAll(arguments: String) {
    Json.parseToJsonElement(arguments).jsonArray.forEachIndexed { index, value ->
        val text = if (value is JsonNull) null else value.jsonPrimitive.content
        if (text == null) bindNull(index + 1) else bindText(index + 1, text)
    }
}

/** Runs [block] to its end on the calling thread. */
private fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }
