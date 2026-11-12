// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
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
abstract class KitaeDatabase : RoomDatabase()

/** Room's compiler writes this for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object KitaeDatabaseConstructor : RoomDatabaseConstructor<KitaeDatabase> {
    override fun initialize(): KitaeDatabase
}

/**
 * Builds Kitae's database from this builder, which each platform makes for its file. SQLite
 * comes with the app, as Kitae's own tables need a newer one than older Android versions have, and
 * databases and backups from versions 8 and 9 are brought up to 10 with what [seed] says.
 */
fun RoomDatabase.Builder<KitaeDatabase>.buildKitaeDatabase(seed: Seed): KitaeDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(*kitaeMigrations(seed))
        .build()
