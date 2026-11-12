// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ca.gureet.kitae.backup.PreferenceValue
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.SettingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The settings, kept in the DataStore file Kitae for Android 2.0.0 keeps them in
 * (`settings.preferences_pb`), under its names and kinds, so its settings carry over and backups
 * stay the same. Names this version doesn't know, and values it can't read, keep their defaults.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = dataStore.data.map(::read)

    /** Whether Kitae for Android's first launch is over: it is once a plan is followed. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboarding] ?: false }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val settings = change(read(preferences))
            preferences[stringPreferencesKey(SettingKey.UNIT)] = settings.unit.stored
            preferences[stringPreferencesKey(SettingKey.DECIMALS)] = settings.decimals.stored
            preferences[intPreferencesKey(SettingKey.DEFAULT_REST)] = settings.defaultRestSeconds
            preferences[booleanPreferencesKey(SettingKey.REST_ALERTS)] = settings.restAlerts
            preferences[stringPreferencesKey(SettingKey.THEME)] = settings.theme.stored
            preferences[booleanPreferencesKey(SettingKey.BACK_UP_AFTER_WORKOUTS)] = settings.backUpAfterWorkouts
            preferences[booleanPreferencesKey(SettingKey.WORKOUT_SUMMARY)] = settings.showsWorkoutSummary
            preferences[booleanPreferencesKey(SettingKey.NEW_BESTS)] = settings.showsNewBests
        }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[onboarding] = true }
    }

    companion object {
        /** The DataStore file's name, Kitae for Android 2.0.0's. */
        const val FILE_NAME = "settings"

        private val onboarding = booleanPreferencesKey(SettingKey.ONBOARDING_DONE)

        /** Settings from what DataStore keeps, read the way a backup's are. */
        fun read(preferences: Preferences): AppSettings {
            val values = preferences.asMap().entries.mapNotNull { (key, value) ->
                val converted: PreferenceValue = when (value) {
                    is Boolean -> PreferenceValue.BoolValue(value)
                    is Int -> PreferenceValue.IntValue(value)
                    is Long -> PreferenceValue.LongValue(value)
                    is Float -> PreferenceValue.FloatValue(value)
                    is Double -> PreferenceValue.DoubleValue(value)
                    is String -> PreferenceValue.StringValue(value)
                    is Set<*> -> PreferenceValue.StringSetValue(value.filterIsInstance<String>())
                    is ByteArray -> PreferenceValue.BytesValue(value)
                    else -> return@mapNotNull null
                }
                key.name to converted
            }.toMap()
            val backUp = (values[SettingKey.BACK_UP_AFTER_WORKOUTS] as? PreferenceValue.BoolValue)?.value ?: true
            return AppSettings().applyingBackup(values).copy(backUpAfterWorkouts = backUp)
        }
    }
}
