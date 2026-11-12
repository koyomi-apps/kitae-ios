// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.motion

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads `ExerciseMotions.json` the way the iPhone decodes it: points are [x, y] arrays, arms are
 * swung when they have an `upper` angle and reach for a point otherwise, a motion with `view`
 * "front" has front keyframes, and anything left out takes its default. Keys it doesn't know are
 * left alone.
 */
internal object MotionFile {
    fun read(text: String): List<ExerciseMotion> {
        val list = Json.parseToJsonElement(text) as? JsonArray ?: throw SerializationException("The animations aren't a list")
        return list.mapIndexed { i, element -> Fields(element, "[$i]").motion() }
    }

    private fun Fields.motion(): ExerciseMotion {
        val keyframes = if (optionalString("view") == "front") {
            ExerciseMotion.Keyframes.Front(fields("from").frontPose(), fields("to").frontPose())
        } else {
            ExerciseMotion.Keyframes.Side(fields("from").pose(), fields("to").pose())
        }
        return ExerciseMotion(
            key = string("key"),
            name = string("name"),
            keyframes = keyframes,
            legsFront = optionalBoolean("legsFront") ?: false,
            gear = optionalList("gear")?.map { it.gear() } ?: emptyList(),
            timing = optionalDoubles("timing") ?: listOf(0.08, 0.5, 0.58, 0.96),
            seconds = optionalDouble("seconds") ?: 2.4,
            shift = optionalDouble("shift") ?: 0.0,
            zoom = optionalDouble("zoom") ?: 1.0,
            shadow = optionalVec("shadow") ?: Vec(4.0, 0.0),
            shadowWidth = optionalDouble("shadowWidth") ?: 40.0,
        )
    }

    private fun Fields.pose() = FigurePose(
        hip = vec("hip"),
        lean = double("lean"),
        curl = optionalDouble("curl") ?: 0.0,
        shrug = optionalDouble("shrug") ?: 0.0,
        head = optionalDouble("head"),
        ankle = optionalVec("ankle") ?: Vec(0.0, -FigureBody.ANKLE_HEIGHT),
        foot = optionalDouble("foot") ?: 0.0,
        thigh = optionalDouble("thigh"),
        shin = optionalDouble("shin"),
        farAnkle = optionalVec("farAnkle"),
        farFoot = optionalDouble("farFoot"),
        farThigh = optionalDouble("farThigh"),
        farShin = optionalDouble("farShin"),
        knee = optionalDouble("knee") ?: -1.0,
        kneeAt = optionalVec("kneeAt"),
        arm = fields("arm").arm(),
        farArm = optionalFields("farArm")?.arm(),
    )

    private fun Fields.arm(): ArmPose {
        val upperLength = optionalDouble("upperLength") ?: 1.0
        val flex = optionalDouble("flex") ?: 0.0
        if (has("upper")) return ArmPose.Swing(double("upper"), optionalDouble("bend") ?: 0.0, upperLength, flex)
        return ArmPose.Reach(
            hand = vec("hand"),
            onTorso = optionalString("on") == "torso",
            scale = optionalDouble("scale") ?: 1.0,
            upperLength = upperLength,
            elbow = optionalDouble("elbow") ?: -1.0,
            flex = flex,
        )
    }

    private fun Fields.frontPose() = FrontPose(
        hip = vec("hip"),
        turn = optionalDouble("turn") ?: 0.0,
        lean = optionalDouble("lean") ?: 0.0,
        torsoLength = optionalDouble("torsoLength") ?: 1.0,
        shrug = optionalDouble("shrug") ?: 0.0,
        head = optionalDouble("head") ?: 0.0,
        headHeight = optionalDouble("headHeight") ?: 1.0,
        thigh = optionalDouble("thigh") ?: 3.0,
        thighLength = optionalDouble("thighLength") ?: 1.0,
        shin = optionalDouble("shin") ?: 1.5,
        shinLength = optionalDouble("shinLength") ?: 1.0,
        arm = fields("arm").frontArm(),
        otherArm = optionalFields("otherArm")?.frontArm(),
        otherThigh = optionalDouble("otherThigh"),
        otherShin = optionalDouble("otherShin"),
        ankle = optionalVec("ankle"),
    )

    private fun Fields.frontArm(): FrontArmPose {
        val upperLength = optionalDouble("upperLength") ?: 1.0
        val foreLength = optionalDouble("foreLength") ?: 1.0
        if (has("upper")) return FrontArmPose.Swing(double("upper"), optionalDouble("bend") ?: 0.0, upperLength, foreLength)
        return FrontArmPose.Reach(
            hand = vec("hand"),
            onTorso = optionalString("on") == "torso",
            elbow = optionalDouble("elbow") ?: 1.0,
            upperLength = upperLength,
            foreLength = foreLength,
        )
    }

    private fun Fields.gear(): Gear {
        val kind = string("kind")
        return Gear(
            kind = Gear.Kind.fromStored(kind) ?: throw SerializationException("$path.kind: no gear is called $kind"),
            on = optionalString("on"),
            from = optionalVec("from"),
            to = optionalVec("to"),
            at = optionalVec("at"),
            anchor = optionalVec("anchor"),
            thick = optionalDouble("thick") ?: 8.0,
            legs = optionalDoubles("legs") ?: emptyList(),
            width = optionalDouble("width") ?: 4.0,
            length = optionalDouble("length") ?: 40.0,
            handle = optionalString("handle") ?: "bar",
            stack = optionalBoolean("stack") ?: false,
            profile = optionalBoolean("profile") ?: false,
            hang = optionalBoolean("hang") ?: false,
            one = optionalBoolean("one") ?: false,
            radius = optionalDouble("radius"),
            front = optionalBoolean("front") ?: false,
            top = optionalDouble("top") ?: 70.0,
            bottom = optionalDouble("bottom") ?: 0.0,
            out = optionalDouble("out"),
            side = optionalDouble("side") ?: 1.0,
        )
    }

    /** An object's values, read as the iPhone's decoders read them; [path] says where for errors. */
    private class Fields(element: JsonElement, val path: String) {
        private val values = element as? JsonObject ?: throw SerializationException("$path isn't an object")

        fun has(key: String) = key in values

        private fun value(key: String) = values[key]?.takeUnless { it is JsonNull }

        private fun wrong(key: String, what: String) = SerializationException("$path.$key isn't $what")

        private fun number(element: JsonElement, key: String) =
            (element as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: throw wrong(key, "a number")

        fun optionalDouble(key: String): Double? = value(key)?.let { number(it, key) }

        fun double(key: String): Double = optionalDouble(key) ?: throw wrong(key, "there")

        fun optionalString(key: String): String? = value(key)?.let {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw wrong(key, "text")
        }

        fun string(key: String): String = optionalString(key) ?: throw wrong(key, "there")

        fun optionalBoolean(key: String): Boolean? = value(key)?.let {
            (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.booleanOrNull ?: throw wrong(key, "true or false")
        }

        fun optionalDoubles(key: String): List<Double>? = value(key)?.let { list ->
            (list as? JsonArray ?: throw wrong(key, "a list")).map { number(it, key) }
        }

        /** A point, written [x, y]. */
        fun optionalVec(key: String): Vec? = value(key)?.let {
            val xy = it as? JsonArray
            if (xy == null || xy.size < 2) throw wrong(key, "a point")
            Vec(number(xy[0], key), number(xy[1], key))
        }

        fun vec(key: String): Vec = optionalVec(key) ?: throw wrong(key, "there")

        fun optionalFields(key: String): Fields? = value(key)?.let { Fields(it, "$path.$key") }

        fun fields(key: String): Fields = optionalFields(key) ?: throw wrong(key, "there")

        fun optionalList(key: String): List<Fields>? = value(key)?.let { list ->
            (list as? JsonArray ?: throw wrong(key, "a list")).mapIndexed { i, it -> Fields(it, "$path.$key[$i]") }
        }
    }
}
