// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.SetType

/**
 * How an exercise's sets use energy, for a workout's estimate (see [WorkoutEnergy]), as the
 * iPhone has it (`ExerciseEnergy.swift`).
 *
 * A rep costs [perKilogram] kcal for each kilogram it moves: the weight logged times [loadFactor]
 * (2 for a pair of dumbbells, less on a sled pushed at an angle, -1 for a machine's help, which
 * comes off the body's weight), plus [bodyShare] of the body's own weight (most of it in a squat
 * or a pull-up, little in a curl), all times [sides] for exercises done one side at a time. A
 * hold costs [holdMETs] a second instead, for the body and, in a carry, for the weight carried as
 * well (the weight logged times [carryFactor]).
 *
 * The costs per kilogram come from sets whose whole cost was measured, aerobic, anaerobic and the
 * recovery after (Scott et al. 2014, "Total Energy Costs – Aerobic and Anaerobic, Exercise and
 * Recovery – of Five Resistance Exercises"): about 0.0075 kcal per kilogram a rep for squats,
 * 0.012 for deadlifts, 0.011 for presses and 0.0034 for calf raises, with the body's weight
 * counted. Rows, pulls, curls and other lifts of the arms use the press's figure, and bodyweight
 * squats and push-ups match Nakagata et al. (2022).
 */
data class ExerciseEnergy(
    val perKilogram: Double,
    val bodyShare: Double,
    val loadFactor: Double = 1.0,
    val sides: Double = 1.0,
    /** For holds, whose reps are seconds: METs while holding. */
    val holdMETs: Double? = null,
    /** For carries, holds that walk with the weight: the weight logged times this counts like body weight (2 for a pair of dumbbells). */
    val carryFactor: Double = 0.0,
) {
    /** The weight logged is an assisted machine's help, which comes off the body's weight. */
    val isAssisted: Boolean
        get() = loadFactor < 0

    /**
     * The energy of one set, in kcal, on top of resting. [reach] scales how far each rep moves the
     * weight, by the lifter's height (see [WorkoutEnergy.reach]); a hold doesn't move.
     */
    fun kilocalories(reps: Int, weightKg: Double, bodyKg: Double, reach: Double = 1.0): Double {
        val count = reps.coerceIn(0, MOST_REPS).toDouble()
        val load = weightKg.coerceIn(0.0, MOST_KILOGRAMS)
        holdMETs?.let { mets ->
            return maxOf(mets - 1, 0.0) * (bodyKg + load * carryFactor) * count / 3600 * sides
        }
        return perKilogram * maxOf(load * loadFactor + bodyShare * bodyKg, 0.0) * count * sides * reach
    }

    companion object {
        /** The most a set counts for, against typing slips. */
        const val MOST_REPS = 300
        const val MOST_KILOGRAMS = 600.0

        // Kcal for each kilogram moved, each rep.
        private const val SQUAT = 0.0075
        private const val HINGE = 0.012
        private const val ARMS = 0.011
        private const val RAISE = 0.012
        private const val CALF = 0.0034
        private const val LEG_MACHINE = 0.008
        private const val CORE = 0.008

        /**
         * Each of Kitae's exercises', by its library key: the starters, then the library's later
         * ones. Dumbbells are counted as a pair at the weight of one, as people log them, and a side
         * at a time as both sides done.
         */
        val starters: Map<String, ExerciseEnergy> = mapOf(
        "dumbbell-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Curls
        "barbell-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Barbell Curls
        "ez-bar-preacher-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Preacher Curls
        "incline-dumbbell-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Incline Bicep Curls
        "tricep-pushdowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Tricep Push Down
        "ez-bar-skull-crushers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Skull-Crushers
        "dumbbell-overhead-tricep-extensions" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Overhead Extensions
        "dumbbell-lateral-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03, loadFactor = 2.0), // Lateral Raises
        "dumbbell-shoulder-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Shoulder Press
        "face-pulls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.03), // Face Pulls
        "barbell-back-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Squats
        "leg-press" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.15, loadFactor = 0.7), // Leg Press
        "hack-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.7, loadFactor = 0.7), // Hack Squats
        "barbell-stiff-legged-deadlift" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55), // Stiff Legged Deadlift
        "lying-leg-curls" to ExerciseEnergy(perKilogram = LEG_MACHINE, bodyShare = 0.1), // Lying Leg Curls
        "standing-calf-raises" to ExerciseEnergy(perKilogram = CALF, bodyShare = 1.0), // Calf Raises
        "barbell-hip-thrusts" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.4), // Hip Thrusts
        "dumbbell-lunges" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85, loadFactor = 2.0), // Lunges
        "sit-ups" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.5), // Sit-ups
        "lying-leg-raises" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.3), // Leg Raises
        "barbell-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Bench Press
        "barbell-incline-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Incline Bench
        "pec-deck" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Pec Dec
        "dumbbell-flyes" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Chest Fly
        "dumbbell-shrugs" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.0, loadFactor = 2.0), // Shrugs
        "lat-pulldowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Lat Pull-down
        "pull-ups" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95), // Pull-ups
        "lat-prayers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.1), // Lat Prayers
        "barbell-bent-over-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.1), // Bent-over Rows
        "chest-supported-dumbbell-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Chest-Supported Rows
        "leg-extensions" to ExerciseEnergy(perKilogram = LEG_MACHINE, bodyShare = 0.05), // Leg Extensions
        "behind-the-back-cable-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.01, sides = 2.0), // Behind-the-Back Bicep Curls
        "smith-machine-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Smith-Squats
        "cable-lateral-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.015, sides = 2.0), // Cable Lateral Raises
        "barbell-upright-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.04), // Upright Rows
        "dumbbell-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Dumbbell Bench Press
        "incline-dumbbell-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Incline Dumbbell Press
        "barbell-decline-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Decline Bench Press
        "cable-crossovers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Cable Crossovers
        "push-ups" to ExerciseEnergy(perKilogram = 0.014, bodyShare = 0.64), // Push-ups
        "machine-chest-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Machine Chest Press
        "chin-ups" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95), // Chin-ups
        "straight-arm-pulldowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Straight-Arm Pulldowns
        "seated-cable-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Seated Cable Rows
        "single-arm-dumbbell-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, sides = 2.0), // Single-Arm Dumbbell Rows
        "t-bar-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.1), // T-Bar Rows
        "barbell-deadlift" to ExerciseEnergy(perKilogram = 0.0124, bodyShare = 0.6), // Deadlift
        "barbell-overhead-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Overhead Press
        "arnold-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Arnold Press
        "dumbbell-front-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03, loadFactor = 2.0), // Front Raises
        "dumbbell-rear-delt-flyes" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03, loadFactor = 2.0), // Rear Delt Flyes
        "hammer-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Hammer Curls
        "cable-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Cable Curls
        "concentration-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.01, sides = 2.0), // Concentration Curls
        "dips" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95), // Dips
        "close-grip-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Close-Grip Bench Press
        "dumbbell-tricep-kickbacks" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.01, sides = 2.0), // Tricep Kickbacks
        "barbell-front-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Front Squats
        "dumbbell-bulgarian-split-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.8, loadFactor = 2.0, sides = 2.0), // Bulgarian Split Squats
        "goblet-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Goblet Squats
        "wall-sit" to ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, holdMETs = 3.5), // Wall Sit
        "barbell-romanian-deadlift" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55), // Romanian Deadlift
        "seated-leg-curls" to ExerciseEnergy(perKilogram = LEG_MACHINE, bodyShare = 0.05), // Seated Leg Curls
        "good-mornings" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55), // Good Mornings
        "glute-bridges" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.4), // Glute Bridges
        "cable-glute-kickbacks" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.15, sides = 2.0), // Cable Kickbacks
        "machine-hip-abductions" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.0), // Hip Abductions
        "seated-calf-raises" to ExerciseEnergy(perKilogram = CALF, bodyShare = 0.0), // Seated Calf Raises
        "plank" to ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, holdMETs = 3.5), // Plank
        "side-plank" to ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, sides = 2.0, holdMETs = 3.0), // Side Plank
        "crunches" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.35), // Crunches
        "cable-crunches" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.35), // Cable Crunches
        "russian-twists" to ExerciseEnergy(perKilogram = 0.004, bodyShare = 0.3), // Russian Twists
        "hanging-leg-raises" to ExerciseEnergy(perKilogram = 0.012, bodyShare = 0.35), // Hanging Leg Raises
        "ab-wheel-rollouts" to ExerciseEnergy(perKilogram = 0.011, bodyShare = 0.55), // Ab Wheel Rollouts
        // The library's later exercises.
        "decline-dumbbell-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, loadFactor = 2.0), // Decline Dumbbell Press
        "smith-machine-bench-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Smith Machine Bench Press
        "smith-machine-incline-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Smith Machine Incline Press
        "incline-machine-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Incline Machine Press
        "incline-dumbbell-flyes" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Incline Dumbbell Flyes
        "low-to-high-cable-flyes" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Low-to-High Cable Flyes
        // Hands or feet raised a bench's height: about 48% or 72% of the body's weight on the
        // hands, from Ebben et al. (2011), who measured 64% in a push-up on the floor.
        "incline-push-ups" to ExerciseEnergy(perKilogram = 0.014, bodyShare = 0.48), // Incline Push-ups
        "decline-push-ups" to ExerciseEnergy(perKilogram = 0.014, bodyShare = 0.72), // Decline Push-ups
        "chest-dips" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95), // Chest Dips
        "seated-barbell-shoulder-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Seated Barbell Shoulder Press
        "machine-shoulder-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Machine Shoulder Press
        "smith-machine-shoulder-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Smith Machine Shoulder Press
        // The legs' dip and drive lift the body a little each rep too.
        "barbell-push-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.25), // Push Press
        // The hand lifts the plates on the bar's end as far as itself; one arm at a time.
        "landmine-press" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.03, sides = 2.0), // Landmine Press
        // About 60% of the body's weight on the hands: where the body's centre of mass sits
        // between hands and feet in the figure, scaled to Ebben et al.'s 64% for a push-up.
        "pike-push-ups" to ExerciseEnergy(perKilogram = 0.014, bodyShare = 0.6), // Pike Push-ups
        "machine-lateral-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03), // Machine Lateral Raises
        "cable-front-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03), // Cable Front Raises
        "plate-front-raises" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03), // Plate Front Raises
        "reverse-pec-deck" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03), // Reverse Pec Deck
        "cable-rear-delt-flyes" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03, loadFactor = 2.0), // Cable Rear Delt Flyes
        "band-pull-aparts" to ExerciseEnergy(perKilogram = RAISE, bodyShare = 0.03), // Band Pull-Aparts
        "dumbbell-upright-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.04, loadFactor = 2.0), // Dumbbell Upright Rows
        "cable-upright-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.04), // Cable Upright Rows
        "ez-bar-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // EZ-Bar Curls
        "dumbbell-preacher-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Dumbbell Preacher Curls
        "machine-preacher-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Machine Preacher Curls
        "dumbbell-spider-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Spider Curls
        "dumbbell-zottman-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Zottman Curls
        "cable-hammer-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Cable Hammer Curls
        "barbell-reverse-curls" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Reverse Curls
        // The weight moves only as far as the hand turns, about a fifth as far as in a curl.
        "dumbbell-wrist-curls" to ExerciseEnergy(perKilogram = ARMS * 0.2, bodyShare = 0.0, loadFactor = 2.0), // Wrist Curls
        "dumbbell-reverse-wrist-curls" to ExerciseEnergy(perKilogram = ARMS * 0.2, bodyShare = 0.0, loadFactor = 2.0), // Reverse Wrist Curls
        "straight-bar-pushdowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Straight-Bar Pushdowns
        "cable-overhead-tricep-extensions" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Cable Overhead Extensions
        "ez-bar-overhead-tricep-extensions" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // EZ-Bar Overhead Extensions
        "dumbbell-skull-crushers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02, loadFactor = 2.0), // Dumbbell Skull-Crushers
        "cable-tricep-kickbacks" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.01, sides = 2.0), // Cable Tricep Kickbacks
        // About 60% of the body's weight on the hands: where its centre of mass sits between the
        // hands and the heels in the figure, scaled as for pike push-ups.
        "bench-dips" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.6), // Bench Dips
        // The machine's help comes off the body's weight.
        "assisted-dips" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95, loadFactor = -1.0), // Assisted Dips
        // Hands together under the chest carry a little more of the body: about 68%, scaled as for
        // pike push-ups.
        "diamond-push-ups" to ExerciseEnergy(perKilogram = 0.014, bodyShare = 0.68), // Diamond Push-ups
        "close-grip-lat-pulldowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Close-Grip Lat Pull-down
        "single-arm-lat-pulldowns" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.025, sides = 2.0), // Single-Arm Lat Pull-down
        "assisted-pull-ups" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95, loadFactor = -1.0), // Assisted Pull-ups
        "neutral-grip-pull-ups" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.95), // Neutral-Grip Pull-ups
        "dumbbell-pullovers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02), // Dumbbell Pullovers
        // From the knees up, the bar travels about 60% as far as in a deadlift.
        "barbell-rack-pulls" to ExerciseEnergy(perKilogram = HINGE * 0.6, bodyShare = 0.55), // Rack Pulls
        "dumbbell-bent-over-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.1, loadFactor = 2.0), // Dumbbell Bent-over Rows
        "barbell-pendlay-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.1), // Pendlay Rows
        "barbell-seal-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Seal Rows
        "machine-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05), // Machine Rows
        "single-arm-cable-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.025, sides = 2.0), // Single-Arm Cable Rows
        // About 80% of the body's weight hangs from the hands: where its centre of mass sits
        // between the bar and the heels in the figure, scaled as for pike push-ups.
        "inverted-rows" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.8), // Inverted Rows
        "barbell-shrugs" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.0), // Barbell Shrugs
        // Walking at a moderate pace, about 3.5 METs, with the weights counted like body weight,
        // as in Pandolf et al.'s (1977) equation for walking with a load.
        "dumbbell-farmers-carry" to ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, holdMETs = 3.5, carryFactor = 2.0), // Farmer's Carry
        "trap-bar-deadlift" to ExerciseEnergy(perKilogram = 0.0124, bodyShare = 0.6), // Trap Bar Deadlift
        "barbell-lunges" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Barbell Lunges
        "dumbbell-walking-lunges" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85, loadFactor = 2.0), // Walking Lunges
        "dumbbell-reverse-lunges" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85, loadFactor = 2.0), // Reverse Lunges
        "dumbbell-split-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.8, loadFactor = 2.0, sides = 2.0), // Split Squats
        // Each step lifts the whole body the bench's height, about as far as a squat moves it.
        "dumbbell-step-ups" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.95, loadFactor = 2.0, sides = 2.0), // Step-ups
        "dumbbell-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85, loadFactor = 2.0), // Dumbbell Squats
        "bodyweight-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Bodyweight Squats
        "barbell-box-squats" to ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.85), // Box Squats
        "dumbbell-romanian-deadlift" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55, loadFactor = 2.0), // Dumbbell Romanian Deadlift
        // The free leg sinks as the body rises, taking back some of the lift.
        "dumbbell-single-leg-romanian-deadlift" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.4, sides = 2.0), // Single-Leg Romanian Deadlift
        "standing-leg-curls" to ExerciseEnergy(perKilogram = LEG_MACHINE, bodyShare = 0.05, sides = 2.0), // Standing Leg Curls
        // The body above the knees, most of its weight, falls and rises about two thirds as far as
        // a squat moves it.
        "nordic-hamstring-curls" to ExerciseEnergy(perKilogram = SQUAT * 0.7, bodyShare = 0.85), // Nordic Curls
        // The bar travels about a quarter less than in a deadlift: Escamilla et al. (2000) measured
        // 25 to 40% more distance and work in the conventional one.
        "barbell-sumo-deadlift" to ExerciseEnergy(perKilogram = 0.0124 * 0.75, bodyShare = 0.6), // Sumo Deadlift
        "machine-hip-thrusts" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.4), // Machine Hip Thrusts
        "single-leg-hip-thrusts" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.4, sides = 2.0), // Single-Leg Hip Thrusts
        "barbell-glute-bridges" to ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.4), // Barbell Glute Bridges
        "cable-pull-throughs" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55), // Cable Pull-throughs
        "kettlebell-swings" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.55), // Kettlebell Swings
        "back-extensions" to ExerciseEnergy(perKilogram = HINGE, bodyShare = 0.5), // Back Extensions
        "machine-hip-adductions" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.0), // Hip Adductions
        "cable-hip-abductions" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.05, sides = 2.0), // Cable Hip Abductions
        // Sitting, only the sled moves, at the leg press's angle.
        "leg-press-calf-raises" to ExerciseEnergy(perKilogram = CALF, bodyShare = 0.0, loadFactor = 0.7), // Leg Press Calf Raises
        "single-leg-calf-raises" to ExerciseEnergy(perKilogram = CALF, bodyShare = 1.0, sides = 2.0), // Single-Leg Calf Raises
        "smith-machine-calf-raises" to ExerciseEnergy(perKilogram = CALF, bodyShare = 1.0), // Smith Machine Calf Raises
        // The shins hang, so the legs rise about 80% as far as in a hanging leg raise.
        "hanging-knee-raises" to ExerciseEnergy(perKilogram = 0.012 * 0.8, bodyShare = 0.35), // Hanging Knee Raises
        "captains-chair-knee-raises" to ExerciseEnergy(perKilogram = 0.012 * 0.8, bodyShare = 0.35), // Captain's Chair Knee Raises
        // The legs rise all the way to the bar, about twice as far as in a hanging leg raise.
        "toes-to-bar" to ExerciseEnergy(perKilogram = 0.012 * 2, bodyShare = 0.35), // Toes-to-Bar
        // From 30 degrees below level, the upper body rises about 1.6 times as far as in a sit-up.
        "decline-sit-ups" to ExerciseEnergy(perKilogram = CORE * 1.6, bodyShare = 0.5), // Decline Sit-ups
        // A sit-up and a leg raise at once, each rising a little less far.
        "v-ups" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.7), // V-ups
        "bicycle-crunches" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.35), // Bicycle Crunches
        // The hips and legs curl up instead of the shoulders.
        "reverse-crunches" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.4), // Reverse Crunches
        "machine-crunches" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.35), // Machine Crunches
        // An arm and the other leg move each rep: about a fifth of the body.
        "dead-bugs" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.2), // Dead Bugs
        "bird-dogs" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.2), // Bird Dogs
        "hollow-body-hold" to ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, holdMETs = 3.5), // Hollow Body Hold
        // Each rep drives one leg, about a sixth of the body, in and back.
        "mountain-climbers" to ExerciseEnergy(perKilogram = CORE, bodyShare = 0.16), // Mountain Climbers
        // The hands press across the cable's pull, so the stack moves only about a quarter as far
        // as in a press.
        "cable-pallof-press" to ExerciseEnergy(perKilogram = ARMS * 0.25, bodyShare = 0.02, sides = 2.0), // Pallof Press
        "cable-woodchoppers" to ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05, sides = 2.0), // Cable Woodchoppers
        // Bending sideways costs about as much as a crunch.
        "dumbbell-side-bends" to ExerciseEnergy(perKilogram = 0.006, bodyShare = 0.3, sides = 2.0), // Side Bends
        )

        /**
         * One of Kitae's exercises' own, or for people's own exercises, one for their muscle group,
         * or a hold for isometric ones.
         */
        fun of(key: String?, target: String, isIsometric: Boolean): ExerciseEnergy {
            if (key != null) starters[key]?.let { return it }
            if (isIsometric) return ExerciseEnergy(perKilogram = 0.0, bodyShare = 0.0, holdMETs = 3.5)
            return when (MuscleGroup.fromStored(target)) {
                MuscleGroup.Biceps, MuscleGroup.Triceps -> ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.02)
                MuscleGroup.Shoulders -> ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.04)
                MuscleGroup.Chest, MuscleGroup.Lats, MuscleGroup.UpperBack -> ExerciseEnergy(perKilogram = ARMS, bodyShare = 0.05)
                MuscleGroup.Traps -> ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.0)
                MuscleGroup.Quads -> ExerciseEnergy(perKilogram = SQUAT, bodyShare = 0.4)
                MuscleGroup.Glutes -> ExerciseEnergy(perKilogram = 0.007, bodyShare = 0.3)
                MuscleGroup.Hamstrings -> ExerciseEnergy(perKilogram = 0.009, bodyShare = 0.2)
                MuscleGroup.Calves -> ExerciseEnergy(perKilogram = CALF, bodyShare = 0.5)
                MuscleGroup.Core -> ExerciseEnergy(perKilogram = CORE, bodyShare = 0.35)
                null -> ExerciseEnergy(perKilogram = 0.01, bodyShare = 0.2)
            }
        }
    }
}

/**
 * How much more a set of this type costs than its logged reps and weight show.
 *
 * Sets taken to failure cost more for the same work, as extra anaerobic and recovery energy (Scott
 * & Earnest 2011): single bench press sets to failure measured about 43 and 60 kJ (Scott et al.
 * 2011) where the same lab's sets stopped short of failure come to about 26 and 45 kJ for the same
 * work (Scott et al. 2014), a third to two thirds more. Kitae counts 30%, at the low end, as sets
 * logged as standard are often close to failure too. Drop and rest-pause sets go to failure as
 * well, and their drops and mini-sets are logged as sets of their own, so they count the same: with
 * the same weight lifted, drop sets cost no more than straight sets (Enes et al. 2024). A warm-up
 * counts for its own, lighter weight.
 */
val SetType.energyFactor: Double
    get() = when (this) {
        SetType.Warmup, SetType.Standard -> 1.0
        SetType.Failure, SetType.Drop, SetType.RestPause -> 1.3
    }
