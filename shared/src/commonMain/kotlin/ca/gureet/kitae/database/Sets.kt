// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.database

/** A set's type. The stored names are Kitae for Android's, so backups stay compatible. */
enum class SetType(val stored: String) {
    Warmup("Warmup"),
    Standard("Standard"),
    Failure("Failure"),
    Drop("Drop"),
    RestPause("RestPause"),
    ;

    companion object {
        /** A type as it's stored. One this version doesn't know counts as standard. */
        fun fromStored(stored: String): SetType = entries.firstOrNull { it.stored == stored } ?: Standard
    }
}

/** A set's values before it's saved. Weights are always kilograms. */
data class SetDraft(
    val reps: Int,
    val weightKg: Double,
    val type: SetType = SetType.Standard,
    /** Rest after this set. Null uses the exercise's rest, or the default. */
    val restSeconds: Int? = null,
)

/**
 * New places that put the warm-ups in front of the other sets, keeping each group's order, as
 * the iPhone's `warmupsFirstSlots` does: each of [items] (in their current order) that moves,
 * with the place it moves to.
 */
internal fun <T> warmupsFirst(items: List<T>, slot: (T) -> Int, isWarmup: (T) -> Boolean): List<Pair<T, Int>> {
    val slots = items.map(slot)
    val wanted = items.filter(isWarmup) + items.filterNot(isWarmup)
    return wanted.mapIndexedNotNull { index, item -> if (slot(item) == slots[index]) null else item to slots[index] }
}
