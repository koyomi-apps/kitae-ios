// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room3.Room
import ca.gureet.kitae.database.KitaeDatabase
import ca.gureet.kitae.database.KitaeDatabaseConstructor
import ca.gureet.kitae.database.Seed
import ca.gureet.kitae.database.buildKitaeDatabase
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.muscles.ExerciseMuscles

/** Kamae for Android. Its parts live as long as the app does. */
class KamaeApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

private val Context.settingsDataStore by preferencesDataStore(SettingsStore.FILE_NAME)

/**
 * What the screens share: the database, in Kitae for Android 2.0.0's file (`kenko_database`), so
 * it opens 2.0.0's data in place; the settings; the library's data files, which the build copies
 * from the iPhone's folders; and the rest that's going.
 */
class AppContainer(context: Context) {
    private val assets = context.assets

    private fun text(name: String) = assets.open(name).use { it.readBytes().decodeToString() }

    val seed: Seed by lazy { Seed.decode(text("Seed.json")) }

    val muscles: ExerciseMuscles by lazy { ExerciseMuscles.decode(text("ExerciseMuscles.json")) }

    val database: KitaeDatabase by lazy {
        Room.databaseBuilder(context, DATABASE_NAME) { KitaeDatabaseConstructor.initialize() }.buildKitaeDatabase(seed)
    }

    val settings = SettingsStore(context.applicationContext.settingsDataStore)

    val rest = RestStore(context.getSharedPreferences("rest", Context.MODE_PRIVATE))

    companion object {
        /** Kitae for Android 2.0.0's database file, which this version opens in place. */
        const val DATABASE_NAME = "kenko_database"
    }
}

/** Keeps the rest that's going, so it carries on when Kamae is closed and opened again. */
class RestStore(private val preferences: SharedPreferences) {
    /** The rest kept for the workout [workoutId], if it's still going. */
    fun load(workoutId: Long, now: Long): RestTimer {
        if (preferences.getLong("workout", -1) != workoutId) return RestTimer()
        val rest = RestTimer(
            endsAt = preferences.getLong("endsAt", -1).takeIf { it >= 0 },
            pausedRemaining = preferences.getLong("pausedRemaining", -1).takeIf { it >= 0 },
            total = preferences.getLong("total", 0),
        )
        return if (rest.isRunning && rest.remaining(now) == 0L) RestTimer() else rest
    }

    fun save(workoutId: Long, rest: RestTimer) {
        preferences.edit {
            if (!rest.isActive) {
                clear()
            } else {
                putLong("workout", workoutId)
                putLong("endsAt", rest.endsAt ?: -1)
                putLong("pausedRemaining", rest.pausedRemaining ?: -1)
                putLong("total", rest.total)
            }
        }
    }
}
