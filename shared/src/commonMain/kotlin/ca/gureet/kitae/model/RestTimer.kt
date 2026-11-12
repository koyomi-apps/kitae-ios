// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.model

import kotlinx.serialization.Serializable

/**
 * The rest between sets, as the iPhone counts it (`RestTimer.swift`). It counts down to a moment
 * rather than ticking, so it stays right while the app is in the background. Times are in
 * milliseconds since 1970, like a workout's start, and lengths in milliseconds. It's
 * serializable, so an app can keep it and carry on after it's closed.
 */
@Serializable
data class RestTimer(
    /** When the rest ends, while it's counting down. */
    val endsAt: Long? = null,
    /** The time left, while it's paused. */
    val pausedRemaining: Long? = null,
    /** How long this rest is in total, for the progress bar. */
    val total: Long = 0,
) {
    val isRunning: Boolean
        get() = endsAt != null

    val isPaused: Boolean
        get() = pausedRemaining != null

    val isActive: Boolean
        get() = isRunning || isPaused

    fun remaining(now: Long): Long = endsAt?.let { maxOf(it - now, 0) } ?: pausedRemaining ?: 0

    /** A rest of [seconds] starting [now]. */
    fun started(seconds: Int, now: Long): RestTimer {
        val total = maxOf(seconds, 0) * 1000L
        return RestTimer(endsAt = now + total, total = total)
    }

    fun paused(now: Long): RestTimer {
        val endsAt = endsAt ?: return this
        return copy(endsAt = null, pausedRemaining = maxOf(endsAt - now, 0))
    }

    fun resumed(now: Long): RestTimer {
        val pausedRemaining = pausedRemaining ?: return this
        return copy(endsAt = now + pausedRemaining, pausedRemaining = null)
    }

    /** Adds or takes away [seconds]. Taking away more than is left ends the rest. */
    fun adding(seconds: Int, now: Long): RestTimer {
        if (!isActive) return this
        val left = maxOf(remaining(now) + seconds * 1000L, 0)
        if (left == 0L) return RestTimer()
        val total = maxOf(total + seconds * 1000L, left)
        return if (isRunning) RestTimer(endsAt = now + left, total = total) else RestTimer(pausedRemaining = left, total = total)
    }

    /**
     * The countdown at its widest, as it starts: "0:00", "00:00" from ten minutes, or "0:00:00"
     * from an hour. The time left keeps this much room, so it's never cut short.
     */
    val widestClock: String
        get() {
            val seconds = (total + 999) / 1000
            return when {
                seconds >= 3600 -> "0:00:00"
                seconds >= 600 -> "00:00"
                else -> "0:00"
            }
        }
}

/** A rest length as Kitae shows it: 90 seconds is 1:30. */
fun restLabel(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${if (rest < 10) "0" else ""}$rest"
}

/**
 * [items] in a new order, the way a list moves rows: the ones at [source] go in front of the one
 * at [destination], which counts from before the move.
 */
fun <T> moved(items: List<T>, source: Set<Int>, destination: Int): List<T> {
    val moving = source.filter { it in items.indices }.sorted().map { items[it] }
    val rest = items.filterIndexed { index, _ -> index !in source }.toMutableList()
    val index = destination - source.count { it in 0 until maxOf(destination, 0) }
    rest.addAll(index.coerceIn(0, rest.size), moving)
    return rest
}
