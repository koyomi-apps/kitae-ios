// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Kamae's colours, as the iPhone's colour sets have them (`Shared/Colors.xcassets`): Kitae for
 * Android's Zestful palette with a few tones of its own. Material's colour roles are built from
 * these, so its components come out in Kamae's colours too.
 */
@Immutable
data class KitaeColors(
    /** Every screen's background. */
    val paper: Color,
    /** Cards and list rows. */
    val card: Color,
    /** Set fields, chips, choice bars, the done button before it's ticked. */
    val field: Color,
    /** Empty squares in the activity grid. */
    val dayOff: Color,
    val ink: Color,
    /** Captions, second lines, set labels. */
    val inkSoft: Color,
    /** Chevrons and faint marks. */
    val inkFaint: Color,
    /** Hairlines between rows. */
    val rule: Color,
    /** Day names, exercise names, ticked sets, links. */
    val olive: Color,
    val onOlive: Color,
    /** Done set fields, the picked chip, the + buttons. */
    val oliveSoft: Color,
    val onOliveSoft: Color,
    /** Start, Continue, Finish. */
    val mauve: Color,
    val onMauve: Color,
    /** The rest timer bar and pill. */
    val mauveSoft: Color,
    val onMauveSoft: Color,
    /** Muscles that help, on the map and beside an exercise's name. */
    /** The muscle map's body, the muscles not worked, and the helping ones. */
    val muscleMapBase: Color,
    val muscleMapIdle: Color,
    val muscleMapSecondary: Color,
    /** The animated figure's own colours. */
    val figure: FigureColors,
    val error: Color,
    val isDark: Boolean,
) {
    companion object {
        private val lightInk = Color(0xFF1C1C16)
        private val darkInk = Color(0xFFE6E2D9)

        val Light = KitaeColors(
            paper = Color(0xFFFDF9F0),
            card = Color(0xFFF4F0E6),
            field = Color(0xFFECE8DF),
            dayOff = Color(0xFFE3DFD3),
            ink = lightInk,
            inkSoft = Color(0xFF5E5C4E),
            inkFaint = Color(0xFF8F8C7A),
            rule = lightInk.copy(alpha = 0.102f),
            olive = Color(0xFF63611E),
            onOlive = Color(0xFFFFFFFF),
            oliveSoft = Color(0xFFF7F3A1),
            onOliveSoft = Color(0xFF545210),
            mauve = Color(0xFF70576B),
            onMauve = Color(0xFFFFFFFF),
            mauveSoft = Color(0xFFE5C4DC),
            onMauveSoft = Color(0xFF4A3447),
            muscleMapBase = Color(0xFFD8D4C6),
            muscleMapIdle = Color(0xFFBAB6A6),
            muscleMapSecondary = Color(0xFF9B984C),
            figure = FigureColors(
                body = Color(0xFFAAA696),
                far = Color(0xFFCDC9BA),
                gear = Color(0xFF34332B),
                gearFace = Color(0xFF4B4940),
                muscleFar = Color(0xFF9C9A66),
                muscleSecondary = Color(0xFF85823C),
                muscleSecondaryFar = Color(0xFFB4B17E),
                prop = Color(0xFFDDD8C8),
                shadow = Color(0xFF1C1C16).copy(alpha = 0.10f),
            ),
            error = Color(0xFFBA1A1A),
            isDark = false,
        )

        val Dark = KitaeColors(
            paper = Color(0xFF14140E),
            card = Color(0xFF1E1E18),
            field = Color(0xFF2B2A24),
            dayOff = Color(0xFF33322B),
            ink = darkInk,
            inkSoft = Color(0xFFB3B0A0),
            inkFaint = Color(0xFF7D7A6B),
            rule = darkInk.copy(alpha = 0.122f),
            olive = Color(0xFFCECA75),
            onOlive = Color(0xFF333200),
            oliveSoft = Color(0xFF49482C),
            onOliveSoft = Color(0xFFE8E4BE),
            mauve = Color(0xFFFFE4F7),
            onMauve = Color(0xFF3F293C),
            mauveSoft = Color(0xFFD8B8D0),
            onMauveSoft = Color(0xFF412C3E),
            muscleMapBase = Color(0xFF45443B),
            muscleMapIdle = Color(0xFF6D6A5F),
            muscleMapSecondary = Color(0xFF9A975E),
            figure = FigureColors(
                body = Color(0xFF6F6C5F),
                far = Color(0xFF47463D),
                gear = Color(0xFFD8D4C7),
                gearFace = Color(0xFFB9B5A8),
                muscleFar = Color(0xFF88864F),
                muscleSecondary = Color(0xFF9A975E),
                muscleSecondaryFar = Color(0xFF67653F),
                prop = Color(0xFF34332B),
                shadow = Color(0xFF000000).copy(alpha = 0.34f),
            ),
            error = Color(0xFFFFB4AB),
            isDark = true,
        )
    }
}

/** Material's colour roles, from Kamae's colours (the plan's table in section 2.1). */
fun KitaeColors.materialScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = olive,
        onPrimary = onOlive,
        primaryContainer = oliveSoft,
        onPrimaryContainer = onOliveSoft,
        inversePrimary = olive,
        secondary = olive,
        onSecondary = onOlive,
        secondaryContainer = oliveSoft,
        onSecondaryContainer = onOliveSoft,
        tertiary = mauve,
        onTertiary = onMauve,
        tertiaryContainer = mauveSoft,
        onTertiaryContainer = onMauveSoft,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = field,
        onSurfaceVariant = inkSoft,
        surfaceTint = paper,
        surfaceBright = paper,
        surfaceDim = dayOff,
        surfaceContainerLowest = paper,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = field,
        surfaceContainerHighest = dayOff,
        outline = inkFaint,
        outlineVariant = rule,
        error = error,
    )
}

val LocalKitaeColors = staticCompositionLocalOf { KitaeColors.Light }

/** The animated figure's colours, from the iPhone's Figure colour sets. */
data class FigureColors(
    val body: Color,
    val far: Color,
    val gear: Color,
    val gearFace: Color,
    val muscleFar: Color,
    val muscleSecondary: Color,
    val muscleSecondaryFar: Color,
    val prop: Color,
    val shadow: Color,
)
