// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.backup.PreferenceValue

/** Light or dark whatever the phone uses, or the same as the phone. */
enum class AppTheme(
    /** Kitae for Android's name for it, as its settings keep it. */
    val stored: String,
) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
    ;

    companion object {
        fun fromStored(stored: String): AppTheme? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * Where each setting is kept, in each app's own store and in a backup's settings file, as on
 * the iPhone (`Settings.swift`). The settings Kitae for Android 2.0.0 has use its names and its
 * values (`weight_unit` is `Kilograms` or `Pounds`, and so on).
 */
object SettingKey {
    const val UNIT = "weight_unit"
    const val DECIMALS = "decimals"
    const val DEFAULT_REST = "rest_timer_seconds"
    const val REST_ALERTS = "rest_alerts"
    const val THEME = "theme"
    const val TEXT_SIZE = "text_size"
    const val BACK_UP_AFTER_WORKOUTS = "back_up_after_workouts"
    const val WORKOUT_SUMMARY = "workout_summary"
    const val NEW_BESTS = "new_bests"
    const val PALETTE = "color_palette"

    /** Kitae for Android's first launch is over once a plan is chosen. */
    const val ONBOARDING_DONE = "onboarding_done"

    /** Kitae for Android's backup folder and last backup, which stay with the phone. */
    const val BACKUP_URI = "backup_uri"
    const val LAST_BACKUP_TIME_SECONDS = "last_backup_time_seconds"
}

/**
 * What Settings changes, apart from the text size, which each app keeps its own way, and where
 * backups go, which belongs to the phone.
 */
data class AppSettings(
    val unit: WeightUnit = WeightUnit.Kilograms,
    val decimals: DecimalStyle = DecimalStyle.Automatic,
    /** The rest after a set when neither the set nor its exercise has one of its own. 0 starts no rest. */
    val defaultRestSeconds: Int = 90,
    /** Whether "Rest over" goes off when a rest runs out. */
    val restAlerts: Boolean = true,
    val theme: AppTheme = AppTheme.System,
    val backUpAfterWorkouts: Boolean = true,
    /** Whether a summary opens after finishing a workout. */
    val showsWorkoutSummary: Boolean = true,
    /** Whether that summary lists the records the workout's sets beat. */
    val showsNewBests: Boolean = true,
    /** Kitae for Android's colour palette, kept from a restored backup so backups bring it back. */
    val androidPalette: String? = null,
) {
    /**
     * What a backup's settings file holds: the settings Kitae for Android has, under its names
     * and with the kinds of value it reads, and the newer ones, which it keeps without using.
     * [planChosen] tells Kitae for Android 2.0.0 its first launch is over.
     */
    fun backupPreferences(planChosen: Boolean): Map<String, PreferenceValue> = buildMap {
        put(SettingKey.UNIT, PreferenceValue.StringValue(unit.stored))
        put(SettingKey.DEFAULT_REST, PreferenceValue.IntValue(defaultRestSeconds))
        put(SettingKey.THEME, PreferenceValue.StringValue(theme.stored))
        put(SettingKey.DECIMALS, PreferenceValue.StringValue(decimals.stored))
        put(SettingKey.REST_ALERTS, PreferenceValue.BoolValue(restAlerts))
        put(SettingKey.WORKOUT_SUMMARY, PreferenceValue.BoolValue(showsWorkoutSummary))
        put(SettingKey.NEW_BESTS, PreferenceValue.BoolValue(showsNewBests))
        if (planChosen) put(SettingKey.ONBOARDING_DONE, PreferenceValue.BoolValue(true))
        androidPalette?.let { put(SettingKey.PALETTE, PreferenceValue.StringValue(it)) }
    }

    /**
     * These settings with the ones from a backup's settings file, from either app. Anything it
     * doesn't have, or that can't be read, stays as it is.
     */
    fun applyingBackup(preferences: Map<String, PreferenceValue>): AppSettings {
        fun text(key: String) = (preferences[key] as? PreferenceValue.StringValue)?.value
        fun flag(key: String) = (preferences[key] as? PreferenceValue.BoolValue)?.value
        return copy(
            unit = text(SettingKey.UNIT)?.let(WeightUnit::fromStored) ?: unit,
            defaultRestSeconds = (preferences[SettingKey.DEFAULT_REST] as? PreferenceValue.IntValue)?.value
                ?.takeIf { it >= 0 } ?: defaultRestSeconds,
            theme = text(SettingKey.THEME)?.let(AppTheme::fromStored) ?: theme,
            decimals = text(SettingKey.DECIMALS)?.let(DecimalStyle::fromStored) ?: decimals,
            restAlerts = flag(SettingKey.REST_ALERTS) ?: restAlerts,
            showsWorkoutSummary = flag(SettingKey.WORKOUT_SUMMARY) ?: showsWorkoutSummary,
            showsNewBests = flag(SettingKey.NEW_BESTS) ?: showsNewBests,
            androidPalette = text(SettingKey.PALETTE)?.takeIf { it in ANDROID_PALETTES } ?: androidPalette,
        )
    }

    companion object {
        /** The palettes Kitae for Android 2.0.0 has. It can't open settings with another one. */
        val ANDROID_PALETTES = setOf("Dynamic", "Default", "Zestful", "Serene", "Twilight")
    }
}
