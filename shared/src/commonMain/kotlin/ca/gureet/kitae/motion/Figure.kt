// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The geometry behind the exercise animations, as the iPhone has it (`FigureRig.swift`): a flat
// side view of a person, posed from a few numbers per keyframe. Units are points for a figure
// about 190 tall. The floor is y = 0, y grows downward, x grows the way the figure faces, and the
// near ankle usually stands at x = 0. Drawing the figure is each app's.

/** A point or a direction in the figure's space. */
data class Vec(val x: Double, val y: Double) {
    operator fun plus(other: Vec) = Vec(x + other.x, y + other.y)

    operator fun minus(other: Vec) = Vec(x - other.x, y - other.y)

    operator fun times(k: Double) = Vec(x * k, y * k)

    val length: Double
        get() = sqrt(x * x + y * y)

    val unit: Vec
        get() = if (length > 0) this * (1 / length) else ZERO

    /** For a limb that points down the body, the side it bends toward when it hangs straight down. */
    val front: Vec
        get() = Vec(y, -x)

    fun rotated(radians: Double) = Vec(x * cos(radians) - y * sin(radians), x * sin(radians) + y * cos(radians))

    companion object {
        val ZERO = Vec(0.0, 0.0)

        /** A unit direction: 0° up, 90° forward, 180° down. */
        fun direction(degrees: Double): Vec {
            val r = degrees * PI / 180
            return Vec(sin(r), -cos(r))
        }

        fun lerp(a: Vec, b: Vec, t: Double) = a + (b - a) * t
    }
}

/** Lengths of a figure 190 tall, in standard body proportions. */
object FigureBody {
    const val THIGH = 46.5
    const val SHIN = 46.7
    const val UPPER_ARM = 35.3
    const val FOREARM = 27.7
    const val ANKLE_HEIGHT = 7.4

    /** The far arm and leg sit a little behind the near ones. */
    val FAR_OFFSET = Vec(-3.6, -1.6)
}

object FigureMath {
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /**
     * The middle joint (knee or elbow) of a two-part limb reaching from [root] to [target].
     * [bend] picks the side the joint goes to.
     */
    fun joint(root: Vec, target: Vec, l1: Double, l2: Double, bend: Double): Vec {
        val d = target - root
        val dist = minOf(maxOf(d.length, abs(l1 - l2) + 0.01), l1 + l2 - 0.01)
        val cosA = (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist)
        return root + d.unit.rotated(bend * acos(minOf(1.0, maxOf(-1.0, cosA)))) * l1
    }

    /** Eases in and out over 0…1. */
    fun ease(x: Double) = 0.5 - 0.5 * cos(PI * minOf(1.0, maxOf(0.0, x)))

    /** 0 below 0, 1 above 1, and a smooth S between. */
    fun smoothstep(x: Double): Double {
        val k = minOf(1.0, maxOf(0.0, x))
        return k * k * (3 - 2 * k)
    }

    /**
     * How far from the start pose (0) toward the end pose (1) a loop is at [t] (0…1). [k] holds
     * the ends of: the hold at the start, the move to the end, the hold at the end, and the move
     * back.
     */
    fun phase(t: Double, k: List<Double>): Double {
        if (k.size != 4) return 0.0
        return when {
            t < k[0] -> 0.0
            t < k[1] -> ease((t - k[0]) / (k[1] - k[0]))
            t < k[2] -> 1.0
            t < k[3] -> 1 - ease((t - k[2]) / (k[3] - k[2]))
            else -> 0.0
        }
    }
}

/** Where an arm is in a keyframe. */
sealed interface ArmPose {
    /**
     * The upper arm swung forward from hanging by [upper] degrees, the elbow bent by [bend].
     * [upperLength] shortens the upper arm when it points toward the viewer, as in a press.
     * [flex] bends the hand at the wrist, in degrees toward the front of the forearm.
     */
    data class Swing(val upper: Double, val bend: Double, val upperLength: Double, val flex: Double = 0.0) : ArmPose

    /**
     * The hand at a point: on the body (along the spine, then out the front) when [onTorso],
     * otherwise in the scene. [scale] shortens an arm that reaches out sideways, [upperLength]
     * shortens just its upper half, and [elbow] picks the side the elbow goes to.
     */
    data class Reach(
        val hand: Vec,
        val onTorso: Boolean,
        val scale: Double,
        val upperLength: Double,
        val elbow: Double,
        val flex: Double = 0.0,
    ) : ArmPose

    /** Two keyframes can only be blended when their arms are placed the same way. */
    val kind: String
        get() = when (this) {
            is Swing -> "swing"
            is Reach -> if (onTorso) "torso" else "scene"
        }

    companion object {
        fun lerp(a: ArmPose, b: ArmPose, t: Double): ArmPose = when {
            a is Swing && b is Swing -> Swing(
                FigureMath.lerp(a.upper, b.upper, t),
                FigureMath.lerp(a.bend, b.bend, t),
                FigureMath.lerp(a.upperLength, b.upperLength, t),
                FigureMath.lerp(a.flex, b.flex, t),
            )
            a is Reach && b is Reach -> Reach(
                Vec.lerp(a.hand, b.hand, t),
                a.onTorso,
                FigureMath.lerp(a.scale, b.scale, t),
                FigureMath.lerp(a.upperLength, b.upperLength, t),
                a.elbow,
                FigureMath.lerp(a.flex, b.flex, t),
            )
            else -> if (t < 0.5) a else b
        }
    }
}

/** One keyframe of a figure seen from the side. */
data class FigurePose(
    /** The near hip joint. */
    val hip: Vec,
    /** The spine's direction: 0 upright, 90 lying face down with the head forward, -90 lying face up with the head back. */
    val lean: Double,
    /** How far the spine above the waist bends forward, as in a crunch. */
    val curl: Double = 0.0,
    /** How far the shoulders rise, as in a shrug. */
    val shrug: Double = 0.0,
    /** The head's direction, which follows the spine when not given. */
    val head: Double? = null,
    /** The near ankle, which the leg reaches for unless [thigh] and [shin] give its angles. */
    val ankle: Vec = Vec(0.0, -FigureBody.ANKLE_HEIGHT),
    /** Turns the foot about the ankle: positive points the toes down. */
    val foot: Double = 0.0,
    /** The leg's angles, for legs that swing (leg raises, leg curls) rather than stand. */
    val thigh: Double? = null,
    val shin: Double? = null,
    /** The far leg, when it isn't beside the near one. */
    val farAnkle: Vec? = null,
    val farFoot: Double? = null,
    val farThigh: Double? = null,
    val farShin: Double? = null,
    /** The side the knees bend to when reaching for the ankle: -1 forward, 1 back. */
    val knee: Double = -1.0,
    /** A kneeling pose pinned at the knee: the hip follows from the thigh's angle. */
    val kneeAt: Vec? = null,
    val arm: ArmPose,
    /** The far arm, when it isn't doing the same as the near one. */
    val farArm: ArmPose? = null,
) {
    companion object {
        fun lerp(a: FigurePose, b: FigurePose, t: Double): FigurePose {
            fun mix(x: Double?, y: Double?) = if (x != null && y != null) FigureMath.lerp(x, y, t) else x ?: y
            fun mix(x: Vec?, y: Vec?) = if (x != null && y != null) Vec.lerp(x, y, t) else x ?: y
            fun mix(x: ArmPose?, y: ArmPose?) = if (x != null && y != null) ArmPose.lerp(x, y, t) else x ?: y
            return FigurePose(
                hip = Vec.lerp(a.hip, b.hip, t),
                lean = FigureMath.lerp(a.lean, b.lean, t),
                curl = FigureMath.lerp(a.curl, b.curl, t),
                shrug = FigureMath.lerp(a.shrug, b.shrug, t),
                head = mix(a.head, b.head),
                ankle = Vec.lerp(a.ankle, b.ankle, t),
                foot = FigureMath.lerp(a.foot, b.foot, t),
                thigh = mix(a.thigh, b.thigh),
                shin = mix(a.shin, b.shin),
                farAnkle = mix(a.farAnkle, b.farAnkle),
                farFoot = mix(a.farFoot, b.farFoot),
                farThigh = mix(a.farThigh, b.farThigh),
                farShin = mix(a.farShin, b.farShin),
                knee = a.knee,
                kneeAt = mix(a.kneeAt, b.kneeAt),
                arm = ArmPose.lerp(a.arm, b.arm, t),
                farArm = mix(a.farArm, b.farArm),
            )
        }
    }
}

/** A side pose worked out into joint positions. */
class Figure(pose: FigurePose) {
    data class Leg(val hip: Vec, val knee: Vec, val ankle: Vec, val foot: Double)

    data class Arm(
        val shoulder: Vec,
        val elbow: Vec,
        val wrist: Vec,
        val scale: Double,
        /** How far the hand bends at the wrist, in degrees toward the front of the forearm. */
        val flex: Double = 0.0,
    )

    val pose: FigurePose = pose.kneeAt?.let { kneeAt ->
        pose.thigh?.let { thigh -> pose.copy(hip = kneeAt - Vec.direction(thigh) * FigureBody.THIGH) }
    } ?: pose
    val spine: Vec = Vec.direction(this.pose.lean)
    val front: Vec = Vec(-spine.y, spine.x)
    val headDirection: Vec = Vec.direction(this.pose.head ?: (this.pose.lean + this.pose.curl))
    val neckBase: Vec = torso(52.0, 1.5)
    val neckTop: Vec = neckBase + headDirection * 9.0
    val head: Vec = neckTop + headDirection * 9.5 + Vec(-headDirection.y, headDirection.x) * 1.5
    val nearLeg: Leg
    val farLeg: Leg
    val nearArm: Arm
    val farArm: Arm

    init {
        val p = this.pose
        fun leg(ankle: Vec, foot: Double, thigh: Double?, shin: Double?, offset: Vec): Leg {
            val hip = p.hip + offset
            if (thigh != null && shin != null) {
                val knee = hip + Vec.direction(thigh) * FigureBody.THIGH
                return Leg(hip, knee, knee + Vec.direction(shin) * FigureBody.SHIN, foot)
            }
            val a = ankle + offset
            return Leg(hip, FigureMath.joint(hip, a, FigureBody.THIGH, FigureBody.SHIN, p.knee), a, foot)
        }
        val shoulderPoint = torso(48 + p.shrug, -0.5)
        fun arm(armPose: ArmPose, offset: Vec): Arm {
            val shoulder = shoulderPoint + offset
            return when (armPose) {
                is ArmPose.Swing -> {
                    val elbow = shoulder + Vec.direction(180 - armPose.upper) * (FigureBody.UPPER_ARM * armPose.upperLength)
                    val wrist = elbow + Vec.direction(180 - armPose.upper - armPose.bend) * FigureBody.FOREARM
                    Arm(shoulder, elbow, wrist, scale = 1.0, flex = armPose.flex)
                }
                is ArmPose.Reach -> {
                    val target = (if (armPose.onTorso) torso(armPose.hand.x, armPose.hand.y) else armPose.hand) + offset
                    val elbow = FigureMath.joint(
                        shoulder,
                        target,
                        FigureBody.UPPER_ARM * armPose.scale * armPose.upperLength,
                        FigureBody.FOREARM * armPose.scale,
                        armPose.elbow,
                    )
                    val wrist = elbow + (target - elbow).unit * (FigureBody.FOREARM * armPose.scale)
                    Arm(shoulder, elbow, wrist, armPose.scale, armPose.flex)
                }
            }
        }
        val far = FigureBody.FAR_OFFSET
        nearLeg = leg(p.ankle, p.foot, p.thigh, p.shin, Vec.ZERO)
        farLeg = leg(p.farAnkle ?: p.ankle, p.farFoot ?: p.foot, p.farThigh ?: p.thigh, p.farShin ?: p.shin, far)
        nearArm = arm(p.arm, Vec.ZERO)
        farArm = arm(p.farArm ?: p.arm, far)
    }

    /** A point on the body: [along] the spine from the hip, then [out] the front. */
    fun torso(along: Double, out: Double): Vec {
        val p = pose
        val q = p.hip + spine * along + front * out
        if (p.curl == 0.0) return q
        // The spine bends above the waist by the curl, smoothly between 10 and 26 along it.
        val pivot = p.hip + spine * 18.0
        return pivot + (q - pivot).rotated(p.curl * PI / 180 * FigureMath.smoothstep((along - 10) / 16))
    }

    /** The spine's direction at a point [along] it, following the curl. */
    fun spine(along: Double): Vec = spine.rotated(pose.curl * PI / 180 * FigureMath.smoothstep((along - 10) / 16))

    /** The direction the body faces at a point [along] the spine, as an angle like `lean`. */
    fun turn(along: Double): Double = pose.lean + pose.curl * FigureMath.smoothstep((along - 10) / 16)
}
