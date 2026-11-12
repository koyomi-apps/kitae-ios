// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Dates as Kitae stores them, as on the iPhone (`EpochDay.swift`): a workout's day as a count of
 * days since 1 January 1970 where the phone is, and moments as milliseconds since 1970.
 */
object EpochDay {
    /** The day [instant] falls on in [timeZone], counted on the Gregorian calendar. */
    fun of(instant: Instant, timeZone: TimeZone): Long = instant.toLocalDateTime(timeZone).date.toEpochDays()

    /** Midnight at the start of [day] in [timeZone]. */
    fun start(day: Long, timeZone: TimeZone): Instant = LocalDate.fromEpochDays(day).atStartOfDayIn(timeZone)

    /** The date of [day], for its year, month and day of the month. */
    fun date(day: Long): LocalDate = LocalDate.fromEpochDays(day)

    /**
     * The weekday of [day], from 1 for Sunday to 7 for Saturday, as the iPhone's `Calendar`
     * numbers them. 1 January 1970 was a Thursday.
     */
    fun weekday(day: Long): Int = (((day % 7) + 7 + 4) % 7 + 1).toInt()
}
