// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.backup.PreferenceValue
import ca.gureet.kitae.backup.PreferencesFileTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The settings a backup carries, under Kitae for Android's names, as the iPhone's `BackupSettingsTests` check them. */
class SettingsTest {
    @Test
    fun theDefaults() {
        val settings = AppSettings()
        assertEquals(WeightUnit.Kilograms, settings.unit)
        assertEquals(DecimalStyle.Automatic, settings.decimals)
        assertEquals(90, settings.defaultRestSeconds)
        assertTrue(settings.restAlerts)
        assertEquals(AppTheme.System, settings.theme)
    }

    @Test
    fun backupsCarryTheSettingsAndroidReads() {
        val settings = AppSettings(
            unit = WeightUnit.Pounds,
            decimals = DecimalStyle.Comma,
            defaultRestSeconds = 120,
            restAlerts = false,
            theme = AppTheme.Dark,
            androidPalette = "Serene",
        )
        assertEquals(
            mapOf(
                "weight_unit" to PreferenceValue.StringValue("Pounds"),
                "rest_timer_seconds" to PreferenceValue.IntValue(120),
                "theme" to PreferenceValue.StringValue("Dark"),
                "decimals" to PreferenceValue.StringValue("comma"),
                "rest_alerts" to PreferenceValue.BoolValue(false),
                "workout_summary" to PreferenceValue.BoolValue(true),
                "new_bests" to PreferenceValue.BoolValue(true),
                "onboarding_done" to PreferenceValue.BoolValue(true),
                "color_palette" to PreferenceValue.StringValue("Serene"),
            ),
            settings.backupPreferences(planChosen = true),
        )
        // Without a plan, Kitae for Android starts with its first launch.
        val fresh = AppSettings().backupPreferences(planChosen = false)
        assertNull(fresh["onboarding_done"])
        assertNull(fresh["color_palette"])
    }

    @Test
    fun androidsSettingsAreTakenIn() {
        val settings = AppSettings(decimals = DecimalStyle.Comma, restAlerts = false)
            .applyingBackup(PreferencesFileTest.androidSettings)
        assertEquals(WeightUnit.Pounds, settings.unit)
        assertEquals(120, settings.defaultRestSeconds)
        assertEquals(AppTheme.Light, settings.theme)
        assertEquals("Serene", settings.androidPalette)
        // Kitae for Android doesn't have these, so they stay as they were.
        assertEquals(DecimalStyle.Comma, settings.decimals)
        assertFalse(settings.restAlerts)
        assertTrue(settings.backUpAfterWorkouts)
    }

    @Test
    fun settingsThatCantBeReadAreLeftAlone() {
        val settings = AppSettings().applyingBackup(
            mapOf(
                "weight_unit" to PreferenceValue.StringValue("Stones"),
                "rest_timer_seconds" to PreferenceValue.LongValue(60),
                "theme" to PreferenceValue.StringValue("Sepia"),
                "color_palette" to PreferenceValue.StringValue("Neon"),
                "decimals" to PreferenceValue.IntValue(1),
                "rest_alerts" to PreferenceValue.StringValue("yes"),
            ),
        )
        assertEquals(AppSettings(), settings)
        assertEquals(AppSettings(), AppSettings().applyingBackup(mapOf("rest_timer_seconds" to PreferenceValue.IntValue(-30))))
    }

    @Test
    fun storedNamesAreReadWithoutFailing() {
        assertNull(WeightUnit.fromStored("Stones"))
        assertNull(DecimalStyle.fromStored("sometimes"))
        assertNull(AppTheme.fromStored("Sepia"))
        assertEquals(AppTheme.Dark, AppTheme.fromStored("Dark"))
    }
}
