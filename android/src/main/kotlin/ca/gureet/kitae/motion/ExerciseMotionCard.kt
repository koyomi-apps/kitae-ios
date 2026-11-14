// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * An exercise's animation, shown on its page under "How to do it" (the iPhone's
 * `ExerciseMotionView`): a figure doing the movement with the worked muscles in olive, drawn live.
 * With animations off in the phone's settings, it shows the end pose with the start pose faintly
 * behind it. [at] holds the loop at one moment, for screen tests.
 */
@Composable
fun ExerciseMotionCard(motion: ExerciseMotion, muscles: FigureMuscles, modifier: Modifier = Modifier, at: Double? = null) {
    val colors = kitaeColors
    val painter = remember(motion, muscles, colors) { FigurePainter(motion, muscles, colors.figure, colors.olive, colors.card) }
    val resolver = LocalContext.current.contentResolver
    val still = remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val seconds = remember(motion) { mutableDoubleStateOf(0.0) }
    if (!still && at == null) {
        LaunchedEffect(motion) {
            val start = withInfiniteAnimationFrameMillis { it }
            while (true) withInfiniteAnimationFrameMillis { seconds.doubleValue = (it - start) / 1000.0 }
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .clearAndSetSemantics {},
    ) {
        when {
            at != null -> painter.draw(this, at)
            still -> painter.drawStill(this)
            else -> painter.draw(this, seconds.doubleValue % motion.seconds / motion.seconds)
        }
    }
}
