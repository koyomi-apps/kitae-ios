// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Kitae's database, version 10: the same tables as Kitae for iPhone's, so that each app opens
 * the other's files and backups.
 *
 * Room checks a database's identity hash before opening it, and version 10's has to be the
 * iPhone's, `a8dd8c0661148678f11cce771546f37e`. Room exports the schema to `shared/schemas`,
 * and `RoomSchemaTest` compares it with the iPhone's (`ios/KitaeTests/RoomSchema10.json`).
 */
@Database(
    entities = [
        Workout::class,
        Exercise::class,
        Plan::class,
        PlanHistory::class,
        TrainingDay::class,
        DayExercise::class,
        PlannedSet::class,
        WorkoutSet::class,
        SetTypeModifier::class,
    ],
    version = 10,
)
@ConstructedBy(KitaeDatabaseConstructor::class)
abstract class KitaeDatabase : RoomDatabase() {
    abstract fun plans(): Plans

    abstract fun workouts(): Workouts
}

/** Room's compiler writes this for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object KitaeDatabaseConstructor : RoomDatabaseConstructor<KitaeDatabase> {
    override fun initialize(): KitaeDatabase
}

/**
 * Builds Kitae's database from this builder, which each platform makes for its file. SQLite
 * comes with the app, as Kitae's own tables need a newer one than older Android versions have.
 * Databases and backups from versions 8 and 9 are brought up to 10, and each database is
 * prepared as it opens (see [Preparation]), with what [seed] says.
 */
fun RoomDatabase.Builder<KitaeDatabase>.buildKitaeDatabase(seed: Seed): KitaeDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(*kitaeMigrations(seed))
        .addCallback(Preparation(seed))
        .build()

/**
 * What the iPhone does as its database opens (`KitaeDatabase.prepare`). A new database starts
 * with the set types, and the seed's starter exercises and plan; no plan is followed until
 * people pick one. Every database then gets Kitae's own tables and the library's exercises it
 * hasn't been given yet, whether it's new, brought up from 8 or 9, restored or reset.
 */
private class Preparation(private val seed: Seed) : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        connection.transaction {
            for ((type, modifier) in setTypeModifiers) {
                connection.execute(
                    "INSERT OR REPLACE INTO `set_type` (`type`, `modifier`) VALUES (?, ?)",
                    type,
                    modifier.toDouble(),
                )
            }
            seed.insert(connection)
        }
    }

    override suspend fun onOpen(connection: SQLiteConnection) {
        connection.transaction {
            BodyTables.create(connection)
            ExerciseNotes.create(connection)
            ExerciseLibrary.create(connection, seed)
            ExerciseLibrary.sync(connection, seed.exercises)
        }
    }
}

/**
 * How much each set type counted towards Kitae for Android's performance rating, as the iPhone
 * writes them (`Schema.setTypeModifiers`): 32-bit floats, stored as they land in the database.
 */
private val setTypeModifiers = listOf(
    "Standard" to 1.0f,
    "Drop" to 1.35f,
    "RestPause" to 1.2f,
    "Warmup" to 0f,
    "Failure" to 1.15f,
)
