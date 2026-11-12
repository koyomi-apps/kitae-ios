// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /** Kamae's own text size, or null for the phone's. A size kept some other way is the nearest one. */
    val textSize: Flow<TextSize?> = dataStore.data.map { preferences ->
        when (val value = preferences.asMap().entries.firstOrNull { it.key.name == SettingKey.TEXT_SIZE }?.value) {
            is String -> TextSize.fromStored(value)
            is Float -> TextSize.nearest(value)
            is Double -> TextSize.nearest(value.toFloat())
            else -> null
        }
    }

    suspend fun setTextSize(size: TextSize?) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(SettingKey.TEXT_SIZE)
            preferences.remove(key)
            if (size != null) preferences[key] = size.stored
        }
    }

    /** This phone's backups: the folder they go in and when the last one was made. */
    val backups: Flow<BackupPlace> = dataStore.data.map { preferences ->
        BackupPlace(preferences[backupUri], preferences[lastBackupSeconds])
    }

    suspend fun setBackupFolder(uri: String) {
        dataStore.edit { it[backupUri] = uri }
    }

    suspend fun setLastBackup(seconds: Long) {
        dataStore.edit { it[lastBackupSeconds] = seconds }
    }

    /**
     * Takes a restored backup's settings, from either app, in place of these ones. This phone's
     * own stay: its backup folder, its last backup and whether it backs up after each workout,
     * as on the iPhone.
     */
    suspend fun restore(values: Map<String, PreferenceValue>?) {
        dataStore.edit { preferences ->
            val kept = preferences.asMap().filterKeys { it.name in PHONE_KEYS }
            preferences.clear()
            for ((name, value) in values.orEmpty()) {
                if (name in PHONE_KEYS) continue
                when (value) {
                    is PreferenceValue.BoolValue -> preferences[booleanPreferencesKey(name)] = value.value
                    is PreferenceValue.IntValue -> preferences[intPreferencesKey(name)] = value.value
                    is PreferenceValue.LongValue -> preferences[longPreferencesKey(name)] = value.value
                    is PreferenceValue.FloatValue -> preferences[floatPreferencesKey(name)] = value.value
                    is PreferenceValue.DoubleValue -> preferences[doublePreferencesKey(name)] = value.value
                    is PreferenceValue.StringValue -> preferences[stringPreferencesKey(name)] = value.value
                    is PreferenceValue.StringSetValue -> preferences[stringSetPreferencesKey(name)] = value.value.toSet()
                    is PreferenceValue.BytesValue -> preferences[byteArrayPreferencesKey(name)] = value.value
                }
            }
            @Suppress("UNCHECKED_CAST")
            for ((key, value) in kept) preferences[key as Preferences.Key<Any>] = value
        }
    }

    /** Back to a fresh install's settings, keeping this phone's backup folder and last backup. */
    suspend fun reset() {
        dataStore.edit { preferences ->
            val folder = preferences[backupUri]
            val last = preferences[lastBackupSeconds]
            preferences.clear()
            folder?.let { preferences[backupUri] = it }
            last?.let { preferences[lastBackupSeconds] = it }
        }
    }

    companion object {
        /** The DataStore file's name, Kitae for Android 2.0.0's. */
        const val FILE_NAME = "settings"

        private val onboarding = booleanPreferencesKey(SettingKey.ONBOARDING_DONE)
        private val backupUri = stringPreferencesKey(SettingKey.BACKUP_URI)
        private val lastBackupSeconds = longPreferencesKey(SettingKey.LAST_BACKUP_TIME_SECONDS)

        /** The settings that belong to this phone, not to backups. */
        private val PHONE_KEYS = setOf(
            SettingKey.BACKUP_URI,
            SettingKey.LAST_BACKUP_TIME_SECONDS,
            SettingKey.BACK_UP_AFTER_WORKOUTS,
            SettingKey.TEXT_SIZE,
        )

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

/** Where this phone's backups go, as a folder picked with Android's picker, and when the last one was made. */
data class BackupPlace(val folderUri: String? = null, val lastBackupSeconds: Long? = null)
