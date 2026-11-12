// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R

// Three typefaces, each with one job, as on the iPhone: Darker Grotesque for day names, titles
// and headings; Space Mono for numbers, set labels, captions, chips and the big buttons; and the
// phone's own font for everything else. Both bundled fonts are under the SIL Open Font License
// (assets/licences).

val DarkerGrotesque = FontFamily(
    Font(R.font.darker_grotesque_bold, FontWeight.Bold),
    Font(R.font.darker_grotesque_semibold, FontWeight.SemiBold),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/**
 * Darker Grotesque has a lot of air above and below, so its lines are pulled together: a line is
 * as tall as its line height, shorter than the font's own, with the difference shared above and
 * below, as the canvas's CSS does.
 */
private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun display(size: TextUnit, lineHeight: TextUnit, letterSpacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = DarkerGrotesque,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    lineHeightStyle = tight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun mono(size: TextUnit, bold: Boolean = false, lineHeight: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = SpaceMono,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size,
    lineHeight = lineHeight,
)

/** Kamae's own text styles (sizes in sp, so they follow the text size). */
object KitaeType {
    /** The day's name on Today. */
    val dayHero = display(112.sp, 92.sp, (-2).sp)

    /** The day's name in the summary. */
    val daySummary = display(72.sp, 64.sp, (-1).sp)

    /** An exercise's page title. */
    val exerciseTitle = display(54.sp, 50.sp)

    /** Big page titles: History, Exercises, You, Settings, Body. */
    val bigTitle = display(48.sp, 46.sp)

    /** A workout's page, the plan's name in the editor, the latest weight. */
    val pageTitle = display(46.sp, 44.sp)

    /** Card titles, like the plan card. */
    val cardTitle = display(32.sp, 32.sp)

    /** Exercise names in a workout and in sheets, month names in History. */
    val sectionTitle = display(26.sp, 30.sp)

    /** The workout's title in its top bar. */
    val workoutTitle = display(24.sp, 26.sp)

    /** Tile titles. */
    val tileTitle = display(20.sp, 22.sp)

    /** Set labels: W1, 01, F1. */
    val setLabel = mono(26.sp, bold = true)

    /** Numbers in tiles. */
    val tileNumber = mono(28.sp, bold = true)

    /** The rest countdown in its bar. */
    val restCountdown = mono(22.sp, bold = true)

    /** Start and Continue. */
    val bigButton = mono(17.sp, bold = true)

    /** Typed reps and weights. */
    val typed = mono(17.sp, lineHeight = 24.sp)

    /** Chips and choice bars; bold when picked. */
    val chip = mono(14.sp)

    /** Captions like ".up next" and "5 exercises · 15 sets". */
    val caption = mono(14.sp, lineHeight = 20.sp)

    /** Smaller captions, like ".exercises" and "3 sets". */
    val smallCaption = mono(13.sp, lineHeight = 18.sp)

    /** List section headers like ".set type", and the line under Start. */
    val sectionCaption = mono(12.sp, lineHeight = 16.sp)

    /** Field captions: ".reps", ".weight". */
    val fieldCaption = mono(10.sp, lineHeight = 14.sp)

    /** The keyboard bar's steps. */
    val step = mono(15.sp)
}

/** Material's styles: display and headline in Darker Grotesque, the rest in the phone's font. */
val KamaeTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = DarkerGrotesque, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = DarkerGrotesque, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = DarkerGrotesque, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = DarkerGrotesque, fontWeight = FontWeight.Bold),
        // The big titles of History and Exercises, in the large top app bar.
        headlineMedium = base.headlineMedium.copy(
            fontFamily = DarkerGrotesque,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontFamily = DarkerGrotesque, fontWeight = FontWeight.Bold),
    )
}
