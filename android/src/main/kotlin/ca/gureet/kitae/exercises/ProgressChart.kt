// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.exercises

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** An axis that fits [values] with two to four round steps (the iPhone's `ChartScale`). */
class ChartScale(values: List<Double>, minimumStep: Double = 0.0) {
    val lower: Double
    val upper: Double
    val step: Double

    init {
        val low = values.minOrNull() ?: 0.0
        val high = values.maxOrNull() ?: 0.0
        var range = high - low
        if (range < 0.001) range = max(abs(high) / 2, 1.0)
        val magnitude = 10.0.pow(floor(log10(range / 3)))
        val candidates = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }
        step = max(candidates.firstOrNull { range / it <= 3 } ?: (magnitude * 10), minimumStep)
        var bottom = floor(low / step) * step
        var top = ceil(high / step) * step
        if (top - bottom < step) {
            bottom -= step
            top += step
        }
        lower = if (low >= 0) max(bottom, 0.0) else bottom
        upper = top
    }

    val ticks: List<Double>
        get() = generateSequence(lower) { it + step }.takeWhile { it <= upper + step / 2 }.toList()
}

/**
 * The line of an exercise's progress, oldest first (the iPhone's `ProgressChart`): round values up
 * the side, the area under the line, and a dot for each workout, the last one filled.
 */
@Composable
fun ProgressChart(values: List<Double>, minimumStep: Double, label: (Double) -> String, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val measurer = rememberTextMeasurer()
    val scale = ChartScale(values, minimumStep)
    val labelStyle = KitaeType.fieldCaption.copy(color = colors.inkSoft)
    Canvas(modifier) {
        val labels = scale.ticks.map { it to measurer.measure(label(it), labelStyle) }
        val labelWidth = labels.maxOfOrNull { it.second.size.width } ?: 0
        val left = labelWidth + 8.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val inset = 8.dp.toPx()
        fun y(value: Double) = (bottom - (value - scale.lower) / (scale.upper - scale.lower) * (bottom - top)).toFloat()
        for ((value, layout) in labels) {
            val lineY = y(value)
            drawLine(colors.rule, Offset(left, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
            drawText(layout, topLeft = Offset(0f, lineY - layout.size.height / 2f))
        }
        if (values.isEmpty()) return@Canvas
        val span = (size.width - left - 2 * inset).coerceAtLeast(1f)
        val points = values.mapIndexed { index, value ->
            val x = left + inset + if (values.size > 1) span * index / (values.size - 1) else span / 2
            Offset(x, y(value))
        }
        val line = Path().apply {
            points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, y(scale.lower))
            lineTo(points.first().x, y(scale.lower))
            close()
        }
        drawPath(area, colors.olive.copy(alpha = 0.12f))
        drawPath(line, colors.olive, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex) {
                drawCircle(colors.olive, radius = 5.dp.toPx(), center = point)
            } else {
                drawCircle(colors.card, radius = 3.5.dp.toPx(), center = point)
                drawCircle(colors.olive, radius = 3.5.dp.toPx(), center = point, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
