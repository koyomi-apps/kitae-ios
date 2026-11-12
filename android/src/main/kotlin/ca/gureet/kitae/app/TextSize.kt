// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import kotlin.math.abs

/**
 * Kamae's own text size, picked in Settings in place of the phone's (the iPhone's `TextSize`):
 * seven steps whose [scale] stands in for the phone's font scale, as the iPhone's body sizes,
 * 14 to 23 points, stand to its default 17.
 */
enum class TextSize(val stored: String, val scale: Float) {
    ExtraSmall("extraSmall", 0.82f),
    Small("small", 0.88f),
    Medium("medium", 0.94f),
    Standard("standard", 1f),
    Large("large", 1.12f),
    ExtraLarge("extraLarge", 1.24f),
    Largest("largest", 1.35f),
    ;

    companion object {
        fun fromStored(stored: String?): TextSize? = entries.firstOrNull { it.stored == stored }

        /** The size nearest the phone's [fontScale], for the slider while Kamae follows the phone. */
        fun nearest(fontScale: Float): TextSize = entries.minBy { abs(it.scale - fontScale) }
    }
}
