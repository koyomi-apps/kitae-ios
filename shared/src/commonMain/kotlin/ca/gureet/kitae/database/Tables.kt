// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.RoomWarnings

// The tables of version 10, as the iPhone has them in `ios/Kitae/Database/Schema.swift`. The
// classes are named as the iPhone names what they hold, and keep the stored table and column
// names, which come from Kitae for Android. Each declares its columns in the stored order, so
// the schema Room exports matches the iPhone's file column for column. Changing anything here
// changes the identity hash, and neither app could open the other's database.

/** A workout: a training day done, or being done, on a date. Stored as a session. */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Plan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TrainingDay::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("planId"), Index("routineId")],
)
data class Workout(
    /** The day, in days since 1970 where the phone was. */
    val date: Long,
    val planId: Long?,
    @ColumnInfo(name = "routineId") val dayId: Long?,
    @ColumnInfo(defaultValue = "1") val isFinished: Boolean = true,
    /** Milliseconds since 1970. Workouts from older versions of Kitae for Android have none. */
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "exercises",
    indices = [Index("libraryKey", unique = true)],
)
data class Exercise(
    val name: String,
    /** The muscle it mainly works, as stored: "Chest", "UpperBack" and so on. */
    val target: String,
    /** A link to a video or page that shows it. */
    val reference: String? = null,
    /** Held, like a plank, so its sets count seconds instead of reps. */
    val isIsometric: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Rest after each of its sets, unless the set has its own. Null uses the default. */
    val restSeconds: Int? = null,
    /** How to do it, one step per line. */
    val instructions: String? = null,
    /**
     * One of Kitae's own exercises' lasting name, like "barbell-bench-press", which never
     * changes and is never reused. People's own exercises have none.
     */
    val libraryKey: String? = null,
    /** False for exercises done with the body alone, like sit-ups: their sets have no weight. */
    @ColumnInfo(defaultValue = "1") val usesWeight: Boolean = true,
)

@Entity(tableName = "plans")
data class Plan(
    val name: String,
    // Kitae for Android's earlier columns. Neither app reads them, but Room's schema has them.
    @ColumnInfo(defaultValue = "NULL") val description: String? = null,
    @ColumnInfo(defaultValue = "NULL") val difficulty: String? = null,
    @ColumnInfo(defaultValue = "NULL") val focus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val equipment: String? = null,
    @ColumnInfo(defaultValue = "NULL") val time: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** When each plan was followed. The plan being followed is the one whose row hasn't ended. */
@Entity(
    tableName = "plan_history",
    foreignKeys = [
        ForeignKey(
            entity = Plan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("planId", "start", "end")],
)
data class PlanHistory(
    val planId: Long?,
    /** In days since 1970, like [end]. */
    val start: Long,
    @ColumnInfo(defaultValue = "NULL") val end: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** A named day of a plan, like Push A or Legs. Stored as a routine. */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = Plan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class TrainingDay(
    val planId: Long,
    val name: String,
    val position: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** An exercise in a training day. */
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingDay::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class DayExercise(
    @ColumnInfo(name = "routineId") val dayId: Long,
    val exerciseId: Long,
    val position: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

/** A set as it's planned for an exercise of a training day. */
@Entity(
    tableName = "routine_sets",
    foreignKeys = [
        ForeignKey(
            entity = DayExercise::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineExerciseId")],
)
data class PlannedSet(
    @ColumnInfo(name = "routineExerciseId") val dayExerciseId: Long,
    /** Reps, or seconds for an exercise that's held. */
    val reps: Int,
    /** Always kilograms. */
    @ColumnInfo(name = "weight") val weightKg: Double,
    /** The set's type as stored: "Warmup", "Standard", "Failure", "Drop" or "RestPause". */
    val type: String,
    val position: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Rest after this set. Null uses the exercise's rest, or the default. */
    val restSeconds: Int? = null,
) {
    val setType: SetType
        get() = SetType.fromStored(type)
}

/**
 * A set of a workout.
 *
 * Room warns that no index covers `exerciseId` on its own. The stored schema has none, and adding
 * one would change the identity hash.
 */
@Suppress(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannedSet::class,
            parentColumns = ["id"],
            childColumns = ["routineSetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId", "exerciseId"), Index("routineSetId")],
)
data class WorkoutSet(
    /** Reps, or seconds for an exercise that's held. */
    val reps: Int,
    /** Always kilograms. */
    @ColumnInfo(name = "weight") val weightKg: Double,
    /** The set's type as stored, like [PlannedSet.type]. */
    val type: String,
    /** Its place in the workout. */
    val order: Int,
    @ColumnInfo(name = "sessionId") val workoutId: Long,
    val exerciseId: Long,
    /** Reps in reserve, which Kitae for Android stores with each set but doesn't ask for. */
    val rir: Int,
    @ColumnInfo(name = "isCompleted", defaultValue = "1") val isDone: Boolean = true,
    /** The planned set it comes from. Sets added during the workout have none. */
    @ColumnInfo(name = "routineSetId") val plannedSetId: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Rest after this set. Null uses the exercise's rest, or the default. */
    val restSeconds: Int? = null,
) {
    val setType: SetType
        get() = SetType.fromStored(type)

    /** Added during the workout rather than planned. */
    val isAdded: Boolean
        get() = plannedSetId == null
}

/**
 * How much each set type counted towards Kitae for Android's performance rating. Kitae for
 * Android joins on this table, so every set type has a row.
 */
@Entity(tableName = "set_type")
data class SetTypeModifier(
    @PrimaryKey val type: String,
    val modifier: Double,
)
