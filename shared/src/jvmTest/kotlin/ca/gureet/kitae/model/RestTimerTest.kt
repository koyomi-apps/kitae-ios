// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rest timer, as the iPhone's `RestTimerTests` and `RestRoomTests` check it. */
class RestTimerTest {
    private val start = 1_000_000_000L

    private fun at(seconds: Int) = start + seconds * 1000L

    @Test
    fun countsDown() {
        val timer = RestTimer().started(90, start)
        assertEquals(60_000, timer.remaining(at(30)))
        assertEquals(0, timer.remaining(at(200)))
    }

    @Test
    fun pauseKeepsTheTimeLeft() {
        var timer = RestTimer().started(90, start).paused(at(30))
        assertTrue(timer.isPaused)
        assertEquals(60_000, timer.remaining(at(500)))
        timer = timer.resumed(at(500))
        assertEquals(50_000, timer.remaining(at(510)))
    }

    @Test
    fun addsAndRemovesTime() {
        var timer = RestTimer().started(90, start).adding(15, start)
        assertEquals(105_000, timer.remaining(start))
        timer = timer.adding(-15, at(10))
        assertEquals(80_000, timer.remaining(at(10)))
    }

    @Test
    fun removingMoreThanIsLeftEndsTheRest() {
        assertFalse(RestTimer().started(10, start).adding(-15, start).isActive)
    }

    @Test
    fun pausedTimeCanChangeToo() {
        val timer = RestTimer().started(60, start).paused(start).adding(15, at(100))
        assertEquals(75_000, timer.remaining(at(200)))
    }

    @Test
    fun theTimeKeepsRoomForItsWidest() {
        val now = 1_800_000_000_000L
        var rest = RestTimer()
        assertEquals("0:00", rest.widestClock)
        rest = rest.started(95, now)
        assertEquals("0:00", rest.widestClock)
        rest = rest.started(599, now)
        assertEquals("0:00", rest.widestClock)
        rest = rest.adding(15, now)
        assertEquals("00:00", rest.widestClock)
        rest = rest.started(600, now)
        assertEquals("00:00", rest.widestClock)
        rest = rest.started(3600, now)
        assertEquals("0:00:00", rest.widestClock)
        // As many characters as the time it starts at.
        assertEquals("0:00".length, restLabel(599).length)
        assertEquals("00:00".length, restLabel(600).length)
        assertEquals("1:30", restLabel(90))
    }

    @Test
    fun movesLikeAList() {
        val items = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "a", "c", "d", "e"), moved(items, setOf(0), 2))
        assertEquals(listOf("e", "a", "b", "c", "d"), moved(items, setOf(4), 0))
        assertEquals(listOf("a", "c", "d", "b", "e"), moved(items, setOf(1), 4))
        assertEquals(listOf("a", "c", "e", "b", "d"), moved(items, setOf(1, 3), 5))
        assertEquals(items, moved(items, setOf(2), 2))
        assertEquals(items, moved(items, setOf(2), 3))
    }
}
