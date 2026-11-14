// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ca.gureet.kitae.motion.ExerciseMotion
import ca.gureet.kitae.motion.FigurePainter
import ca.gureet.kitae.ui.theme.KitaeColors
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every animation drawn at its start, the middle of its move, its end and as the still picture,
 * as scripts/motions/sheet.js draws them. Two sheets of the ones that use each kind of gear are
 * saved under build/outputs/roborazzi, to set beside sheet.js's pictures of the same keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiguresTest {
    private val library = Samples.library
    private val colors = KitaeColors.Light

    private fun painter(motion: ExerciseMotion) =
        FigurePainter(motion, motion.muscles(library.muscles), colors.figure, colors.olive, colors.card)

    /** sheet.js's moments: the start, the middle of the move, the middle of the hold at the end, and still (null). */
    private fun moments(motion: ExerciseMotion): List<Double?> {
        val k = motion.timing
        return listOf(0.0, k[0] + (k[1] - k[0]) / 2, (k[1] + k[2]) / 2, null)
    }

    private fun draw(width: Int, height: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat()), block)
        return bitmap
    }

    /** One of sheet.js's cards: the figure on Card, corners 24. */
    private fun card(motion: ExerciseMotion, t: Double?): ImageBitmap = draw(CELL, CELL) {
        drawRoundRect(colors.card, cornerRadius = CornerRadius(24f))
        val painter = painter(motion)
        if (t == null) painter.drawStill(this) else painter.draw(this, t)
    }

    @Test
    fun everyAnimationDraws() {
        val pixels = IntArray(CELL * CELL)
        val background = IntArray(CELL * CELL)
        draw(CELL, CELL) { drawRoundRect(colors.card, cornerRadius = CornerRadius(24f)) }.readPixels(background)
        for (motion in library.motions.all) {
            for (t in moments(motion)) {
                card(motion, t).readPixels(pixels)
                val drawn = pixels.indices.count { pixels[it] != background[it] }
                assertTrue("${motion.key} at ${t ?: "still"} drew $drawn pixels", drawn > 800)
            }
        }
    }

    @Test
    fun sheets() {
        sheet("figures-1", FIRST_SHEET)
        sheet("figures-2", SECOND_SHEET)
    }

    private fun sheet(name: String, keys: List<String>) {
        val width = GAP + 4 * (CELL + GAP)
        val height = GAP + keys.size * (CELL + GAP)
        val sheet = draw(width, height) {
            drawRect(colors.paper)
            keys.forEachIndexed { row, key ->
                val motion = library.motions.byKey.getValue(key)
                moments(motion).forEachIndexed { column, t ->
                    drawImage(card(motion, t), Offset((GAP + column * (CELL + GAP)).toFloat(), (GAP + row * (CELL + GAP)).toFloat()))
                }
            }
        }
        val file = File("build/outputs/roborazzi/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { sheet.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val CELL = 150
        const val GAP = 6

        /** Between them, every kind of gear, both views, and legs in front of the body. */
        val FIRST_SHEET = listOf(
            "band-pull-aparts", "pull-ups", "barbell-upright-rows", "barbell-back-squats", "barbell-front-squats",
            "barbell-hip-thrusts", "cable-lateral-raises", "lat-pulldowns", "straight-arm-pulldowns", "machine-crunches",
            "cable-hammer-curls", "tricep-pushdowns", "behind-the-back-cable-curls", "cable-hip-abductions",
            "cable-glute-kickbacks", "cable-crossovers", "dumbbell-curls", "dumbbell-overhead-tricep-extensions", "goblet-squats",
        )
        val SECOND_SHEET = listOf(
            "concentration-curls", "dumbbell-lunges", "single-arm-dumbbell-rows", "ez-bar-preacher-curls", "leg-press",
            "leg-press-calf-raises", "kettlebell-swings", "machine-hip-abductions", "assisted-dips", "russian-twists",
            "barbell-decline-bench-press", "lying-leg-curls", "machine-hip-thrusts", "seated-calf-raises", "leg-extensions",
            "t-bar-rows", "trap-bar-deadlift", "ab-wheel-rollouts",
        )
    }
}
