// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

/**
 * A length of time as the workout shows it: "0:05", "23:40", or "1:05:12" from an hour. A
 * countdown rounds up, so a rest shows 0:01 until it's over; a clock counting up rounds down.
 */
fun clockText(milliseconds: Long, countdown: Boolean = false): String {
    val seconds = maxOf(if (countdown) (milliseconds + 999) / 1000 else milliseconds / 1000, 0)
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = seconds % 60
    val tail = "${if (rest < 10) "0" else ""}$rest"
    return if (hours > 0) "$hours:${if (minutes < 10) "0" else ""}$minutes:$tail" else "$minutes:$tail"
}
