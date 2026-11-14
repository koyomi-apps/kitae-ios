// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import ca.gureet.kitae.motion.FigureMuscles.Shade
import ca.gureet.kitae.motion.FigureShapes.capsule
import ca.gureet.kitae.motion.FigureShapes.circle
import ca.gureet.kitae.motion.FigureShapes.ellipse
import ca.gureet.kitae.motion.FigureShapes.hexagon
import ca.gureet.kitae.motion.FigureShapes.outline
import ca.gureet.kitae.motion.FigureShapes.union
import ca.gureet.kitae.muscles.Muscle
import kotlin.math.PI
import kotlin.math.max

/** The front figure's outline: from the crotch round the hips, up the side, over the shoulders and down the other side. */
private val FRONT_TORSO = listOf(
    -10.0 to 0.0, -8.0 to 9.0, -4.0 to 15.0, 4.0 to 16.5, 14.0 to 14.0, 24.0 to 15.0, 34.0 to 18.0, 42.0 to 21.0, 48.0 to 22.0,
    51.5 to 17.5, 54.0 to 10.0, 55.5 to 5.0, 55.5 to -5.0, 54.0 to -10.0, 51.5 to -17.5, 48.0 to -22.0, 42.0 to -21.0,
    34.0 to -18.0, 24.0 to -15.0, 14.0 to -14.0, 4.0 to -16.5, -4.0 to -15.0, -8.0 to -9.0,
)

private class FrontLegParts(val thigh: Path, val shin: Path, val foot: Path) {
    val all: Path = union(thigh, shin, foot)
}

private class FrontArmParts(val arm: FrontFigure.Arm) {
    val fd: Vec = (arm.wrist - arm.elbow).unit
    val grip: Vec = arm.wrist + fd * (6.5 * max(arm.foreLength, 0.5))
    val upper: Path = capsule(arm.shoulder, arm.elbow, 6.8, 4.5)
    val forearm: Path
    val hand: Path = capsule(arm.wrist, grip, 3.6, 3.9)
    val all: Path

    init {
        val fore = Vec.lerp(arm.elbow, arm.wrist, 0.3)
        forearm = union(capsule(arm.elbow, fore, 4.5, 5.0), capsule(fore, arm.wrist, 5.0, 3.3))
        all = union(upper, forearm, hand)
    }
}

/**
 * Draws a front figure back to front (the iPhone's `FrontPainter`): the shadow, frames and stacks,
 * pads, legs, body and muscles (legs in front of the body when they come toward the viewer), head,
 * cables, arms, and gear in the hands. Arms, and legs in front of the body, get a thin outline in
 * the card's colour so they stand apart from the body behind them.
 */
internal fun FigurePainter.drawFront(scope: DrawScope, f: FrontFigure) = with(scope) {
    val c = colors
    val turn = f.pose.turn
    val r = turn * PI / 180

    // An outline in the card's colour, left off round a joint so limbs stay joined to the body.
    fun separate(path: Path, joint: Vec, radius: Double) {
        clipPath(circle(joint, radius), ClipOp.Difference) {
            drawPath(path, card, style = Stroke(width = 3.2f, join = StrokeJoin.Round))
        }
    }

    fun tint(shade: Shade): Color = if (shade == Shade.Primary) muscle else c.muscleSecondary

    fun legParts(l: FrontFigure.Leg): FrontLegParts {
        val mid = Vec.lerp(l.hip, l.knee, 0.42)
        val calf = Vec.lerp(l.knee, l.ankle, 0.3)
        return FrontLegParts(
            thigh = union(capsule(l.hip, mid, 10.4, 9.2), capsule(mid, l.knee, 9.2, 6.6)),
            shin = union(capsule(l.knee, calf, 6.6, 7.2), capsule(calf, l.ankle, 7.2, 3.9)),
            foot = ellipse(l.ankle + Vec(1.5 * l.side, 3.4).rotated(r), 5.2, 3.6, turn),
        )
    }

    fun drawLegs(inFront: Boolean) {
        for (l in f.legs) {
            val parts = legParts(l)
            if (inFront) separate(parts.all, l.hip, 11.5)
            fill(parts.thigh, c.body)
            fill(parts.shin, c.body)
            fill(parts.foot, c.body)
            // The adductors, inside the thighs, show only from the front.
            val shade = muscles[Muscle.Adductors] ?: continue
            fillInside(
                parts.thigh,
                muscleShape(l.hip, l.knee, 10.4, 6.6, Triple(0.1, 0.42, 0.8), Triple(0.5, 0.55, 0.45), -l.side),
                tint(shade),
            )
        }
    }

    val arms = f.arms.map(::FrontArmParts)
    fun grip(side: Double): Vec = arms.firstOrNull { it.arm.side == side }?.grip ?: Vec.ZERO

    fun frontPad(g: Gear) {
        val from = g.from ?: return
        val to = g.to ?: return
        pad(g.copy(anchor = null), from, to)
    }

    fill(ellipse(motion.shadow, motion.shadowWidth, 3.4, 0.0), c.shadow)
    stacksAndPosts()
    for (g in motion.gear(Gear.Kind.Pad)) if (!g.front) frontPad(g)
    if (!motion.legsFront) drawLegs(inFront = false)

    // The body and its muscles.
    val torso = outline(FRONT_TORSO.map { f.torso(it.first, it.second) })
    fill(torso, c.body)
    val sides = listOf(1.0, -1.0)

    // The glutes show at the outer hips; with legs in front, on the outside of the thighs.
    fun glutes(s: Double): Path = if (motion.legsFront) {
        ellipse(f.torso(2.0, 17 * s), 3.2, 7.0, f.turn(2.0))
    } else {
        ellipse(f.torso(1.0, 13.5 * s), 3.6, 7.5, f.turn(1.0))
    }
    for (shade in Shade.entries) {
        val color = tint(shade)
        if (muscles[Muscle.Chest] == shade) {
            for (s in sides) fillInside(torso, ellipse(f.torso(37.5, 8.5 * s), 8.2, 6.4, f.turn(37.5)), color)
        }
        if (muscles[Muscle.Abs] == shade) fillInside(torso, capsule(f.torso(5.0, 0.0), f.torso(31.0, 0.0), 5.0, 5.2), color)
        if (muscles[Muscle.Obliques] == shade) {
            for (s in sides) fillInside(torso, ellipse(f.torso(16.0, 12 * s), 3.6, 8.5, f.turn(16.0)), color)
        }
        if (muscles[Muscle.Lats] == shade) {
            for (s in sides) fillInside(torso, ellipse(f.torso(31.0, 16 * s), 3.4, 8.0, f.turn(31.0)), color)
        }
        if (muscles[Muscle.Traps] == shade) {
            for (s in sides) fillInside(torso, capsule(f.torso(53.5, 6 * s), f.torso(50.5, 15.5 * s), 3.2, 3.6), color)
        }
        if (muscles[Muscle.Glutes] == shade && !motion.legsFront) {
            for (s in sides) fillInside(torso, glutes(s), color)
        }
    }
    if (motion.legsFront) {
        drawLegs(inFront = true)
        muscles[Muscle.Glutes]?.let { shade ->
            for (s in sides) fillInside(union(torso, legParts(f.legs[if (s > 0) 1 else 0]).thigh), glutes(s), tint(shade))
        }
    }

    // Neck and head; a head hanging forward shows its crown in front of the shoulders.
    val headTurn = turn + f.pose.lean + f.pose.head
    if (f.showsNeck) {
        fill(capsule(f.neckBase, f.neckTop, 5.6, 5.0), c.body)
        fill(ellipse(f.head + f.headUp * 1.5, 9.8, 10.4, headTurn), c.body)
        fill(ellipse(f.head - f.headUp * 5.0, 7.2, 6.8, headTurn), c.body)
    } else {
        drawPath(circle(f.head, 10.6), card, style = Stroke(width = 3.2f))
        fill(circle(f.head, 10.0), c.body)
    }
    for (g in motion.gear(Gear.Kind.Pad)) if (g.front) frontPad(g)

    // Cables run in front of the body to the hand on their side, or to that side's ankle cuff.
    for (g in motion.gear(Gear.Kind.Cable)) {
        val pulley = g.from ?: continue
        val cuff = if (g.on == "ankle") f.legs.firstOrNull { it.side == g.side }?.ankle else null
        val end = cuff ?: grip(g.side)
        fill(capsule(pulley, end, 0.8, 0.8), c.gear)
        fill(circle(pulley, 4.2), c.gear)
        fill(circle(pulley, 1.7), c.prop)
        if (cuff != null) {
            fill(circle(end, 3.6), c.gear)
            fill(circle(end, 1.8), c.body)
        }
    }
    // A band stretched between the hands, in front of the body.
    if (motion.gear(Gear.Kind.Band).isNotEmpty()) fill(capsule(grip(1.0), grip(-1.0), 1.2, 1.2), c.gear)
    // A barbell across the front of the body: the bar through both hands, its plates edge-on.
    for (g in motion.gear(Gear.Kind.Barbell)) {
        val mid = Vec.lerp(grip(-1.0), grip(1.0), 0.5)
        val h = g.radius ?: 17.0
        fill(capsule(mid - Vec(98.0, 0.0), mid + Vec(98.0, 0.0), 1.7, 1.7), c.gear)
        for (s in listOf(-1.0, 1.0)) {
            val at = mid + Vec(86 * s, 0.0)
            fill(capsule(at - Vec(0.0, h), at + Vec(0.0, h), 3.4, 3.4), c.gear)
        }
    }

    // The arms, the -x one first. From the front the three delts are one shoulder cap, shaded as
    // the most worked of them.
    val delts = listOf(Muscle.FrontDelts, Muscle.SideDelts, Muscle.RearDelts).mapNotNull { muscles[it] }.maxOrNull()
    for (p in arms) {
        val a = p.arm
        separate(p.all, a.shoulder, 9.0)
        fill(p.upper, c.body)
        fill(p.forearm, c.body)
        fill(p.hand, c.body)
        for (shade in Shade.entries) {
            val color = tint(shade)
            if (delts == shade) {
                val cap = union(p.upper, circle(a.shoulder, 7.2))
                fillInside(cap, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, Triple(0.0, 0.16, 0.38), Triple(1.0, 1.0, 0.7), 0.0), color)
            }
            if (muscles[Muscle.Triceps] == shade) {
                fillInside(p.upper, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, Triple(0.25, 0.55, 0.88), Triple(0.5, 0.6, 0.5), a.side), color)
            }
            if (muscles[Muscle.Biceps] == shade) {
                fillInside(p.upper, muscleShape(a.shoulder, a.elbow, 6.8, 4.5, Triple(0.25, 0.55, 0.88), Triple(0.5, 0.62, 0.5), -a.side), color)
            }
        }
    }

    // What the hands hold, and pads beside the knees.
    val dumbbells = motion.gear(Gear.Kind.Dumbbells)
    if (dumbbells.isNotEmpty()) {
        val onlyOne = dumbbells.any { it.one }
        val profile = dumbbells.any { it.profile }
        for (p in arms) {
            if (onlyOne && p.arm.side != 1.0) continue
            val g = p.grip
            if (profile) {
                val axis = p.fd.front
                fill(capsule(g - axis * 7.0, g + axis * 7.0, 1.7, 1.7), c.gear)
                for (k in listOf(-8.0, 8.0)) {
                    val end = g + axis * k
                    fill(capsule(end - p.fd * 6.5, end + p.fd * 6.5, 2.8, 2.8), c.gear)
                }
            } else {
                fill(hexagon(g, 8.6, 30.0), c.gear)
                fill(hexagon(g, 6.0, 30.0), c.gearFace)
                fill(circle(g, 2.4), c.body)
            }
        }
    }
    for (g in motion.gear(Gear.Kind.Plate)) plate(grip(1.0), g.radius ?: 12.0)
    if (motion.gear(Gear.Kind.Handle).isNotEmpty()) {
        for (p in arms) fill(circle(p.grip, 3.4), c.gear)
    }
    // Outside the knees, or inside them (`side` -1).
    for (g in motion.gear(Gear.Kind.KneePads)) {
        for (l in f.legs) {
            val at = l.knee + f.side * (10.5 * l.side * g.side)
            fill(capsule(at + f.up * 8.0, at - f.up * 8.0, 4.0, 4.0), c.prop)
        }
    }
}
