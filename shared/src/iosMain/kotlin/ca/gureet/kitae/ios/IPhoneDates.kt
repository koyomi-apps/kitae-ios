// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ios

import ca.gureet.kitae.database.EpochDay
import kotlin.time.Instant
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone

/**
 * The shared module's days ([EpochDay]) for the iPhone, whose time zones come by name, like
 * "America/Toronto", and whose moments come in milliseconds since 1970.
 */
object IPhoneDates {
    /** The day the moment [epochMilliseconds] falls on in the time zone named [zone]. */
    fun dayOf(epochMilliseconds: Long, zone: String): Long =
        EpochDay.of(Instant.fromEpochMilliseconds(epochMilliseconds), timeZone(zone))

    /** Midnight at the start of [day] in the time zone named [zone], in milliseconds since 1970. */
    fun startOf(day: Long, zone: String): Long = EpochDay.start(day, timeZone(zone)).toEpochMilliseconds()

    /** The zone named [zone], or the phone's own when this version can't read that name. */
    private fun timeZone(zone: String): TimeZone =
        try {
            TimeZone.of(zone)
        } catch (_: IllegalTimeZoneException) {
            TimeZone.currentSystemDefault()
        }
}
