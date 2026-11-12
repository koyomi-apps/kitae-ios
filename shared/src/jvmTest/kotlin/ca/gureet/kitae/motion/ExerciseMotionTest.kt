// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.bundledSeed
import ca.gureet.kitae.muscles.ExerciseMuscles
import ca.gureet.kitae.muscles.Muscle
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

/**
 * The exercise animations: that they load, match the starter exercises and hold together, as the
 * iPhone's `ExerciseMotionTests` check them, without drawing.
 */
class ExerciseMotionTest {
    private val motions = ExerciseMotions.decode(File(System.getProperty("kitae.motions")).readText())

    private fun motion(key: String) = assertNotNull(motions.byKey[key], key)

    @Test
    fun everyStarterExerciseHasOneByItsName() {
        val names = bundledSeed.exercises.associate { it.key to it.name }
        val keys = motions.all.map { it.key }
        assertTrue(motions.all.isNotEmpty())
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(names.keys, keys.toSet())
        for (motion in motions.all) assertEquals(names[motion.key], motion.name, motion.key)
        val bench = Exercise(id = 21, name = "Bench Press", target = "Chest", libraryKey = "barbell-bench-press")
        assertEquals("barbell-bench-press", motions.motion(bench)?.key)
        assertNull(motions.motion(bench.copy(libraryKey = null)))
    }

    @Test
    fun keyframesCanBeBlended() {
        for (motion in motions.all) {
            val name = motion.name
            when (val keyframes = motion.keyframes) {
                is ExerciseMotion.Keyframes.Side -> {
                    val (from, to) = keyframes
                    assertEquals(from.arm.kind, to.arm.kind, "$name: arm")
                    assertEquals(from.farArm?.kind, to.farArm?.kind, "$name: far arm")
                    assertEquals(from.thigh == null, to.thigh == null, "$name: legs")
                    assertEquals(from.shin == null, to.shin == null, "$name: shins")
                    assertEquals(from.kneeAt == null, to.kneeAt == null, "$name: knee")
                }
                is ExerciseMotion.Keyframes.Front -> {
                    val (from, to) = keyframes
                    assertEquals(from.arm.kind, to.arm.kind, "$name: arm")
                    assertEquals(from.otherArm?.kind, to.otherArm?.kind, "$name: other arm")
                    assertEquals(from.ankle == null, to.ankle == null, "$name: ankles")
                    assertEquals(from.otherThigh == null, to.otherThigh == null, "$name: other leg")
                }
            }
            assertEquals(4, motion.timing.size, "$name: timing")
            assertTrue(motion.timing == motion.timing.sorted() && motion.timing.all { it in 0.0..1.0 }, "$name: timing")
            assertTrue(motion.seconds > 0 && motion.zoom in 0.5..1.5, "$name: loop")
        }
    }

    @Test
    fun limbsKeepTheirLengthsAndStayAboveTheFloor() {
        for (motion in motions.all) {
            val name = motion.name
            for (step in 0..20) {
                val k = motion.progress(step / 20.0)
                when (val keyframes = motion.keyframes) {
                    is ExerciseMotion.Keyframes.Side -> {
                        val figure = Figure(FigurePose.lerp(keyframes.from, keyframes.to, k))
                        for (leg in listOf(figure.nearLeg, figure.farLeg)) {
                            assertTrue(abs((leg.knee - leg.hip).length - FigureBody.THIGH) < 0.01, "$name: thigh")
                            assertTrue(abs((leg.ankle - leg.knee).length - FigureBody.SHIN) < 0.01, "$name: shin")
                            assertTrue(leg.knee.y <= 0 && leg.ankle.y <= 0, "$name: leg below the floor")
                        }
                        for (arm in listOf(figure.nearArm, figure.farArm)) {
                            val forearm = (arm.wrist - arm.elbow).length
                            assertTrue(abs(forearm - FigureBody.FOREARM * arm.scale) < 0.01, "$name: forearm")
                            assertTrue(arm.wrist.y <= 0 && arm.elbow.y <= 0, "$name: arm below the floor")
                        }
                        assertTrue(figure.head.y < 0 && figure.pose.hip.y < 0, "$name: body below the floor")
                    }
                    is ExerciseMotion.Keyframes.Front -> {
                        val pose = FrontPose.lerp(keyframes.from, keyframes.to, k)
                        val figure = FrontFigure(pose)
                        for (leg in figure.legs) {
                            val thigh = (leg.knee - leg.hip).length
                            assertTrue(abs(thigh - FigureBody.THIGH * pose.thighLength) < 0.01, "$name: thigh")
                            assertTrue(leg.knee.y <= 0 && leg.ankle.y <= 0, "$name: leg below the floor")
                        }
                        for (arm in figure.arms) {
                            val forearm = (arm.wrist - arm.elbow).length
                            assertTrue(abs(forearm - FigureBody.FOREARM * arm.foreLength) < 0.01, "$name: forearm")
                            assertTrue(arm.wrist.y <= 0 && arm.elbow.y <= 0, "$name: arm below the floor")
                        }
                        assertTrue(figure.head.y < 0, "$name: head below the floor")
                    }
                }
            }
        }
    }

    @Test
    fun blendingHalfwayMixesBothKeyframes() {
        val a = FigurePose(hip = Vec(0.0, -100.0), lean = 0.0, arm = ArmPose.Swing(upper = 0.0, bend = 0.0, upperLength = 1.0))
        val b = FigurePose(
            hip = Vec(-30.0, -60.0),
            lean = 40.0,
            curl = 10.0,
            arm = ArmPose.Swing(upper = 20.0, bend = 120.0, upperLength = 0.5),
        )
        val mid = FigurePose.lerp(a, b, 0.5)
        assertEquals(Vec(-15.0, -80.0), mid.hip)
        assertTrue(mid.lean == 20.0 && mid.curl == 5.0)
        assertEquals(ArmPose.Swing(upper = 10.0, bend = 60.0, upperLength = 0.75), mid.arm)
    }

    @Test
    fun aWristBendsTheHandAndWhatItHolds() {
        val file = ExerciseMotions.decode(
            """
            [{"key": "wrist", "name": "Wrist", "from": {"hip": [-8, -58], "lean": 30, "arm": {"hand": [40, -70], "elbow": 1, "flex": 40}},
              "to": {"hip": [-8, -58], "lean": 30, "arm": {"upper": 10}}}]
            """,
        )
        val motion = file.all.single()
        val bent = (motion.keyframes as ExerciseMotion.Keyframes.Side).from.arm
        assertEquals(ArmPose.Reach(Vec(40.0, -70.0), onTorso = false, scale = 1.0, upperLength = 1.0, elbow = 1.0, flex = 40.0), bent)
        val straight = ArmPose.Reach(Vec(40.0, -70.0), onTorso = false, scale = 1.0, upperLength = 1.0, elbow = 1.0)
        assertEquals(straight.copy(flex = 20.0), ArmPose.lerp(straight, bent, 0.5))
        // The wrist stays put; only the hand turns.
        val pose = FigurePose(hip = Vec(-8.0, -58.0), lean = 30.0, arm = bent)
        assertEquals(Figure(pose.copy(arm = straight)).nearArm.wrist, Figure(pose).nearArm.wrist)
        assertEquals(40.0, Figure(pose).nearArm.flex)
        // What the file leaves out takes the iPhone's defaults.
        assertEquals(ArmPose.Swing(upper = 10.0, bend = 0.0, upperLength = 1.0), (motion.keyframes as ExerciseMotion.Keyframes.Side).to.arm)
        assertEquals(ExerciseMotion("wrist", "Wrist", motion.keyframes), motion)
    }

    @Test
    fun aBrokenFileIsntAnimations() {
        val arm = """{"upper": 0}"""
        for (text in listOf(
            """{"key": "k"}""",
            """[{"key": "k", "name": "No hip", "from": {"lean": 0, "arm": $arm}, "to": {"hip": [0, -90], "lean": 0, "arm": $arm}}]""",
            """[{"key": "k", "name": "Bad point", "from": {"hip": [0], "lean": 0, "arm": $arm}, "to": {"hip": [0, -90], "lean": 0, "arm": $arm}}]""",
            """[{"key": "k", "name": "Bad gear", "gear": [{"kind": "rocket"}], "from": {"hip": [0, -90], "lean": 0, "arm": $arm},
                "to": {"hip": [0, -90], "lean": 0, "arm": $arm}}]""",
            "[{",
        )) {
            assertFailsWith<SerializationException>(text) { ExerciseMotions.decode(text) }
        }
    }

    @Test
    fun aKneelingPoseStaysOnItsKnee() {
        val knee = Vec(-6.0, -6.6)
        for (thigh in listOf(160.0, 200.0, 245.0)) {
            val pose = FigurePose(
                hip = Vec.ZERO,
                lean = 60.0,
                thigh = thigh,
                shin = 270.0,
                kneeAt = knee,
                arm = ArmPose.Swing(upper = 0.0, bend = 0.0, upperLength = 1.0),
            )
            assertTrue((Figure(pose).nearLeg.knee - knee).length < 0.01)
        }
    }

    @Test
    fun theFrontFigureMirrorsItsArms() {
        val figure = FrontFigure(
            FrontPose(hip = Vec(0.0, -100.0), arm = FrontArmPose.Swing(upper = 80.0, bend = 10.0, upperLength = 1.0, foreLength = 1.0)),
        )
        val (other, arm) = figure.arms
        assertTrue(abs(other.wrist.x + arm.wrist.x) < 0.01 && abs(other.wrist.y - arm.wrist.y) < 0.01)
        assertTrue(arm.wrist.x > 60)
    }

    @Test
    fun theFrontFigureCanLiftOneLegOrPlantBoth() {
        val arm = FrontArmPose.Swing(upper = 6.0, bend = 10.0, upperLength = 1.0, foreLength = 1.0)
        // One leg out to the side, the other standing as a mirrored leg would.
        val lifted = FrontFigure(
            FrontPose(hip = Vec(0.0, -100.0), thigh = 30.0, shin = 30.0, arm = arm, otherThigh = 3.0, otherShin = 1.5),
        )
        val standing = FrontFigure(FrontPose(hip = Vec(0.0, -100.0), arm = arm))
        assertEquals(standing.legs[0].ankle, lifted.legs[0].ankle)
        assertTrue(lifted.legs[1].ankle.y < standing.legs[1].ankle.y - 5)
        // Legs reaching for their ankles keep the feet planted, the knees bending outward.
        val wide = FrontFigure(FrontPose(hip = Vec(0.0, -60.0), thighLength = 0.9, arm = arm, ankle = Vec(50.0, -7.4)))
        assertTrue((wide.legs[1].ankle - Vec(50.0, -7.4)).length < 0.01 && (wide.legs[0].ankle - Vec(-50.0, -7.4)).length < 0.01)
        assertTrue(wide.legs[1].knee.x > wide.legs[1].hip.x && wide.legs[0].knee.x < wide.legs[0].hip.x)
    }

    @Test
    fun aLoopHoldsThenMovesThenHolds() {
        val k = listOf(0.1, 0.5, 0.6, 0.9)
        assertEquals(0.0, FigureMath.phase(0.05, k))
        assertTrue(abs(FigureMath.phase(0.3, k) - 0.5) < 1e-9)
        assertEquals(1.0, FigureMath.phase(0.55, k))
        assertEquals(0.0, FigureMath.phase(0.95, k))
    }

    @Test
    fun theAnimationsShadeTheMapsMuscles() {
        val muscles = ExerciseMuscles.decode(File(System.getProperty("kitae.muscles")).readText())
        // Bench press: the chest most, with the front delts and triceps helping.
        val bench = motion("barbell-bench-press").muscles(muscles)
        assertEquals(setOf(Muscle.Chest), bench.primary)
        assertEquals(setOf(Muscle.FrontDelts, Muscle.Triceps), bench.secondary)
        assertEquals(FigureMuscles.Shade.Primary, bench[Muscle.Chest])
        assertEquals(FigureMuscles.Shade.Secondary, bench[Muscle.Triceps])
        assertNull(bench[Muscle.Quads])
        // The traps show the upper back, and the glutes the abductors.
        val rows = motion("barbell-bent-over-rows").muscles(muscles)
        assertEquals(FigureMuscles.Shade.Primary, rows[Muscle.Traps])
        assertEquals(FigureMuscles.Shade.Secondary, rows[Muscle.RearDelts])
        val abductions = motion("machine-hip-abductions").muscles(muscles)
        assertEquals(setOf(Muscle.Glutes), abductions.primary)
        assertTrue(abductions.secondary.isEmpty())
        // The adductors show from the front, inside the thighs.
        assertEquals(setOf(Muscle.Adductors), motion("machine-hip-adductions").muscles(muscles).primary)
        // Every animation has a muscle in olive, and shades only muscles it has shapes for.
        for (motion in motions.all) {
            val shaded = motion.muscles(muscles)
            assertTrue(shaded.primary.isNotEmpty(), motion.name)
            assertTrue(shaded.primary.intersect(shaded.secondary).isEmpty(), motion.name)
            assertTrue((shaded.primary + shaded.secondary).none { it == Muscle.UpperBack || it == Muscle.Abductors }, motion.name)
        }
    }
}
