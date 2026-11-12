// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Weights as they're written and typed, as the iPhone's `WeightFormatterTests` check them. */
class WeightTest {
    private val point = WeightFormatter(decimals = DecimalStyle.Point)

    @Test
    fun showsOneOrTwoDecimals() {
        assertEquals("20.0", point.string(kilograms = 20.0))
        assertEquals("22.5", point.string(kilograms = 22.5))
        assertEquals("18.75", point.string(kilograms = 18.75))
        assertEquals("1.05", point.string(kilograms = 1.05))
        assertEquals("0.0", point.string(kilograms = 0.0))
    }

    @Test
    fun commaStyle() {
        assertEquals("22,5", WeightFormatter(decimals = DecimalStyle.Comma).string(kilograms = 22.5))
    }

    @Test
    fun automaticFollowsTheRegion() {
        assertEquals("22,5", WeightFormatter(decimals = DecimalStyle.Automatic, phoneSeparator = ',').string(kilograms = 22.5))
        assertEquals("22.5", WeightFormatter(decimals = DecimalStyle.Automatic, phoneSeparator = '.').string(kilograms = 22.5))
    }

    @Test
    fun keyboardStepsKeepTheDecimalStyle() {
        assertEquals("−2.5", point.step(-2.5))
        assertEquals("+5", point.step(5.0))
        assertEquals("+2,5", WeightFormatter(decimals = DecimalStyle.Comma).step(2.5))
        assertEquals(listOf(-5.0, -2.5, 2.5, 5.0), WeightUnit.Pounds.steps)
    }

    @Test
    fun readsAPointOrAComma() {
        assertEquals(22.5, point.kilograms("22,5"))
        assertEquals(22.5, point.kilograms("22.5"))
        assertEquals(7.0, point.kilograms(" 7 "))
        assertEquals(22.0, point.kilograms("22."))
    }

    @Test
    fun rejectsWhatIsntAWeight() {
        assertNull(point.kilograms(""))
        assertNull(point.kilograms("-3"))
        assertNull(point.kilograms("abc"))
        assertNull(point.kilograms("22.5f"))
        assertNull(point.kilograms("Infinity"))
    }

    @Test
    fun pounds() {
        val pounds = WeightFormatter(unit = WeightUnit.Pounds, decimals = DecimalStyle.Point)
        assertEquals("45.0", pounds.string(kilograms = 45 * WeightUnit.KILOGRAMS_PER_POUND))
        val kg = assertNotNull(pounds.kilograms("45"))
        assertTrue(abs(kg - 20.411656) < 0.0001)
        assertTrue(abs(WeightUnit.Pounds.defaultWeightKg - kg) < 1e-9)
    }

    @Test
    fun bodyWeightsAndPercentagesToATenth() {
        assertEquals("78.4 kg", point.body(kilograms = 78.38))
        assertEquals("18.5%", point.percent(18.46))
        assertEquals("18%", point.percent(18.04))
        assertEquals("18", point.tenthsOrWhole(18.0))
    }
}
