// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ios

import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.AppTheme
import ca.gureet.kitae.model.DecimalStyle
import ca.gureet.kitae.model.WeightUnit

/**
 * The shared module's settings for the iPhone's Swift, which keeps its own settings type: what a
 * backup's settings file holds is worked out here, from the values Kitae stores for each setting
 * (`weight_unit` is "Kilograms" or "Pounds", and so on).
 */
object IPhoneSettings {
    fun settings(
        unit: String,
        decimals: String,
        defaultRestSeconds: Int,
        restAlerts: Boolean,
        theme: String,
        backUpAfterWorkouts: Boolean,
        showsWorkoutSummary: Boolean,
        showsNewBests: Boolean,
        androidPalette: String?,
    ) = AppSettings(
        unit = WeightUnit.fromStored(unit) ?: WeightUnit.Kilograms,
        decimals = DecimalStyle.fromStored(decimals) ?: DecimalStyle.Automatic,
        defaultRestSeconds = defaultRestSeconds,
        restAlerts = restAlerts,
        theme = AppTheme.fromStored(theme) ?: AppTheme.System,
        backUpAfterWorkouts = backUpAfterWorkouts,
        showsWorkoutSummary = showsWorkoutSummary,
        showsNewBests = showsNewBests,
        androidPalette = androidPalette,
    )
}
