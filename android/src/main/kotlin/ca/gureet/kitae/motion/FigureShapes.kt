// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** A point as Compose draws it. */
val Vec.offset: Offset
    get() = Offset(x.toFloat(), y.toFloat())

private fun Path.moveTo(v: Vec) = moveTo(v.x.toFloat(), v.y.toFloat())

private fun Path.lineTo(v: Vec) = lineTo(v.x.toFloat(), v.y.toFloat())

private fun Path.cubicTo(c1: Vec, c2: Vec, to: Vec) =
    cubicTo(c1.x.toFloat(), c1.y.toFloat(), c2.x.toFloat(), c2.y.toFloat(), to.x.toFloat(), to.y.toFloat())

/**
 * The figure's shapes as paths (the iPhone's `FigureShapes`). Every capsule and circle winds
 * the same way, so several filled as one path, with Compose's non-zero rule, make their union.
 */
object FigureShapes {
    /** A limb: a tapered capsule between two circles. */
    fun capsule(p1: Vec, p2: Vec, r1: Double, r2: Double): Path {
        val d = p2 - p1
        val dist = d.length
        if (dist <= 0.01) return circle(p1, max(r1, r2))
        val th = atan2(d.y, d.x)
        val phi = acos(min(1.0, max(-1.0, (r1 - r2) / dist)))
        fun on(c: Vec, r: Double, a: Double) = c + Vec(cos(a), sin(a)) * r
        return Path().apply {
            moveTo(on(p1, r1, th + phi))
            lineTo(on(p2, r2, th + phi))
            arc(this, p2, r2, th + phi, th - phi)
            lineTo(on(p1, r1, th - phi))
            arc(this, p1, r1, th - phi, th + phi - 2 * PI)
            close()
        }
    }

    /** A circle, wound the same way as capsules so a union of the two fills solid. */
    fun circle(c: Vec, r: Double): Path = Path().apply {
        moveTo(c + Vec(r, 0.0))
        arc(this, c, r, 0.0, -2 * PI)
        close()
    }

    /** An ellipse of radii [rx] and [ry] round [c], turned by [degrees]. */
    fun ellipse(c: Vec, rx: Double, ry: Double, degrees: Double): Path {
        val turn = degrees * PI / 180
        fun at(a: Double) = c + Vec(rx * cos(a), ry * sin(a)).rotated(turn)
        fun tangent(a: Double) = Vec(-rx * sin(a), ry * cos(a)).rotated(turn)
        val k = 4.0 / 3.0 * tan(PI / 8)
        return Path().apply {
            moveTo(at(0.0))
            for (i in 0 until 4) {
                val a = i * PI / 2
                val b = a + PI / 2
                cubicTo(at(a) + tangent(a) * k, at(b) - tangent(b) * k, at(b))
            }
            close()
        }
    }

    /** A smooth closed outline through the points. */
    fun outline(points: List<Vec>): Path {
        val path = Path()
        val n = points.size
        if (n <= 2) return path
        path.moveTo(points[0])
        for (i in 0 until n) {
            val p0 = points[(i + n - 1) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            path.cubicTo(p1 + (p2 - p0) * (1.0 / 6), p2 - (p3 - p1) * (1.0 / 6), p2)
        }
        path.close()
        return path
    }

    fun polygon(points: List<Vec>): Path = Path().apply {
        points.forEachIndexed { index, point -> if (index == 0) moveTo(point) else lineTo(point) }
        close()
    }

    fun hexagon(c: Vec, r: Double, degrees: Double): Path = polygon((0 until 6).map { c + Vec.direction(degrees + it * 60.0) * r })

    /** Several shapes filled as one. */
    fun union(vararg paths: Path): Path = Path().apply { for (path in paths) addPath(path) }

    /** An arc from angle [a0] to [a1] (radians, either way round) as cubic curves, from the current point. */
    private fun arc(path: Path, c: Vec, r: Double, a0: Double, a1: Double) {
        val steps = max(1, ceil(abs(a1 - a0) / (PI / 2)).toInt())
        val step = (a1 - a0) / steps
        val k = 4.0 / 3.0 * tan(step / 4) * r
        var a = a0
        repeat(steps) {
            val b = a + step
            val p0 = c + Vec(cos(a), sin(a)) * r
            val p3 = c + Vec(cos(b), sin(b)) * r
            path.cubicTo(p0 + Vec(-sin(a), cos(a)) * k, p3 - Vec(-sin(b), cos(b)) * k, p3)
            a = b
        }
    }
}

/**
 * A muscle hugging one side of a limb from [a] to [b] (radii [ra] and [rb]): thicker in its middle
 * than at its ends. [side] is 1 for the front of the limb, -1 for the back, 0 for both.
 */
fun muscleShape(a: Vec, b: Vec, ra: Double, rb: Double, at: Triple<Double, Double, Double>, widths: Triple<Double, Double, Double>, side: Double): Path {
    val out = (b - a).unit.front * side
    fun point(t: Double, width: Double): Pair<Vec, Double> {
        val r = FigureMath.lerp(ra, rb, t)
        return (Vec.lerp(a, b, t) + out * (r - r * width - 0.1)) to r * width
    }
    val (p1, r1) = point(at.first, widths.first)
    val (p2, r2) = point(at.second, widths.second)
    val (p3, r3) = point(at.third, widths.third)
    return FigureShapes.union(FigureShapes.capsule(p1, p2, r1, r2), FigureShapes.capsule(p2, p3, r2, r3))
}
