// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.muscles.ExerciseMuscles
import ca.gureet.kitae.muscles.Muscle
import ca.gureet.kitae.muscles.MuscleWork

/** Something the figure holds or uses, or a piece of a bench or machine, as the iPhone has it. */
data class Gear(
    val kind: Kind,
    /**
     * The part of the body it follows: "hands", "back", "front", "shins", "ankle", "shin", "calf",
     * "thigh", "knee", "hips" or "chest".
     */
    val on: String? = null,
    val from: Vec? = null,
    val to: Vec? = null,
    val at: Vec? = null,
    val anchor: Vec? = null,
    val thick: Double = 8.0,
    val legs: List<Double> = emptyList(),
    val width: Double = 4.0,
    val length: Double = 40.0,
    val handle: String = "bar",
    val stack: Boolean = false,
    /** Dumbbells seen side-on, for a neutral grip. */
    val profile: Boolean = false,
    /** One dumbbell hanging from both hands, as in overhead extensions. */
    val hang: Boolean = false,
    /** Only in the near hand. */
    val one: Boolean = false,
    val radius: Double? = null,
    val front: Boolean = false,
    val top: Double = 70.0,
    val bottom: Double = 0.0,
    val out: Double? = null,
    val side: Double = 1.0,
) {
    /** [stored] is the kind's name in `ExerciseMotions.json`. */
    enum class Kind(val stored: String) {
        /**
         * A plate seen end-on: in the hands, across the shoulders (`on` "back"), on the front of
         * the shoulders ("front") or across the hips ("hips"). In the front view, a bar across the
         * body through both hands, its plates edge-on.
         */
        Barbell("barbell"),

        /** A barbell with smaller plates. */
        EzBar("ezbar"),

        /** A trap bar in the hands: its frame front to back through the grip, a plate at the grip. */
        TrapBar("trapbar"),

        /** A kettlebell in the hands, its bell beyond the grip along the forearm. */
        Kettlebell("kettlebell"),

        /** In the hands, or held at the chest (`on` "chest") for a goblet squat. */
        Dumbbells("dumbbells"),

        /**
         * A bench, seat or pad: its top edge from `from` to `to`, `thick` deep, with legs down to
         * the floor at the x of each of `legs`. `front` pads are drawn over the body, under the
         * near arm. With `on` "back" or "front" it moves with the body, from `top` to `bottom`
         * along the spine; with `on` "shins" it's a machine's knee pad under the near shin, on a
         * lever from `anchor`.
         */
        Pad("pad"),

        /** A frame member from `from` to `to`, `width` wide. */
        Post("post"),

        /**
         * A cable from a pulley at `from` to the hands (or `on`), ending in a `handle`: "bar",
         * "rope", "single" or "none". `stack` draws the weight stack under the pulley. In the
         * front view, `side` picks the hand, or with `on` "ankle" that side's ankle.
         */
        Cable("cable"),

        /** A pull-up bar seen end-on at `at`. */
        Bar("bar"),

        /** A machine's foam roller, at `at` or following the body (`on`). */
        Roller("roller"),

        /** A machine's handle in the hands. */
        Handle("handle"),

        /**
         * A plate under the near foot that moves with it (leg press). With `on` "ball" it stays
         * square to the rail from `from` to `to`, pushed by the ball of the foot (leg press calf
         * raises).
         */
        FootPlate("footplate"),

        /** A bar from `anchor` on the floor to the hands, plates at its end (T-bar rows). */
        TBar("tbar"),

        /** An ab wheel in the hands. */
        Wheel("wheel"),

        /** A weight plate held in both hands: face-on from the front, upright and edge-on from the side. */
        Plate("plate"),

        /** Pads on the outside of the knees (front view), or the inside with `side` -1. */
        KneePads("kneepads"),

        /** A resistance band stretched between the hands (front view). */
        Band("band"),
        ;

        companion object {
            fun fromStored(stored: String): Kind? = entries.firstOrNull { it.stored == stored }
        }
    }
}

/**
 * An exercise's animation, as the iPhone has it (`ExerciseMotion.swift`): two keyframes of the
 * figure and the gear it uses, read from `ExerciseMotions.json`. Most are drawn from the side;
 * movements out to the sides face the viewer. They're keyed by the exercises' library keys
 * ([Exercise.libraryKey]), which never change.
 */
data class ExerciseMotion(
    /** The library key of the exercise it shows. */
    val key: String,
    /** The exercise's name as Kitae starts with it, to make the file easier to read. */
    val name: String,
    val keyframes: Keyframes,
    /** In the front view, legs that come toward the viewer (sitting) are drawn over the body. */
    val legsFront: Boolean = false,
    val gear: List<Gear> = emptyList(),
    /**
     * The ends of the hold at the start, the move to the end, the hold at the end and the move
     * back, as fractions of a loop.
     */
    val timing: List<Double> = listOf(0.08, 0.5, 0.58, 0.96),
    /** Length of one loop. */
    val seconds: Double = 2.4,
    /** Moves the figure sideways so it sits in the middle of its card. */
    val shift: Double = 0.0,
    /**
     * Draws the figure smaller (below 1) for exercises that need more room, such as pull-ups, or
     * bigger for ones close to the floor.
     */
    val zoom: Double = 1.0,
    /** Middle and half width of the shadow on the floor. */
    val shadow: Vec = Vec(4.0, 0.0),
    val shadowWidth: Double = 40.0,
) {
    sealed interface Keyframes {
        data class Side(val from: FigurePose, val to: FigurePose) : Keyframes

        data class Front(val from: FrontPose, val to: FrontPose) : Keyframes
    }

    /** How far from the start pose (0) toward the end pose (1) the loop is at [t] (0…1). */
    fun progress(t: Double): Double = FigureMath.phase(t, timing)

    fun gear(kind: Gear.Kind): List<Gear> = gear.filter { it.kind == kind }

    /** The muscles the figure shades, from the exercise's muscle map. */
    fun muscles(muscles: ExerciseMuscles): FigureMuscles = FigureMuscles(muscles.byKey[key])
}

/**
 * The muscles the figure shades for an exercise, from its muscle map: the ones it works most in
 * olive and the ones that help lighter. The figure has no shapes of its own for the upper back and
 * the hip's abductors, so its traps and glutes show them; the adductors, inside the thighs, show
 * only from the front.
 */
class FigureMuscles(work: MuscleWork?) {
    /** The lighter shade comes first, so where muscles meet the ones worked most are drawn on top. */
    enum class Shade { Secondary, Primary }

    val primary: Set<Muscle> = work?.primary.orEmpty().mapTo(LinkedHashSet(), ::shape)
    val secondary: Set<Muscle> = work?.secondary.orEmpty().mapTo(LinkedHashSet(), ::shape) - primary

    /** How the figure shades [muscle], or null when the exercise doesn't work it. */
    operator fun get(muscle: Muscle): Shade? = when (muscle) {
        in primary -> Shade.Primary
        in secondary -> Shade.Secondary
        else -> null
    }

    override fun equals(other: Any?) = other is FigureMuscles && primary == other.primary && secondary == other.secondary

    override fun hashCode() = 31 * primary.hashCode() + secondary.hashCode()

    override fun toString() = "FigureMuscles(primary=$primary, secondary=$secondary)"

    companion object {
        /** The muscle whose shape shows [muscle] on the figure. */
        fun shape(muscle: Muscle): Muscle = when (muscle) {
            Muscle.UpperBack -> Muscle.Traps
            Muscle.Abductors -> Muscle.Glutes
            else -> muscle
        }
    }
}

/** The animations, from the text of `ExerciseMotions.json`, which each app hands in. */
class ExerciseMotions(
    /** In the file's order. */
    val all: List<ExerciseMotion>,
) {
    /** By library key. */
    val byKey: Map<String, ExerciseMotion> = all.distinctBy { it.key }.associateBy { it.key }

    /** The animation of an exercise, if it's one of Kitae's with one. */
    fun motion(exercise: Exercise): ExerciseMotion? = exercise.libraryKey?.let { byKey[it] }

    companion object {
        /**
         * Reads the text of `ExerciseMotions.json`, throwing
         * [kotlinx.serialization.SerializationException] when it isn't the animations.
         */
        fun decode(text: String): ExerciseMotions = ExerciseMotions(MotionFile.read(text))
    }
}
