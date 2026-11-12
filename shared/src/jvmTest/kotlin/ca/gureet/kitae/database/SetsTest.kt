// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Set types and warm-ups first, as the iPhone's `WarmupOrderTests` check them. */
class SetsTest {
    private data class Item(val slot: Int, val isWarmup: Boolean)

    @Test
    fun warmupsMoveToTheFront() {
        val sets = listOf(Item(0, false), Item(1, true), Item(2, false), Item(3, true))
        val moves = warmupsFirst(sets, { it.slot }, { it.isWarmup })
        assertEquals(listOf(1, 3, 0, 2), moves.map { it.first.slot })
        assertEquals(listOf(0, 1, 2, 3), moves.map { it.second })
    }

    @Test
    fun nothingMovesWhenWarmupsAreFirst() {
        val sets = listOf(Item(4, true), Item(7, false), Item(9, false))
        assertTrue(warmupsFirst(sets, { it.slot }, { it.isWarmup }).isEmpty())
    }

    @Test
    fun unknownTypesReadAsStandard() {
        assertEquals(SetType.RestPause, SetType.fromStored("RestPause"))
        assertEquals(SetType.Standard, SetType.fromStored("Cluster"))
    }
}
