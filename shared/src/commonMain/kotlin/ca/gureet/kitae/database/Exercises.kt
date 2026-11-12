// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.SkipQueryVerification
import androidx.room3.Transaction

/**
 * The exercises Kitae knows, its own and people's, read and written as the iPhone does
 * (`Exercises.swift`), which does it the way Kitae for Android does.
 */
@Dao
abstract class Exercises {
    @Query("SELECT * FROM `exercises` ORDER BY `name` COLLATE NOCASE ASC, `id` ASC")
    abstract suspend fun all(): List<Exercise>

    @Query("SELECT * FROM `exercises` WHERE `id` = :id")
    abstract suspend fun exercise(id: Long): Exercise?

    /** Adds an exercise and returns its id. Names are unique, whatever their case. */
    @Transaction
    open suspend fun create(draft: ExerciseDraft): Long =
        insertExercise(
            name = usableName(draft.name, excludingId = null),
            target = draft.muscle.stored,
            reference = savedReference(draft.reference),
            isIsometric = draft.isIsometric,
            usesWeight = draft.usesWeight,
            restSeconds = draft.restSeconds,
            instructions = savedInstructions(draft.instructions),
        )

    /** Saves an exercise's details. */
    @Transaction
    open suspend fun update(id: Long, draft: ExerciseDraft) {
        updateExercise(
            id = id,
            name = usableName(draft.name, excludingId = id),
            target = draft.muscle.stored,
            reference = savedReference(draft.reference),
            isIsometric = draft.isIsometric,
            usesWeight = draft.usesWeight,
            restSeconds = draft.restSeconds,
            instructions = savedInstructions(draft.instructions),
        )
    }

    /** Deletes an exercise: it goes from every plan, its sets from past workouts, and its note. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteExercise(id)
        deleteNote(id)
    }

    /** The sets of an exercise done in each workout, newest first. */
    @Transaction
    open suspend fun history(exerciseId: Long): List<ExerciseSession> {
        val sessions = mutableListOf<ExerciseSession>()
        for (row in doneSetsOf(exerciseId)) {
            val last = sessions.lastOrNull()
            if (last?.id == row.workoutId) {
                sessions[sessions.lastIndex] = last.copy(sets = last.sets + row.draft)
            } else {
                sessions += ExerciseSession(row.workoutId, row.date, row.dayName, listOf(row.draft))
            }
        }
        return sessions
    }

    /** For each exercise, by id, the sets done in the newest workout that had any. */
    @Transaction
    open suspend fun lastSessions(): Map<Long, ExerciseSession> {
        val last = HashMap<Long, ExerciseSession>()
        for (row in allDoneSets()) {
            val session = last[row.exerciseId]
            if (session == null) {
                last[row.exerciseId] = ExerciseSession(row.workoutId, row.date, sets = listOf(row.draft))
            } else if (session.id == row.workoutId) {
                last[row.exerciseId] = session.copy(sets = session.sets + row.draft)
            }
        }
        return last
    }

    /** The name, trimmed, if no other exercise has it. Case doesn't make a name different. */
    private suspend fun usableName(name: String, excludingId: Long?): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw NameProblemException(NameProblem.Empty)
        if (exerciseNamed(trimmed, excludingId)) throw NameProblemException(NameProblem.ExerciseTaken)
        return trimmed
    }

    @Query("SELECT EXISTS (SELECT 1 FROM `exercises` WHERE `name` = :name COLLATE NOCASE AND `id` IS NOT :excludingId)")
    protected abstract suspend fun exerciseNamed(name: String, excludingId: Long?): Boolean

    @Query(
        """
        INSERT INTO `exercises` (`name`, `target`, `reference`, `isIsometric`, `usesWeight`, `restSeconds`, `instructions`)
        VALUES (:name, :target, :reference, :isIsometric, :usesWeight, :restSeconds, :instructions)
        """,
    )
    protected abstract suspend fun insertExercise(
        name: String,
        target: String,
        reference: String?,
        isIsometric: Boolean,
        usesWeight: Boolean,
        restSeconds: Int?,
        instructions: String?,
    ): Long

    @Query(
        """
        UPDATE `exercises`
        SET `name` = :name, `target` = :target, `reference` = :reference, `isIsometric` = :isIsometric,
        `usesWeight` = :usesWeight, `restSeconds` = :restSeconds, `instructions` = :instructions
        WHERE `id` = :id
        """,
    )
    protected abstract suspend fun updateExercise(
        id: Long,
        name: String,
        target: String,
        reference: String?,
        isIsometric: Boolean,
        usesWeight: Boolean,
        restSeconds: Int?,
        instructions: String?,
    )

    @Query("DELETE FROM `exercises` WHERE `id` = :id")
    protected abstract suspend fun deleteExercise(id: Long)

    // Kitae's own table, outside Room's schema (see ExerciseNotes).
    @SkipQueryVerification
    @Query("DELETE FROM `exercise_notes` WHERE `exerciseId` = :exerciseId")
    protected abstract suspend fun deleteNote(exerciseId: Long)

    @Query(
        """
        SELECT `sets`.`exerciseId`, `sets`.`sessionId` AS `workoutId`, `sessions`.`date`, `routines`.`name` AS `dayName`,
        `sets`.`reps`, `sets`.`weight`, `sets`.`type`, `sets`.`restSeconds`
        FROM `sets`
        INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
        LEFT JOIN `routines` ON `routines`.`id` = `sessions`.`routineId`
        WHERE `sets`.`exerciseId` = :exerciseId
        AND `sets`.`isCompleted` = 1
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` ASC, `sets`.`id` ASC
        """,
    )
    protected abstract suspend fun doneSetsOf(exerciseId: Long): List<DoneSetRow>

    @Query(
        """
        SELECT `sets`.`exerciseId`, `sets`.`sessionId` AS `workoutId`, `sessions`.`date`, NULL AS `dayName`,
        `sets`.`reps`, `sets`.`weight`, `sets`.`type`, `sets`.`restSeconds`
        FROM `sets`
        INNER JOIN `sessions` ON `sessions`.`id` = `sets`.`sessionId`
        WHERE `sets`.`isCompleted` = 1
        ORDER BY `sessions`.`date` DESC, `sessions`.`id` DESC, `sets`.`order` ASC, `sets`.`id` ASC
        """,
    )
    protected abstract suspend fun allDoneSets(): List<DoneSetRow>

    companion object {
        /**
         * A reference as it's saved: trimmed, with https:// in front when it has no scheme, or
         * null when it's empty.
         */
        fun savedReference(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            return if ("://" in trimmed) trimmed else "https://$trimmed"
        }

        fun savedInstructions(text: String): String? = text.trim().ifEmpty { null }
    }
}

/** A done set as the exercise queries read it. */
data class DoneSetRow(
    val exerciseId: Long,
    val workoutId: Long,
    val date: Long,
    val dayName: String?,
    val reps: Int,
    val weight: Double,
    val type: String,
    val restSeconds: Int?,
) {
    val draft: SetDraft
        get() = SetDraft(reps, weight, SetType.fromStored(type), restSeconds)
}

/** The muscle an exercise mainly works. The stored names are Kitae for Android's. */
enum class MuscleGroup(val stored: String) {
    Biceps("Biceps"),
    Triceps("Triceps"),
    Shoulders("Shoulders"),
    Quads("Quads"),
    Hamstrings("Hamstrings"),
    Calves("Calves"),
    Glutes("Glutes"),
    Core("Core"),
    Chest("Chest"),
    Traps("Traps"),
    Lats("Lats"),
    UpperBack("UpperBack"),
    ;

    companion object {
        /** A muscle as it's stored, or null for one this version doesn't know. */
        fun fromStored(stored: String): MuscleGroup? = entries.firstOrNull { it.stored == stored }
    }
}

/** An exercise's details while they're being edited. */
data class ExerciseDraft(
    val name: String = "",
    val muscle: MuscleGroup = MuscleGroup.Chest,
    val isIsometric: Boolean = false,
    val usesWeight: Boolean = true,
    val restSeconds: Int? = null,
    val instructions: String = "",
    val reference: String = "",
) {
    constructor(exercise: Exercise) : this(
        name = exercise.name,
        muscle = exercise.muscle ?: MuscleGroup.Chest,
        isIsometric = exercise.isIsometric,
        usesWeight = exercise.usesWeight,
        restSeconds = exercise.restSeconds,
        instructions = exercise.instructions.orEmpty(),
        reference = exercise.reference.orEmpty(),
    )
}

/** The sets of one exercise done in one workout. */
data class ExerciseSession(
    /** The workout's id. */
    val id: Long,
    val date: Long,
    /** The day's name, while the day still exists. */
    val dayName: String? = null,
    val sets: List<SetDraft>,
) {
    /** The sets that count: warm-ups only count when nothing else was done. */
    val workSets: List<SetDraft>
        get() = sets.filter { it.type != SetType.Warmup }.ifEmpty { sets }

    val heaviest: SetDraft?
        get() = heaviestSet(workSets)

    /** On an assisted machine: the set with the least help. */
    val leastHelp: SetDraft?
        get() = leastHelpSet(workSets)

    val mostReps: Int
        get() = workSets.maxOfOrNull { it.reps } ?: 0

    /**
     * The set to show for it: the heaviest, the one with the least help on an assisted machine,
     * or the one with the most reps when the exercise doesn't use weight.
     */
    fun best(usesWeight: Boolean, assisted: Boolean = false): SetDraft? {
        if (!usesWeight) return workSets.maxByOrNull { it.reps }
        return if (assisted) leastHelp else heaviest
    }
}

/** The heaviest set, and of the heaviest, the one with the most reps. */
fun heaviestSet(sets: List<SetDraft>): SetDraft? =
    sets.maxWithOrNull(compareBy<SetDraft> { it.weightKg }.thenBy { it.reps })

/**
 * On an assisted machine, whose weight is the help it gives: the set with the least help, and of
 * those, the one with the most reps.
 */
fun leastHelpSet(sets: List<SetDraft>): SetDraft? =
    sets.maxWithOrNull(compareBy<SetDraft> { -it.weightKg }.thenBy { it.reps })

/**
 * How an exercise went over its last workouts, for the chart on its page: the heaviest set each
 * time, the least help on an assisted machine, or the most reps or longest hold when it's done
 * without weight. [history] is newest first, as [Exercises.history] reads it.
 */
class ExerciseProgress(
    history: List<ExerciseSession>,
    isIsometric: Boolean,
    usesWeight: Boolean = true,
    assisted: Boolean = false,
) {
    enum class Measure {
        Weight,

        /** An assisted machine's help, which goes down as people get stronger. */
        Help,
        Reps,
        Seconds,
        ;

        /** Kilograms, of weight or of help. */
        val isWeight: Boolean
            get() = this == Weight || this == Help
    }

    data class Point(
        /** The workout's id. */
        val id: Long,
        val date: Long,
        /** Kilograms, reps or seconds. */
        val value: Double,
    )

    val measure: Measure

    /** Oldest first. */
    val points: List<Point>

    init {
        val recent = history.take(MAX_POINTS).reversed()
        val byWeight = usesWeight && recent.any { (it.heaviest?.weightKg ?: 0.0) > 0 }
        measure = when {
            byWeight -> if (assisted) Measure.Help else Measure.Weight
            isIsometric -> Measure.Seconds
            else -> Measure.Reps
        }
        points = recent.map { session ->
            val value = when (measure) {
                Measure.Weight -> session.heaviest?.weightKg ?: 0.0
                Measure.Help -> session.leastHelp?.weightKg ?: 0.0
                Measure.Reps, Measure.Seconds -> session.mostReps.toDouble()
            }
            Point(session.id, session.date, value)
        }
    }

    /** The last value less the first; less help shows as a drop. */
    val change: Double
        get() = (points.lastOrNull()?.value ?: 0.0) - (points.firstOrNull()?.value ?: 0.0)

    companion object {
        /** How many workouts the chart shows. */
        const val MAX_POINTS = 12
    }
}

/** An exercise's best sets ever, from its [history] (newest first). */
class ExerciseRecords(history: List<ExerciseSession>, assisted: Boolean = false) {
    /**
     * The heaviest set, or on an assisted machine the one with the least help, with the most
     * reps among those.
     */
    val best: SetDraft?

    /**
     * The set with the most reps, or the longest hold, and the heaviest of those (on an assisted
     * machine, the one with the least help).
     */
    val mostReps: SetDraft?

    val workoutCount: Int = history.size

    /** The day of the workout with the best set, the latest if it was done more than once. */
    val bestDate: Long?

    init {
        val sets = history.flatMap { it.workSets }
        best = if (assisted) leastHelpSet(sets) else heaviestSet(sets)
        bestDate = best?.let { best -> history.firstOrNull { best in it.workSets }?.date }
        val load = { set: SetDraft -> if (assisted) -set.weightKg else set.weightKg }
        mostReps = sets.maxWithOrNull(compareBy<SetDraft> { it.reps }.thenBy(load))
    }
}
