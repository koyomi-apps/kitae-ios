// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import kotlin.math.abs
import kotlin.math.floor

/** Kilograms or pounds. Weights are always stored in kilograms. */
enum class WeightUnit(
    /** Kitae for Android's name for it, as its settings keep it. */
    val stored: String,
) {
    Kilograms("Kilograms"),
    Pounds("Pounds"),
    ;

    val symbol: String
        get() = when (this) {
            Kilograms -> "kg"
            Pounds -> "lb"
        }

    fun fromKilograms(kg: Double): Double = when (this) {
        Kilograms -> kg
        Pounds -> kg / KILOGRAMS_PER_POUND
    }

    fun toKilograms(value: Double): Double = when (this) {
        Kilograms -> value
        Pounds -> value * KILOGRAMS_PER_POUND
    }

    /** The −/+ buttons above the keyboard: the same numbers in both units, like Kitae for Android. */
    val steps: List<Double>
        get() = listOf(-5.0, -2.5, 2.5, 5.0)

    /** What a new set weighs when there's nothing to copy: 20 kg, or 45 lb. */
    val defaultWeightKg: Double
        get() = when (this) {
            Kilograms -> 20.0
            Pounds -> toKilograms(45.0)
        }

    companion object {
        const val KILOGRAMS_PER_POUND = 0.45359237

        /** A unit as it's stored, or null for one this version doesn't know. */
        fun fromStored(stored: String): WeightUnit? = entries.firstOrNull { it.stored == stored }
    }
}

/** How weights are written: 22.5 or 22,5. */
enum class DecimalStyle(val stored: String) {
    /** Follows the phone's region. */
    Automatic("automatic"),
    Point("point"),
    Comma("comma"),
    ;

    /** The decimal separator, the phone's ([phoneSeparator], which the app hands in) for [Automatic]. */
    fun separator(phoneSeparator: Char): Char = when (this) {
        Automatic -> phoneSeparator
        Point -> '.'
        Comma -> ','
    }

    companion object {
        fun fromStored(stored: String): DecimalStyle? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * Writes and reads weights as the iPhone's `WeightFormatter` does. [phoneSeparator] is the
 * decimal separator of the phone's region, which [DecimalStyle.Automatic] follows.
 */
class WeightFormatter(
    val unit: WeightUnit = WeightUnit.Kilograms,
    val decimals: DecimalStyle = DecimalStyle.Automatic,
    val phoneSeparator: Char = '.',
) {
    private val separator: Char
        get() = decimals.separator(phoneSeparator)

    /** Like Kitae for Android: at least one decimal and at most two, so 20 shows as 20.0 and 18.75 stays 18.75. */
    fun string(kilograms: Double): String = number(unit.fromKilograms(kilograms))

    /** A number in the same style, for the keyboard's −/+ steps. */
    fun number(value: Double): String {
        val hundredths = rounded(maxOf(value, 0.0) * 100)
        val whole = hundredths / 100
        val fraction = hundredths % 100
        if (fraction % 10 == 0L) return "$whole$separator${fraction / 10}"
        return "$whole$separator${if (fraction < 10) "0" else ""}$fraction"
    }

    /** A body weight to a tenth, like "78.4 kg". */
    fun body(kilograms: Double): String = "${bodyNumber(kilograms)} ${unit.symbol}"

    /** A body weight's number to a tenth, like 78.4, for typing it again. */
    fun bodyNumber(kilograms: Double): String = tenths(unit.fromKilograms(kilograms))

    /** A percentage to a tenth, like "18.5%", or "18%" when it's whole. */
    fun percent(value: Double): String = "${tenthsOrWhole(value)}%"

    /** A number to a tenth, like 18.5, or 18 when it's whole, for typing it again. */
    fun tenthsOrWhole(value: Double): String {
        val tenths = rounded(maxOf(value, 0.0) * 10)
        if (tenths % 10 == 0L) return "${tenths / 10}"
        return "${tenths / 10}$separator${tenths % 10}"
    }

    /** A −/+ step above the keyboard, like −2.5 or +5, with the decimal separator from Settings. */
    fun step(value: Double): String {
        val sign = if (value < 0) "−" else "+"
        val tenths = rounded(abs(value) * 10)
        if (tenths % 10 == 0L) return "$sign${tenths / 10}"
        return "$sign${tenths / 10}$separator${tenths % 10}"
    }

    /**
     * Reads a typed weight in [unit]. A point and a comma both work as the decimal separator,
     * whatever the setting, as keyboards show the region's key.
     */
    fun kilograms(text: String): Double? = number(text)?.let(unit::toKilograms)

    private fun tenths(value: Double): String {
        val tenths = rounded(maxOf(value, 0.0) * 10)
        return "${tenths / 10}$separator${tenths % 10}"
    }

    companion object {
        private val plainNumber = Regex("""^([0-9]+\.?[0-9]*|\.[0-9]+)$""")

        /** Reads a typed number, with a point or a comma. */
        fun number(text: String): Double? {
            val cleaned = text.trim().replace(',', '.')
            if (!plainNumber.matches(cleaned)) return null
            return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        }

        /** Rounds a number that isn't negative to the nearest whole one, halves up, as the iPhone does. */
        private fun rounded(value: Double): Long {
            val whole = floor(value)
            return (if (value - whole >= 0.5) whole + 1 else whole).toLong()
        }
    }
}
