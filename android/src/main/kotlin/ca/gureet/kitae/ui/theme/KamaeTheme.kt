// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.model.AppTheme

/** Cards have corners of 24, tiles 22, set fields 18; sheets and dialogs 28 as Material has them. */
private val KamaeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Kamae's colours, type and shapes, light or dark as Settings › Theme says. */
@Composable
fun KamaeTheme(theme: AppTheme = AppTheme.System, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val colors = if (dark) KitaeColors.Dark else KitaeColors.Light
    CompositionLocalProvider(LocalKitaeColors provides colors) {
        MaterialTheme(colorScheme = colors.materialScheme(), typography = KamaeTypography, shapes = KamaeShapes, content = content)
    }
}

/** Kamae's colours where a composable is drawn. */
val kitaeColors: KitaeColors
    @Composable get() = LocalKitaeColors.current
