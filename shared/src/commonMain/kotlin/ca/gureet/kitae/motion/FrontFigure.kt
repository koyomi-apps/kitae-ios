// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The front view, as the iPhone has it (`FigureFront.swift`): the figure facing the viewer, for
// movements out to the sides. Angles work as in the side view for the +x arm and leg (the
// figure's left, on the right of the picture); the other arm and leg mirror them unless
// `otherArm`, or `otherThigh` and `otherShin`, say otherwise. `turn` turns the whole figure (a
// side plank lies at about -80), `lean` bends the body sideways above the waist, and the lengths
// shorten parts that point toward or away from the viewer.

object FrontBody {
    /** Half the distance between the shoulder joints. */
    const val SHOULDER = 19.5

    /** Half the distance between the hip joints. */
    const val HIP_JOINT = 9.0
}

/** Where an arm is in a front keyframe. */
sealed interface FrontArmPose {
    val foreLength: Double

    /** The arm swung out from hanging by [upper] degrees, the elbow bent by [bend]. */
    data class Swing(val upper: Double, val bend: Double, val upperLength: Double, override val foreLength: Double) : FrontArmPose

    /**
     * The hand at a point: on the body when [onTorso], otherwise in the scene. A point given for
     * `arm` is mirrored for the other arm; one given for `otherArm` is used as it is.
     */
    data class Reach(
        val hand: Vec,
        val onTorso: Boolean,
        val elbow: Double,
        val upperLength: Double,
        override val foreLength: Double,
    ) : FrontArmPose

    /** Two keyframes can only be blended when their arms are placed the same way. */
    val kind: String
        get() = when (this) {
            is Swing -> "swing"
            is Reach -> if (onTorso) "torso" else "scene"
        }

    companion object {
        fun lerp(a: FrontArmPose, b: FrontArmPose, t: Double): FrontArmPose {
            fun mix(x: Double, y: Double) = FigureMath.lerp(x, y, t)
            return when {
                a is Swing && b is Swing ->
                    Swing(mix(a.upper, b.upper), mix(a.bend, b.bend), mix(a.upperLength, b.upperLength), mix(a.foreLength, b.foreLength))
                a is Reach && b is Reach ->
                    Reach(Vec.lerp(a.hand, b.hand, t), a.onTorso, a.elbow, mix(a.upperLength, b.upperLength), mix(a.foreLength, b.foreLength))
                else -> if (t < 0.5) a else b
            }
        }
    }
}

/** One front keyframe. */
data class FrontPose(
    /** The middle of the pelvis, between the hip joints. */
    val hip: Vec,
    val turn: Double = 0.0,
    val lean: Double = 0.0,
    val torsoLength: Double = 1.0,
    val shrug: Double = 0.0,
    /** Tilts the head. */
    val head: Double = 0.0,
    /**
     * Below 1 lowers the head in front of the shoulders, as when bent over; below 0.5 it hangs,
     * showing its crown without a neck.
     */
    val headHeight: Double = 1.0,
    /** The legs' angles, for the +x leg; the other mirrors them unless [otherThigh] and [otherShin] say otherwise. */
    val thigh: Double = 3.0,
    val thighLength: Double = 1.0,
    val shin: Double = 1.5,
    val shinLength: Double = 1.0,
    val arm: FrontArmPose,
    val otherArm: FrontArmPose? = null,
    val otherThigh: Double? = null,
    val otherShin: Double? = null,
    /**
     * The +x ankle, for legs that reach for their feet instead of taking angles (feet planted wide
     * while the body rises and falls): the knees are worked out, bending outward, and the other
     * leg mirrors.
     */
    val ankle: Vec? = null,
) {
    companion object {
        fun lerp(a: FrontPose, b: FrontPose, t: Double): FrontPose {
            fun mix(x: Double, y: Double) = FigureMath.lerp(x, y, t)
            fun mixOptional(x: Double?, y: Double?) = if (x != null && y != null) mix(x, y) else x ?: y
            val aOther = a.otherArm
            val bOther = b.otherArm
            val other = if (aOther != null && bOther != null) FrontArmPose.lerp(aOther, bOther, t) else aOther ?: bOther
            val aAnkle = a.ankle
            val bAnkle = b.ankle
            val ankle = if (aAnkle != null && bAnkle != null) Vec.lerp(aAnkle, bAnkle, t) else aAnkle ?: bAnkle
            return FrontPose(
                hip = Vec.lerp(a.hip, b.hip, t),
                turn = mix(a.turn, b.turn),
                lean = mix(a.lean, b.lean),
                torsoLength = mix(a.torsoLength, b.torsoLength),
                shrug = mix(a.shrug, b.shrug),
                head = mix(a.head, b.head),
                headHeight = mix(a.headHeight, b.headHeight),
                thigh = mix(a.thigh, b.thigh),
                thighLength = mix(a.thighLength, b.thighLength),
                shin = mix(a.shin, b.shin),
                shinLength = mix(a.shinLength, b.shinLength),
                arm = FrontArmPose.lerp(a.arm, b.arm, t),
                otherArm = other,
                otherThigh = mixOptional(a.otherThigh, b.otherThigh),
                otherShin = mixOptional(a.otherShin, b.otherShin),
                ankle = ankle,
            )
        }
    }
}

/** A front pose worked out into joint positions. */
class FrontFigure(val pose: FrontPose) {
    data class Leg(
        val hip: Vec,
        val knee: Vec,
        val ankle: Vec,
        /** 1 for the +x leg, -1 for the other. */
        val side: Double,
    )

    data class Arm(val shoulder: Vec, val elbow: Vec, val wrist: Vec, val side: Double, val foreLength: Double)

    private val radians = pose.turn * PI / 180

    /** Up and toward the +x side, turned with the figure. */
    val up: Vec = Vec.direction(pose.turn)
    val side: Vec = Vec(cos(radians), sin(radians))
    val headUp: Vec = Vec.direction(pose.turn + pose.lean + pose.head)
    val neckBase: Vec = torso(55.0, 0.0)
    val neckTop: Vec = neckBase + Vec.direction(pose.turn + pose.lean) * (9 * pose.torsoLength)
    val head: Vec = neckTop + headUp * (10.5 * pose.headHeight)
    val showsNeck: Boolean = pose.headHeight > 0.5

    /** The -x arm first, so the +x arm is drawn in front of it. */
    val arms: List<Arm>
    val legs: List<Leg>

    init {
        val p = pose
        fun arm(armPose: FrontArmPose, s: Double, explicit: Boolean): Arm {
            val shoulder = torso(48.0, FrontBody.SHOULDER * s)
            fun mirrored(d: Vec) = Vec(d.x * s, d.y)
            return when (armPose) {
                is FrontArmPose.Swing -> {
                    val elbow = shoulder + mirrored(Vec.direction(180 - armPose.upper)) * (FigureBody.UPPER_ARM * armPose.upperLength)
                    val wrist = elbow + mirrored(Vec.direction(180 - armPose.upper - armPose.bend)) * (FigureBody.FOREARM * armPose.foreLength)
                    Arm(shoulder, elbow, wrist, s, armPose.foreLength)
                }
                is FrontArmPose.Reach -> {
                    val hand = armPose.hand
                    val target = when {
                        armPose.onTorso -> torso(hand.x, hand.y * s)
                        explicit -> hand
                        else -> Vec(p.hip.x + (hand.x - p.hip.x) * s, hand.y)
                    }
                    val l1 = FigureBody.UPPER_ARM * armPose.upperLength
                    val l2 = FigureBody.FOREARM * armPose.foreLength
                    val elbow = FigureMath.joint(shoulder, target, l1, l2, armPose.elbow * s)
                    Arm(shoulder, elbow, elbow + (target - elbow).unit * l2, s, armPose.foreLength)
                }
            }
        }
        fun leg(s: Double): Leg {
            val hip = p.hip + side * (FrontBody.HIP_JOINT * s)
            fun mirrored(d: Vec) = Vec(d.x * s, d.y)
            val l1 = FigureBody.THIGH * p.thighLength
            val l2 = FigureBody.SHIN * p.shinLength
            val foot = p.ankle
            if (foot != null) {
                val target = Vec(p.hip.x + (foot.x - p.hip.x) * s, foot.y)
                val knee = FigureMath.joint(hip, target, l1, l2, -s)
                return Leg(hip, knee, knee + (target - knee).unit * l2, s)
            }
            val thigh = if (s < 0) p.otherThigh ?: p.thigh else p.thigh
            val shin = if (s < 0) p.otherShin ?: p.shin else p.shin
            val knee = hip + mirrored(Vec.direction(180 - thigh)).rotated(radians) * l1
            val ankle = knee + mirrored(Vec.direction(180 - shin)).rotated(radians) * l2
            return Leg(hip, knee, ankle, s)
        }
        arms = listOf(arm(p.otherArm ?: p.arm, -1.0, explicit = p.otherArm != null), arm(p.arm, 1.0, explicit = false))
        legs = listOf(leg(-1.0), leg(1.0))
    }

    /** A point on the body: [along] the spine from the middle of the pelvis, then [out] to the +x side. */
    fun torso(along: Double, out: Double): Vec {
        val p = pose
        // Shrugging lifts the shoulders fully and the base of the neck a little.
        val lift = p.shrug * FigureMath.smoothstep((along - 42) / 4) * (if (along >= 55) 0.35 else 1.0)
        val q = p.hip + up * ((along + lift) * p.torsoLength) + side * out
        if (p.lean == 0.0) return q
        // The body bends sideways above the waist by the lean, smoothly between 10 and 26 along it.
        val pivot = p.hip + up * (18 * p.torsoLength)
        return pivot + (q - pivot).rotated(p.lean * PI / 180 * FigureMath.smoothstep((along - 10) / 16))
    }

    /** The body's angle at a point [along] the spine, for shapes that turn with it. */
    fun turn(along: Double): Double = pose.turn + pose.lean * FigureMath.smoothstep((along - 10) / 16)
}
