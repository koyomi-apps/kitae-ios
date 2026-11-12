// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import ca.gureet.kitae.motion.ExerciseMotion.Keyframes
import ca.gureet.kitae.motion.FigureMuscles.Shade
import ca.gureet.kitae.motion.FigureShapes.capsule
import ca.gureet.kitae.motion.FigureShapes.circle
import ca.gureet.kitae.motion.FigureShapes.ellipse
import ca.gureet.kitae.motion.FigureShapes.hexagon
import ca.gureet.kitae.motion.FigureShapes.outline
import ca.gureet.kitae.motion.FigureShapes.union
import ca.gureet.kitae.muscles.Muscle
import ca.gureet.kitae.ui.theme.FigureColors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws an exercise's figure, its worked muscles and its gear (the iPhone's `FigurePainter`). From
 * the side, back to front: the shadow, frames and weight stacks, the far arm and leg, benches and
 * seats, the body and head, pads in front of it, cables and bars, the near leg and arm, and what
 * the near hand holds. The front view is in FrontPainter.kt.
 */
class FigurePainter(
    val motion: ExerciseMotion,
    val muscles: FigureMuscles,
    val colors: FigureColors,
    /** The worked muscles' colour. */
    val muscle: Color,
    /** The card behind the figure, for the thin outlines that set front-view limbs apart. */
    val card: Color,
) {
    /** The figure [t] of the way through a loop (0…1), standing on the bottom of the canvas. */
    fun draw(scope: DrawScope, t: Double) {
        val k = motion.progress(t)
        scope.placed {
            when (val keyframes = motion.keyframes) {
                is Keyframes.Side -> drawSide(Figure(FigurePose.lerp(keyframes.from, keyframes.to, k)))
                is Keyframes.Front -> drawFront(this, FrontFigure(FrontPose.lerp(keyframes.from, keyframes.to, k)))
            }
        }
    }

    /** With animations off: the end pose, with the start pose faintly behind it. */
    fun drawStill(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = 0.4f }) }
        pose(start = true)
        drawIntoCanvas { it.restore() }
        pose(start = false)
    }

    private fun DrawScope.pose(start: Boolean) = placed {
        when (val keyframes = motion.keyframes) {
            is Keyframes.Side -> drawSide(Figure(if (start) keyframes.from else keyframes.to))
            is Keyframes.Front -> drawFront(this, FrontFigure(if (start) keyframes.from else keyframes.to))
        }
    }

    /** Scales the figure's units to the canvas, its feet 20dp above the bottom. */
    private inline fun DrawScope.placed(crossinline block: DrawScope.() -> Unit) {
        val px = min(1.15, (size.height / density - 34.0) / 190) * motion.zoom * density
        val left = (size.width / 2 + motion.shift * px).toFloat()
        val top = size.height - 20 * density
        withTransform({
            translate(left, top)
            scale(px.toFloat(), px.toFloat(), Offset.Zero)
        }) { block() }
    }

    internal fun DrawScope.fill(path: Path, color: Color) = drawPath(path, color)

    internal fun DrawScope.fillInside(clip: Path, path: Path, color: Color) = clipPath(clip) { drawPath(path, color) }

    internal fun DrawScope.plate(at: Vec, r: Double) {
        fill(circle(at, r), colors.gear)
        fill(circle(at, r * 0.735), colors.gearFace)
        fill(circle(at, r * 0.21), colors.body)
    }

    /** Frames and weight stacks, behind everything. */
    internal fun DrawScope.stacksAndPosts() {
        for (g in motion.gear(Gear.Kind.Cable)) {
            val pulley = g.from
            if (g.stack && pulley != null) fill(capsule(Vec(pulley.x, -3.0), pulley, 6.5, 6.5), colors.prop)
        }
        for (g in motion.gear(Gear.Kind.Post)) {
            val from = g.from ?: continue
            val to = g.to ?: continue
            fill(capsule(from, to, g.width / 2, g.width / 2), colors.prop)
        }
    }

    /** A bench, seat or pad from [from] to [to], on the legs and lever [g] gives it. */
    internal fun DrawScope.pad(g: Gear, from: Vec, to: Vec) {
        val d = (to - from).unit
        val right = Vec(-d.y, d.x)
        val h = g.thick / 2
        // The lever it rides on, from its pivot at `anchor`.
        g.anchor?.let { fill(capsule(it, Vec.lerp(from, to, 0.5) + right * h, 2.4, 2.4), colors.prop) }
        for (x in g.legs) {
            val k = (x - from.x) / (if (to.x - from.x == 0.0) 1.0 else to.x - from.x)
            val top = Vec.lerp(from, to, k) + right * h
            fill(capsule(top, Vec(x, -2.0), 1.9, 1.9), colors.prop)
            fill(capsule(Vec(x - 8, -1.6), Vec(x + 8, -1.6), 1.6, 1.6), colors.prop)
        }
        fill(capsule(from + right * h, to + right * h, h, h), colors.prop)
    }

    private fun tint(shade: Shade, far: Boolean): Color = when (shade) {
        Shade.Primary -> if (far) colors.muscleFar else muscle
        Shade.Secondary -> if (far) colors.muscleSecondaryFar else colors.muscleSecondary
    }

    private fun DrawScope.drawSide(f: Figure) {
        val c = colors
        val farArm = ArmShape(f.farArm)
        val nearArm = ArmShape(f.nearArm)

        fun leg(l: LegShape, body: Color, far: Boolean) {
            fill(l.thigh, body)
            fill(l.shin, body)
            fill(l.foot, body)
            for (shade in Shade.entries) {
                val color = tint(shade, far)
                if (muscles[Muscle.Quads] == shade) fillInside(l.thigh, l.quads, color)
                if (muscles[Muscle.Hamstrings] == shade) fillInside(l.thigh, l.hamstrings, color)
                if (muscles[Muscle.Calves] == shade) fillInside(l.shin, l.calves, color)
            }
        }

        fun arm(a: ArmShape, body: Color, far: Boolean) {
            fill(a.upper, body)
            fill(a.forearm, body)
            fill(a.hand, body)
            val cap = union(a.upper, circle(a.arm.shoulder, 7.2))
            for (shade in Shade.entries) {
                val color = tint(shade, far)
                if (muscles[Muscle.Biceps] == shade) fillInside(a.upper, a.biceps, color)
                if (muscles[Muscle.Triceps] == shade) fillInside(a.upper, a.triceps, color)
                if (muscles[Muscle.Forearms] == shade) fillInside(a.forearm, a.forearms, color)
                if (muscles[Muscle.SideDelts] == shade) fillInside(cap, a.delts(0.0), color)
                if (muscles[Muscle.FrontDelts] == shade) fillInside(cap, a.delts(1.0), color)
                if (muscles[Muscle.RearDelts] == shade) fillInside(cap, a.delts(-1.0), color)
            }
        }

        fun sidePad(g: Gear) {
            when (g.on) {
                "back" -> pad(g, f.torso(g.top, g.out ?: -12.5), f.torso(g.bottom, (g.out ?: -12.5) - 1))
                "front" -> pad(g, f.torso(g.bottom, g.out ?: 13.5), f.torso(g.top, g.out ?: 13.5))
                // A machine's knee pad, under the front of the near shin.
                "shins" -> {
                    val l = f.nearLeg
                    val n = (l.ankle - l.knee).unit.front
                    pad(g, Vec.lerp(l.knee, l.ankle, 0.8) + n * 6.8, Vec.lerp(l.knee, l.ankle, -0.05) + n * 6.8)
                }
                else -> {
                    val from = g.from ?: return
                    val to = g.to ?: return
                    pad(g, from, to)
                }
            }
        }

        // A goblet squat's dumbbell, held upright at the chest.
        fun goblet() {
            val at = f.torso(40.0, 19.0)
            val d = f.spine(40.0)
            val n = d.front
            fill(capsule(at - d * 7.0, at + d * 7.0, 1.7, 1.7), c.gear)
            for (k in listOf(-8.0, 8.0)) {
                val end = at + d * k
                fill(capsule(end - n * 6.5, end + n * 6.5, 2.8, 2.8), c.gear)
            }
        }

        fun dumbbell(a: ArmShape, near: Boolean) {
            val g = a.grip
            val color = if (near) c.gear else c.body
            val gear = motion.gear(Gear.Kind.Dumbbells)
            when {
                gear.any { it.hang } -> {
                    val top = g + Vec(0.0, 2.0)
                    fill(capsule(top, top + Vec(0.0, 15.0), 1.7, 1.7), color)
                    for (y in listOf(0.0, 15.0)) fill(capsule(top + Vec(-7.0, y), top + Vec(7.0, y), 2.8, 2.8), color)
                }
                gear.any { it.profile } -> {
                    val fd = (a.arm.wrist - a.arm.elbow).unit
                    val axis = fd.front
                    fill(capsule(g - axis * 7.0, g + axis * 7.0, 1.7, 1.7), color)
                    for (k in listOf(-8.0, 8.0)) {
                        val end = g + axis * k
                        fill(capsule(end - fd * 6.5, end + fd * 6.5, 2.8, 2.8), color)
                    }
                }
                near -> {
                    val at = g + Vec(1.0, 1.0)
                    fill(hexagon(at, 8.6, 30.0), c.gear)
                    fill(hexagon(at, 6.0, 30.0), c.gearFace)
                    fill(circle(at, 2.4), c.body)
                }
                else -> fill(hexagon(g + Vec(-1.0, -1.0), 8.6, 30.0), c.body)
            }
        }

        // Points on the body that gear follows.
        fun bodyPoint(name: String?): Vec {
            val l = f.nearLeg
            return when (name) {
                "ankle" -> l.ankle
                "shin" -> Vec.lerp(l.knee, l.ankle, 0.8) + (l.ankle - l.knee).unit.front * 8.0
                "calf" -> Vec.lerp(l.knee, l.ankle, 0.82) + (l.ankle - l.knee).unit.front * -8.5
                "thigh" -> Vec.lerp(l.hip, l.knee, 0.62) + (l.knee - l.hip).unit.front * 9.0
                "knee" -> Vec.lerp(l.hip, l.knee, 0.86) + (l.knee - l.hip).unit.front * 9.5
                "hips" -> f.torso(-2.0, 11.0)
                "chest" -> f.torso(36.0, 15.0)
                else -> nearArm.grip
            }
        }

        fill(ellipse(motion.shadow, motion.shadowWidth, 3.4, 0.0), c.shadow)
        stacksAndPosts()

        // The far side, a little behind the near one.
        val dumbbells = motion.gear(Gear.Kind.Dumbbells).filter { it.on != "chest" }
        leg(LegShape(f.farLeg), c.far, far = true)
        arm(farArm, c.far, far = true)
        if (dumbbells.isNotEmpty() && dumbbells.none { it.one }) dumbbell(farArm, near = false)

        // Benches, seats and pads under the body.
        for (g in motion.gear(Gear.Kind.Pad)) if (!g.front) sidePad(g)

        // A T-bar runs from its anchor on the floor, between the legs, to the hands; plates at its end.
        for (g in motion.gear(Gear.Kind.TBar)) {
            val anchor = g.anchor ?: continue
            val grip = nearArm.grip
            val d = (grip - anchor).unit
            val n = Vec(-d.y, d.x)
            val end = grip + d * 12.0
            val r = g.radius ?: 15.0
            fill(capsule(anchor, grip + d * 16.0, 1.8, 1.8), c.gear)
            fill(capsule(end - n * r, end + n * r, 3.4, 3.4), c.gear)
        }

        // The body.
        val torso = outline(SIDE_TORSO.map { f.torso(it.first, it.second) })
        fill(torso, c.body)
        for (shade in Shade.entries) {
            val color = tint(shade, far = false)
            if (muscles[Muscle.Glutes] == shade) fillInside(torso, ellipse(f.torso(-1.5, -8.5), 8.5, 10.5, f.turn(-1.5)), color)
            if (muscles[Muscle.Chest] == shade) fillInside(torso, ellipse(f.torso(37.0, 8.5), 6.5, 9.0, f.turn(37.0)), color)
            if (muscles[Muscle.Abs] == shade) fillInside(torso, capsule(f.torso(4.0, 5.8), f.torso(27.0, 8.5), 3.4, 3.4), color)
            if (muscles[Muscle.Obliques] == shade) fillInside(torso, ellipse(f.torso(14.0, 2.0), 4.5, 9.0, f.turn(14.0)), color)
            if (muscles[Muscle.Lats] == shade) fillInside(torso, ellipse(f.torso(33.0, -7.5), 4.5, 11.0, f.turn(33.0)), color)
            if (muscles[Muscle.Traps] == shade) fillInside(torso, ellipse(f.torso(50.0, -6.0), 5.0, 7.0, f.turn(50.0)), color)
            if (muscles[Muscle.LowerBack] == shade) fillInside(torso, capsule(f.torso(6.0, -8.0), f.torso(26.0, -6.2), 2.8, 2.8), color)
        }

        // A barbell across the shoulders, seen a little from behind so the head stays clear.
        for (g in motion.gear(Gear.Kind.Barbell)) {
            if (g.on != "back") continue
            val bar = f.torso(50.0, -9.0)
            val at = bar + Vec(-9.0, 2.5)
            fill(capsule(at, bar, 2.6, 2.6), c.gear)
            plate(at, g.radius ?: 17.0)
        }
        // A front squat's bar rests on the front of the shoulders; drawn behind the head so the face stays clear.
        for (g in motion.gear(Gear.Kind.Barbell)) {
            if (g.on == "front") plate(f.torso(50.0, 11.0) + Vec(5.0, 1.0), g.radius ?: 15.0)
        }

        // Neck and head: a cranium and a jaw, so the face's direction reads.
        val headFront = Vec(-f.headDirection.y, f.headDirection.x)
        val turn = f.pose.head ?: (f.pose.lean + f.pose.curl)
        fill(capsule(f.neckBase, f.neckTop, 5.6, 5.0), c.body)
        fill(ellipse(f.head, 10.2, 11.6, turn), c.body)
        fill(ellipse(f.head + headFront * 3.2 - f.headDirection * 5.5, 7.4, 6.4, turn), c.body)

        // Pads in front of the body, such as a preacher bench's, under the near arm.
        for (g in motion.gear(Gear.Kind.Pad)) if (g.front) sidePad(g)

        // Cables run to the middle of the body, behind the near arm; bars are gripped by it.
        for (g in motion.gear(Gear.Kind.Cable)) {
            val pulley = g.from ?: continue
            fill(capsule(pulley, bodyPoint(g.on), 0.8, 0.8), c.gear)
            fill(circle(pulley, 4.2), c.gear)
            fill(circle(pulley, 1.7), c.prop)
        }
        for (g in motion.gear(Gear.Kind.Bar)) g.at?.let { fill(circle(it, 3.6), c.gear) }

        // Foot plates move with the near foot, under its sole.
        for (g in motion.gear(Gear.Kind.FootPlate)) {
            val l = f.nearLeg
            val footTurn = l.foot * PI / 180
            val heel = l.ankle + Vec(-4.5, 2.9).rotated(footTurn)
            val toe = l.ankle + Vec(17.0, 4.2).rotated(footTurn)
            val d = (toe - heel).unit
            val n = Vec(-d.y, d.x)
            val h = g.thick / 2
            val from = g.from
            val to = g.to
            // On a sled's rail (`from` to `to`) it stays square to the rail, pushed by the ball of the
            // foot, as in calf raises on a leg press.
            if (g.on == "ball" && from != null && to != null) {
                val rail = (to - from).unit
                val ball = l.ankle + Vec(12.0, 7.6).rotated(footTurn)
                var along = rail.front
                if (along.x * (toe.x - l.ankle.x) + along.y * (toe.y - l.ankle.y) < 0) along = along * -1.0
                val middle = ball + rail * h + along * (g.length / 2 - 4)
                fill(capsule(middle - along * (g.length / 2), middle + along * (g.length / 2), h, h), c.prop)
                continue
            }
            val mid = Vec.lerp(heel, toe, 0.5) + n * (4 + h)
            fill(capsule(mid - d * (g.length / 2), mid + d * (g.length / 2), h, h), c.prop)
        }
        if (motion.gear(Gear.Kind.Dumbbells).any { it.on == "chest" }) goblet()
        // A weight plate held upright in both hands, seen edge-on.
        for (g in motion.gear(Gear.Kind.Plate)) {
            val at = nearArm.grip
            val r = g.radius ?: 12.0
            fill(capsule(at - Vec(0.0, r), at + Vec(0.0, r), 2.6, 2.6), c.gear)
        }

        // The near side.
        leg(LegShape(f.nearLeg), c.body, far = false)
        arm(nearArm, c.body, far = false)

        // What the near hand holds, and machine pads in front of the legs.
        for (g in motion.gear(Gear.Kind.Barbell)) {
            if (g.on == null || g.on == "hands") plate(nearArm.grip, g.radius ?: 17.0)
        }
        for (g in motion.gear(Gear.Kind.Barbell)) {
            if (g.on == "hips") plate(f.torso(-1.0, 12.0), g.radius ?: 17.0)
        }
        for (g in motion.gear(Gear.Kind.EzBar)) plate(nearArm.grip, g.radius ?: 12.5)
        // A trap bar's frame runs front to back through the near handle, its plate at the handle.
        for (g in motion.gear(Gear.Kind.TrapBar)) {
            val at = nearArm.grip
            fill(capsule(at - Vec(28.0, 0.0), at + Vec(28.0, 0.0), 2.4, 2.4), c.gear)
            plate(at, g.radius ?: 17.0)
        }
        // A kettlebell carries on from the near hand, its bell beyond the grip.
        for (g in motion.gear(Gear.Kind.Kettlebell)) {
            val grip = nearArm.grip
            val d = (grip - nearArm.arm.elbow).unit
            val r = g.radius ?: 9.5
            val bell = grip + d * (r + 3)
            fill(capsule(grip, bell, 2.6, 2.6), c.gear)
            fill(circle(bell, r), c.gear)
            fill(circle(bell - d * 1.6, r * 0.55), c.gearFace)
        }
        for (g in motion.gear(Gear.Kind.Wheel)) plate(nearArm.grip, g.radius ?: 8.0)
        if (dumbbells.isNotEmpty()) dumbbell(nearArm, near = true)
        for (g in motion.gear(Gear.Kind.Cable)) {
            val pulley = g.from ?: continue
            val end = bodyPoint(g.on)
            when (g.handle) {
                "rope" -> {
                    val d = (end - pulley).unit
                    fill(capsule(end - d * 9.0, end + d * 2.0, 2.2, 2.4), c.gear)
                }
                "single" -> {
                    fill(circle(end, 3.6), c.gear)
                    fill(circle(end, 1.8), c.body)
                }
                "none" -> Unit
                else -> fill(circle(end, 3.2), c.gear)
            }
        }
        for (g in motion.gear(Gear.Kind.Handle)) fill(circle(bodyPoint(g.on), 3.4), c.gear)
        for (g in motion.gear(Gear.Kind.Roller)) {
            val at = g.at ?: bodyPoint(g.on)
            fill(circle(at, g.radius ?: 6.5), c.prop)
            fill(circle(at, 2.2), c.gear)
        }
    }

    private companion object {
        /** The torso's outline, from the hips up the front, over the shoulders and down the back. */
        val SIDE_TORSO = listOf(
            -7.0 to 3.0, 2.0 to 9.0, 16.0 to 9.0, 29.0 to 12.5, 41.0 to 12.5, 50.5 to 8.0,
            54.0 to -2.0, 51.0 to -9.5, 39.0 to -11.5, 24.0 to -8.5, 11.0 to -10.5, 0.0 to -14.5, -8.5 to -9.0,
        )
    }
}

/** A leg's parts and the muscles on them. */
private class LegShape(l: Figure.Leg) {
    val thigh: Path
    val shin: Path
    val foot: Path
    val quads: Path
    val hamstrings: Path
    val calves: Path

    init {
        val td = (l.knee - l.hip).unit
        val sd = (l.ankle - l.knee).unit
        val midThigh = Vec.lerp(l.hip, l.knee, 0.42) + td.front * 1.0
        val calf = Vec.lerp(l.knee, l.ankle, 0.3) + sd.front * -1.8
        val turn = l.foot * PI / 180
        val heel = l.ankle + Vec(-4.5, 2.9).rotated(turn)
        val toe = l.ankle + Vec(17.0, 4.2).rotated(turn)
        thigh = union(capsule(l.hip, midThigh, 10.4, 9.2), capsule(midThigh, l.knee, 9.2, 6.6))
        shin = union(capsule(l.knee, calf, 6.6, 7.2), capsule(calf, l.ankle, 7.2, 3.9))
        foot = union(capsule(heel, toe, 4.5, 3.2), capsule(l.ankle, heel, 3.9, 4.5))
        quads = muscleShape(l.hip, l.knee, 10.4, 6.6, Triple(0.12, 0.5, 0.9), Triple(0.55, 0.72, 0.7), 1.0)
        hamstrings = muscleShape(l.hip, l.knee, 10.4, 6.6, Triple(0.14, 0.5, 0.88), Triple(0.5, 0.62, 0.55), -1.0)
        calves = muscleShape(l.knee, l.ankle, 7.0, 3.9, Triple(0.08, 0.3, 0.62), Triple(0.62, 0.78, 0.5), -1.0)
    }
}

/** An arm's parts and the muscles on them. */
private class ArmShape(val arm: Figure.Arm) {
    val upper: Path
    val forearm: Path
    val hand: Path

    /** Where the hand closes around a bar or handle. */
    val grip: Vec
    val biceps: Path
    val triceps: Path
    val forearms: Path

    init {
        val a = arm
        val ud = (a.elbow - a.shoulder).unit
        val fd = (a.wrist - a.elbow).unit
        val fore = Vec.lerp(a.elbow, a.wrist, 0.3) + fd.front * 0.4
        // The hand carries on from the forearm, bent at the wrist by `flex` degrees toward its front.
        val flex = a.flex * PI / 180
        val hd = (fd * cos(flex) + fd.front * sin(flex)).unit
        grip = a.wrist + hd * (6.5 * max(a.scale, 0.8))
        upper = capsule(a.shoulder, a.elbow, 6.8, 4.5)
        forearm = union(capsule(a.elbow, fore, 4.5, 5.0), capsule(fore, a.wrist, 5.0, 3.3))
        hand = capsule(a.wrist, grip, 3.6, 3.9)
        // The biceps grows a little as the elbow bends.
        val bent = acos(min(1.0, max(-1.0, ud.x * fd.x + ud.y * fd.y))) * 180 / PI
        val k = min(1.0, bent / 130)
        biceps = muscleShape(a.shoulder, a.elbow, 6.8, 4.5, Triple(0.2, 0.52, 0.86), Triple(0.55, 0.8 + 0.15 * k, 0.6), 1.0)
        triceps = muscleShape(a.shoulder, a.elbow, 6.8, 4.5, Triple(0.16, 0.5, 0.86), Triple(0.55, 0.72, 0.58), -1.0)
        forearms = muscleShape(a.elbow, a.wrist, 5.0, 3.3, Triple(0.1, 0.32, 0.75), Triple(0.8, 0.85, 0.6), 1.0)
    }

    /** The deltoid over the shoulder: its front (1), middle (0) or rear (-1). */
    fun delts(side: Double): Path {
        val w = if (side == 0.0) Triple(1.0, 1.0, 0.65) else Triple(0.62, 0.62, 0.45)
        return muscleShape(arm.shoulder, arm.elbow, 6.8, 4.5, Triple(0.0, 0.17, 0.4), w, side)
    }
}
