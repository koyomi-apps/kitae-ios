// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Days and times as Kitae stores them, as the iPhone's `EpochDayTests` check them. */
class EpochDayTest {
    private val toronto = TimeZone.of("America/Toronto")

    @Test
    fun countsDaysFrom1970() {
        assertEquals(0, EpochDay.of(Instant.fromEpochSeconds(0), TimeZone.UTC))
        assertEquals(-1, EpochDay.of(Instant.fromEpochSeconds(-1), TimeZone.UTC))
    }

    @Test
    fun usesThePhonesTimeZone() {
        // 03:30 on 10 October in UTC is still 9 October in Toronto.
        val lateEvening = Instant.fromEpochSeconds(1_791_603_000)
        assertEquals(20_736, EpochDay.of(lateEvening, TimeZone.UTC))
        assertEquals(20_735, EpochDay.of(lateEvening, toronto))
    }

    @Test
    fun turnsBackIntoLocalMidnight() {
        val midnight = EpochDay.start(20_735, toronto)
        assertEquals(20_735, EpochDay.of(midnight, toronto))
        assertEquals(20_734, EpochDay.of(Instant.fromEpochMilliseconds(midnight.toEpochMilliseconds() - 1000), toronto))
    }

    @Test
    fun millisecondsRoundTrip() {
        val instant = Instant.fromEpochMilliseconds(1_791_603_000_250)
        assertEquals(1_791_603_000_250, instant.toEpochMilliseconds())
    }
}
