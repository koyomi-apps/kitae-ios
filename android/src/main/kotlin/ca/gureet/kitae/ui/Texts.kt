// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.kitae.R
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.roundedKilocalories
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Workout days as Kamae shows them (the iPhone's `HistoryDates`), named in the phone's language.
 * Days are epoch days, as they're stored.
 */
object Dates {
    private fun format(day: Long, skeleton: String, locale: Locale): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))

    fun year(day: Long): Int = LocalDate.ofEpochDay(day).year

    /** "07". */
    fun dayOfMonth(day: Long): String = LocalDate.ofEpochDay(day).dayOfMonth.toString().padStart(2, '0')

    /** "WED". */
    fun shortWeekday(day: Long, locale: Locale = Locale.getDefault()): String = format(day, "EEE", locale).uppercase(locale)

    /** "Wednesday". */
    fun weekday(day: Long, locale: Locale = Locale.getDefault()): String = format(day, "EEEE", locale)

    /** "October", or "September 2025" in another year than [today]'s. */
    fun monthTitle(year: Int, month: Int, today: Long, locale: Locale = Locale.getDefault()): String {
        val first = LocalDate.of(year, month, 1).toEpochDay()
        return if (year == year(today)) format(first, "LLLL", locale) else format(first, "MMMMy", locale)
    }

    /** "Wed, Oct 7", with the year when it isn't [today]'s. */
    fun short(day: Long, today: Long, locale: Locale = Locale.getDefault()): String =
        format(day, if (year(day) == year(today)) "EEEMMMd" else "EEEMMMdy", locale)

    /** "Oct 7", for the ends of a chart. */
    fun dayAndMonth(day: Long, locale: Locale = Locale.getDefault()): String = format(day, "MMMd", locale)

    /** "Wednesday, October 7", with the year when it isn't [today]'s. */
    fun long(day: Long, today: Long, locale: Locale = Locale.getDefault()): String =
        format(day, if (year(day) == year(today)) "EEEEMMMMd" else "EEEEMMMMdy", locale)
}

/** A muscle's name: "Back" for the upper back, "Other" for one Kamae doesn't know. */
@Composable
@ReadOnlyComposable
fun muscleName(muscle: MuscleGroup?): String = stringResource(
    when (muscle) {
        MuscleGroup.Chest -> R.string.muscle_chest
        MuscleGroup.UpperBack -> R.string.muscle_upper_back
        MuscleGroup.Lats -> R.string.muscle_lats
        MuscleGroup.Shoulders -> R.string.muscle_shoulders
        MuscleGroup.Biceps -> R.string.muscle_biceps
        MuscleGroup.Triceps -> R.string.muscle_triceps
        MuscleGroup.Traps -> R.string.muscle_traps
        MuscleGroup.Core -> R.string.muscle_core
        MuscleGroup.Quads -> R.string.muscle_quads
        MuscleGroup.Hamstrings -> R.string.muscle_hamstrings
        MuscleGroup.Glutes -> R.string.muscle_glutes
        MuscleGroup.Calves -> R.string.muscle_calves
        null -> R.string.muscle_other
    },
)

/** "48 min", or "1 h 49 min". */
@Composable
@ReadOnlyComposable
fun durationLabel(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.duration_minutes, minutes) else stringResource(R.string.duration_hours, minutes / 60, minutes % 60)

/** The day's name, or the weekday for a workout whose day is gone. */
@Composable
@ReadOnlyComposable
fun workoutTitle(summary: WorkoutSummary): String = summary.dayName ?: Dates.weekday(summary.workout.date)

/**
 * "48 min · 15/15 sets · ≈ 230 kcal": the sets, after the workout's length when it's known, and
 * the energy it used once there's a weight to work it out from.
 */
@Composable
@ReadOnlyComposable
fun workoutDetails(summary: WorkoutSummary, kilocalories: Double? = null): String {
    val parts = listOfNotNull(
        summary.minutes?.let { durationLabel(it) },
        pluralStringResource(R.plurals.sets_fraction, summary.setCount, summary.doneCount, summary.setCount),
        kilocalories?.let { stringResource(R.string.kcal_about, roundedKilocalories(it)) },
    )
    return parts.joinToString(stringResource(R.string.details_separator))
}

/** A set as Kamae lists it: "10 × 22.5 kg", "12 reps" without weight, or "45s" for a hold. */
@Composable
@ReadOnlyComposable
fun setSummary(formatter: WeightFormatter, reps: Int, kilograms: Double, isIsometric: Boolean): String {
    val amount = if (isIsometric) "${reps}s" else "$reps"
    if (kilograms > 0) return "$amount × ${formatter.string(kilograms)} ${formatter.unit.symbol}"
    return if (isIsometric) amount else pluralStringResource(R.plurals.reps, reps, reps)
}

/** A set with its weight first, "22.5 kg × 10", as an exercise's heaviest set is shown. */
@Composable
@ReadOnlyComposable
fun weightFirst(formatter: WeightFormatter, reps: Int, kilograms: Double, isIsometric: Boolean): String {
    val amount = if (isIsometric) "${reps}s" else "$reps"
    if (kilograms > 0) return "${formatter.string(kilograms)} ${formatter.unit.symbol} × $amount"
    return if (isIsometric) amount else pluralStringResource(R.plurals.reps, reps, reps)
}
