// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest

/** Kitae's seed: the iPhone's `Seed.json`, which `shared/build.gradle.kts` points the tests to. */
val bundledSeed: Seed by lazy { Seed.decode(File(System.getProperty("kitae.seed")).readText()) }

/** A test with its own database file, deleted afterwards. */
abstract class DatabaseFileTest {
    private val folder = createTempDirectory("kitae")

    /** The database file, named as Kitae names it. */
    val file: Path = folder.resolve("kenko_database")

    @AfterTest
    fun deleteFiles() {
        folder.toFile().deleteRecursively()
    }

    /** Opens the file as the apps do, making a new database if there's none. */
    fun open(seed: Seed = bundledSeed): KitaeDatabase =
        Room.databaseBuilder(file.toString()) { KitaeDatabaseConstructor.initialize() }.buildKitaeDatabase(seed)

    /** Opens a new database that follows the starter plan from [today], as if it was chosen on first launch. */
    suspend fun openFollowingStarterPlan(today: Long = TODAY): KitaeDatabase {
        val database = open()
        database.plans().setCurrent(database.plans().all().first().id, today)
        return database
    }

    companion object {
        /** The day the iPhone's tests take as today: 10 October 2026, in days since 1970. */
        const val TODAY = 20_736L
    }

    /**
     * Makes the file a database as Kitae made it at [version], 8 or 9: Room's tables from the
     * iPhone's schema file for that version, with the starter exercises (and from version 9
     * their keys), then [changes]. As the iPhone's `makeVersion8` and `makeVersion9` do.
     */
    suspend fun makeDatabase(version: Int, changes: (SQLiteConnection) -> Unit = {}) {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Path(System.getProperty("kitae.schemas")),
            databasePath = file,
            driver = BundledSQLiteDriver(),
            databaseClass = KitaeDatabase::class,
            databaseFactory = { KitaeDatabaseConstructor.initialize() },
        )
        helper.createDatabase(version).use { connection ->
            for (exercise in bundledSeed.exercises) {
                val id = exercise.id ?: continue
                connection.execute(
                    "INSERT INTO exercises (id, name, target, isIsometric, instructions) VALUES (?, ?, ?, ?, ?)",
                    id, exercise.name, exercise.target, exercise.isIsometric, exercise.instructions,
                )
                if (version >= 9) {
                    connection.execute("UPDATE exercises SET libraryKey = ? WHERE id = ?", exercise.key, id)
                }
            }
            changes(connection)
        }
    }
}

/** One minute, in milliseconds. */
const val MINUTE = 60_000L

/**
 * A workout of [day] on [on], started at midday, with its first [done] sets done, finished
 * after [minutes] as [finish] says, or left going when that's null. Returns its id.
 */
suspend fun KitaeDatabase.workout(
    day: DayWithExercises,
    on: Long,
    done: Int,
    minutes: Long = 45,
    finish: FinishMode? = FinishMode.KeepSkipped,
): Long {
    val start = on * 86_400_000 + 12 * 60 * MINUTE
    val workouts = workouts()
    val id = workouts.start(day.day.id, on, start)
    for (set in workouts.exercises(id, day.day.id).flatMap { it.sets }.take(done)) workouts.setDone(set.id, true)
    if (finish != null) workouts.finish(id, finish, addNewSets = false, now = start + minutes * MINUTE)
    return id
}

/** Runs [sql], which changes the database. */
suspend fun KitaeDatabase.change(sql: String) {
    useWriterConnection { connection -> connection.usePrepared(sql) { it.step() } }
}

/** The rows [sql] returns, each read by [row]. */
suspend fun <T> KitaeDatabase.rows(sql: String, row: (SQLiteStatement) -> T): List<T> =
    useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList { while (statement.step()) add(row(statement)) }
        }
    }

/** The number [sql] returns, like a count. */
suspend fun KitaeDatabase.number(sql: String): Long = rows(sql) { it.getLong(0) }.single()

/** The first column of the rows [sql] returns, as text. */
suspend fun KitaeDatabase.texts(sql: String): List<String> = rows(sql) { it.getText(0) }

/** The library keys of the exercises that have one, by id. */
suspend fun KitaeDatabase.keys(): Map<Long, String> =
    rows("SELECT id, libraryKey FROM exercises WHERE libraryKey IS NOT NULL") { it.getLong(0) to it.getText(1) }.toMap()

/** The library keys of the exercises that don't use weight. */
suspend fun KitaeDatabase.weightless(): Set<String> =
    texts("SELECT libraryKey FROM exercises WHERE usesWeight = 0").toSet()
