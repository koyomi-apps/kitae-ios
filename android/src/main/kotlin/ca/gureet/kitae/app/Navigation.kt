// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/** A page Kamae shows: each tab's first page, the workout, and the pages pushed over them. */
sealed interface Route {
    /** A tab's first page. */
    data class Home(val tab: Tab) : Route

    /** The workout in progress, the first page of its own stack over the tabs. */
    data object Workout : Route

    /** A past workout, set by set. */
    data class PastWorkout(val id: Long) : Route

    /** An exercise's page. */
    data class ExercisePage(val id: Long) : Route

    /** The plan editor, on one of the plan's days, or its first. */
    data class PlanEditor(val planId: Long, val dayId: Long? = null) : Route

    /** Settings, from You. */
    data object Settings : Route

    /** Picks an exercise to add to one of a plan's days. */
    data class Picker(val planId: Long, val dayId: Long) : Route
}

/** How a page moves: it opens pages on its own stack, and goes back. */
class PageNavigation(val open: (Route) -> Unit, val back: () -> Unit)

/**
 * Where people are: the tab showing, each tab's stack of pages, and the workout's own stack,
 * which shows over the tabs while the workout is open (exercise pages and the plan editor open on
 * it, as on the iPhone).
 */
@Stable
class Navigator(startTab: Tab = Tab.Today) {
    var tab by mutableStateOf(startTab)

    private val stacks = Tab.entries.associateWith { mutableStateListOf<Route>(Route.Home(it)) }

    val workoutStack: SnapshotStateList<Route> = mutableStateListOf(Route.Workout)

    fun stack(tab: Tab): SnapshotStateList<Route> = stacks.getValue(tab)

    /** Shows [item], or takes its stack back to its first page when it's already showing. */
    fun select(item: Tab) {
        if (item == tab) stack(item).popToFirst() else tab = item
    }

    /** What a page on [stack] uses to move. */
    fun on(stack: SnapshotStateList<Route>) = PageNavigation(open = { stack.open(it) }, back = { stack.back() })

    companion object {
        /** Keeps the tab and the stacks through a configuration change or the process being stopped. */
        val Saver: Saver<Navigator, Any> = Saver(
            save = { navigator ->
                buildList {
                    add(navigator.tab.name)
                    for (tab in Tab.entries) add(navigator.stack(tab).joinToString(";") { it.encoded() })
                    add(navigator.workoutStack.joinToString(";") { it.encoded() })
                }
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val parts = saved as List<String>
                Navigator(Tab.valueOf(parts[0])).apply {
                    Tab.entries.forEachIndexed { index, tab -> stack(tab).restore(parts[index + 1]) }
                    workoutStack.restore(parts[Tab.entries.size + 1])
                }
            },
        )
    }
}

/** Opens [route], or goes back to it when it's already on the stack, so no page shows twice. */
fun SnapshotStateList<Route>.open(route: Route) {
    val index = indexOf(route)
    if (index < 0) add(route) else while (size > index + 1) removeAt(lastIndex)
}

fun SnapshotStateList<Route>.back() {
    if (size > 1) removeAt(lastIndex)
}

fun SnapshotStateList<Route>.popToFirst() {
    while (size > 1) removeAt(lastIndex)
}

private fun SnapshotStateList<Route>.restore(encoded: String) {
    val routes = encoded.split(";").mapNotNull(::decodeRoute)
    if (routes.isNotEmpty()) {
        clear()
        addAll(routes)
    }
}

private fun Route.encoded(): String = when (this) {
    is Route.Home -> "home:${tab.name}"
    Route.Workout -> "workout"
    Route.Settings -> "settings"
    is Route.PastWorkout -> "past:$id"
    is Route.ExercisePage -> "exercise:$id"
    is Route.PlanEditor -> "plan:$planId:${dayId ?: ""}"
    is Route.Picker -> "picker:$planId:$dayId"
}

private fun decodeRoute(text: String): Route? {
    val parts = text.split(":")
    return when (parts.firstOrNull()) {
        "home" -> parts.getOrNull(1)?.let { name -> Tab.entries.firstOrNull { it.name == name } }?.let(Route::Home)
        "workout" -> Route.Workout
        "settings" -> Route.Settings
        "past" -> parts.getOrNull(1)?.toLongOrNull()?.let(Route::PastWorkout)
        "exercise" -> parts.getOrNull(1)?.toLongOrNull()?.let(Route::ExercisePage)
        "plan" -> parts.getOrNull(1)?.toLongOrNull()?.let { Route.PlanEditor(it, parts.getOrNull(2)?.toLongOrNull()) }
        "picker" -> {
            val planId = parts.getOrNull(1)?.toLongOrNull()
            val dayId = parts.getOrNull(2)?.toLongOrNull()
            if (planId != null && dayId != null) Route.Picker(planId, dayId) else null
        }
        else -> null
    }
}
