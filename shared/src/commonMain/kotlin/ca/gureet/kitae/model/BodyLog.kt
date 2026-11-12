// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil

// What people tell Kitae about their body, as the iPhone has it (`BodyLog.swift`). The words
// for sexes and activity levels stay with each app.

/** A day's weigh-in: weight, body fat, or both. */
data class BodyEntry(
    val id: Long,
    /** The day, in days since 1970 where the phone was. */
    val date: Long,
    val weightKg: Double? = null,
    /** Percent, like 18.5. */
    val bodyFatPercent: Double? = null,
) {
    /** The weight that isn't fat, when both are known. */
    val leanKg: Double?
        get() = if (weightKg != null && bodyFatPercent != null) weightKg * (1 - bodyFatPercent / 100) else null

    val fatKg: Double?
        get() = if (weightKg != null && bodyFatPercent != null) weightKg * bodyFatPercent / 100 else null
}

/** [stored] is the name the profile keeps. */
enum class Sex(val stored: String) {
    Female("Female"),
    Male("Male"),
    ;

    companion object {
        fun fromStored(stored: String?): Sex? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * How active someone's day is outside their workouts, which Kitae counts itself. Kitae keeps it
 * with the profile, for apps that read its backups, like a calorie tracker. [stored] is the name
 * the profile keeps.
 */
enum class ActivityLevel(val stored: String) {
    Sedentary("Sedentary"),
    LightlyActive("LightlyActive"),
    ModeratelyActive("ModeratelyActive"),
    VeryActive("VeryActive"),
    ExtraActive("ExtraActive"),
    ;

    companion object {
        fun fromStored(stored: String?): ActivityLevel? = entries.firstOrNull { it.stored == stored }
    }
}

/** What people tell Kitae about themselves. Everything is optional. */
data class BodyProfile(
    val heightCm: Double? = null,
    /** In days since 1970. */
    val birthDate: Long? = null,
    val sex: Sex? = null,
    val activityLevel: ActivityLevel? = null,
) {
    /** Whole years old on [day]. */
    fun age(day: Long): Int? {
        val born = birthDate ?: return null
        if (day < born) return null
        return LocalDate.fromEpochDays(born).yearsUntil(LocalDate.fromEpochDays(day))
    }
}

/** The profile and every weigh-in. */
data class BodyLog(
    val profile: BodyProfile = BodyProfile(),
    /** Oldest first, one a day. */
    val entries: List<BodyEntry> = emptyList(),
) {
    val isEmpty: Boolean
        get() = entries.isEmpty() && profile == BodyProfile()

    /** The newest weigh-in with a weight. */
    val latestWeight: BodyEntry?
        get() = entries.lastOrNull { it.weightKg != null }

    /** The newest weigh-in with body fat. */
    val latestBodyFat: BodyEntry?
        get() = entries.lastOrNull { it.bodyFatPercent != null }

    /**
     * The weight that stands for [day]: the last one weighed on or before it, else the first one
     * after, so workouts from before the first weigh-in still have one.
     */
    fun weightKg(day: Long): Double? {
        val weighed = entries.filter { it.weightKg != null }
        return (weighed.lastOrNull { it.date <= day } ?: weighed.firstOrNull())?.weightKg
    }

    /** The body fat that stands for [day], found the same way. */
    fun bodyFatPercent(day: Long): Double? {
        val measured = entries.filter { it.bodyFatPercent != null }
        return (measured.lastOrNull { it.date <= day } ?: measured.firstOrNull())?.bodyFatPercent
    }
}

/** Heights in centimetres, or in feet and inches for people who weigh in pounds. */
data class HeightFormatter(val unit: WeightUnit = WeightUnit.Kilograms) {
    val usesFeet: Boolean
        get() = unit == WeightUnit.Pounds

    /** "180 cm", or "5 ft 11 in". */
    fun string(centimetres: Double): String {
        if (!usesFeet) return "${centimetres.roundToInt()} cm"
        val (feet, inches) = feetAndInches(centimetres)
        return "$feet ft $inches in"
    }

    companion object {
        /** The nearest whole inch, as feet and inches. */
        fun feetAndInches(centimetres: Double): Pair<Int, Int> {
            val total = (centimetres / 2.54).roundToInt()
            return total / 12 to total % 12
        }

        fun centimetres(feet: Int, inches: Double): Double = (feet * 12 + inches) * 2.54
    }
}

/** "≈ 230 kcal", to the nearest 5. */
fun kilocaloriesLabel(kcal: Double): String = "≈ ${roundedKilocalories(kcal)} kcal"

/** To the nearest 5, as the estimates are shown. */
fun roundedKilocalories(kcal: Double): Int = (kcal / 5).roundToInt() * 5
