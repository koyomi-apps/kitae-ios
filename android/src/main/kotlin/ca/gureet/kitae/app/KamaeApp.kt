// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.plans.PlansSheet
import ca.gureet.kitae.today.TodayScreen
import ca.gureet.kitae.ui.components.EmptyState
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.workout.FinishQuestion
import ca.gureet.kitae.workout.FinishQuestionDialog
import ca.gureet.kitae.workout.WorkoutActions
import ca.gureet.kitae.workout.WorkoutScreen
import ca.gureet.kitae.workout.clockText
import ca.gureet.kitae.workout.rememberNow
import ca.gureet.kitae.workout.startedAt

/** What people do around the app, beside the workout's own actions. */
interface AppActions : WorkoutActions {
    fun pickDay(id: Long)
    fun start(day: DayWithExercises)
    fun selectPlan(id: Long)
    fun choosePlan(open: Boolean)
    fun presentWorkout(open: Boolean)
    fun finishAndStart(mode: FinishMode, addNewSets: Boolean, day: DayWithExercises)
}

/** The four tabs, each keeping its own pages. */
enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Today(R.string.tab_today, R.drawable.ic_today),
    History(R.string.tab_history, R.drawable.ic_history),
    Exercises(R.string.tab_exercises, R.drawable.ic_exercises),
    You(R.string.tab_you, R.drawable.ic_you),
}

/**
 * Kamae's frame: a navigation bar with the four tabs on phones, or a navigation rail on wider
 * screens, with each tab's content at most 600 wide. The workout opens over everything, sliding
 * up, and while it's minimized a bar above the tabs opens it again.
 */
@Composable
fun KamaeRoot(
    state: AppState,
    settings: AppSettings,
    actions: AppActions,
    frozenNow: Long? = null,
    startTab: Tab = Tab.Today,
) {
    val colors = kitaeColors
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var switchingTo by remember { mutableStateOf<DayWithExercises?>(null) }
    var question by remember { mutableStateOf<FinishQuestion?>(null) }

    fun start(day: DayWithExercises) {
        val going = state.workout
        if (going == null || going.dayId == day.day.id) {
            actions.start(day)
            return
        }
        switchingTo = day
        question = FinishQuestion.first(going.progress, startingAnotherDay = true)
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val content = @Composable { modifier: Modifier ->
                Box(modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    val width = Modifier.widthIn(max = 600.dp).fillMaxSize()
                    when (tab) {
                        Tab.Today -> TodayScreen(
                            state,
                            onPickDay = actions::pickDay,
                            onStart = ::start,
                            onSelectPlan = actions::selectPlan,
                            onChoosePlan = { actions.choosePlan(true) },
                            modifier = width,
                        )
                        else -> Box(width, contentAlignment = Alignment.Center) {
                            EmptyState(tab.icon, stringResource(R.string.coming_title), stringResource(R.string.coming_text))
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = colors.paper, modifier = Modifier.width(96.dp)) {
                        Spacer(Modifier.height(32.dp))
                        for (item in Tab.entries) {
                            val selected = item == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { tab = item },
                                icon = { KamaeIcon(item.icon, null, tint = if (selected) colors.onOliveSoft else colors.inkSoft) },
                                label = { TabLabel(item, selected) },
                                colors = NavigationRailItemDefaults.colors(indicatorColor = colors.oliveSoft),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        content(Modifier.weight(1f))
                        state.workout?.let { WorkoutBar(it, frozenNow, onOpen = { actions.presentWorkout(true) }) }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    content(Modifier.weight(1f))
                    state.workout?.let { WorkoutBar(it, frozenNow, onOpen = { actions.presentWorkout(true) }) }
                    NavigationBar(containerColor = colors.card) {
                        for (item in Tab.entries) {
                            val selected = item == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab = item },
                                icon = { KamaeIcon(item.icon, null, tint = if (selected) colors.onOliveSoft else colors.inkSoft) },
                                label = { TabLabel(item, selected) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = colors.oliveSoft),
                            )
                        }
                    }
                }
            }
        }
        val workout = state.workout
        AnimatedVisibility(
            visible = state.isWorkoutPresented && workout != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val shown = state.workout ?: workout
            if (shown != null) WorkoutScreen(shown, settings, actions, state.restsFinished, frozenNow)
        }
    }

    if (state.isChoosingPlan) {
        PlansSheet(state, onSelect = actions::selectPlan, onDismiss = { actions.choosePlan(false) })
    }
    val going = state.workout
    val next = switchingTo
    val asked = question
    if (going != null && next != null && asked != null) {
        FinishQuestionDialog(
            asked,
            going.dayName ?: stringResource(R.string.workout),
            going.progress.addedDoneCount,
            onQuestion = { question = it },
            onFinish = { mode, addNewSets ->
                switchingTo = null
                actions.finishAndStart(mode, addNewSets, next)
            },
            nextDayName = next.day.name,
            onContinue = { actions.presentWorkout(true) },
        )
    }
}

@Composable
private fun TabLabel(tab: Tab, selected: Boolean) {
    Text(
        stringResource(tab.label),
        color = if (selected) kitaeColors.ink else kitaeColors.inkSoft,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
}

/**
 * The workout while it's minimized, floating above the tabs as the Kamae canvas draws it: a
 * dumbbell, the day's name, its time and sets, and while resting a pill with the time left, which
 * the day's name gives way to. Tapping it opens the workout.
 */
@Composable
fun WorkoutBar(workout: WorkoutState, frozenNow: Long?, onOpen: () -> Unit) {
    val colors = kitaeColors
    val now = rememberNow(frozenNow)
    val progress = workout.progress
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.field,
            contentColor = colors.ink,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier
                    .clickable(onClickLabel = stringResource(R.string.open_workout), role = Role.Button, onClick = onOpen)
                    .heightIn(min = 56.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KamaeIcon(R.drawable.ic_today, null, tint = colors.olive)
                Column(Modifier.weight(1f)) {
                    Text(
                        workout.dayName ?: stringResource(R.string.workout),
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.workout_progress, progress.setCount, clockText(now - workout.startedAt), progress.doneCount, progress.setCount),
                        color = colors.inkSoft,
                        style = KitaeType.sectionCaption,
                        maxLines = 1,
                    )
                }
                val rest = workout.rest
                if (rest.isActive) {
                    Row(
                        Modifier.height(36.dp).background(colors.mauveSoft, CircleShape).padding(start = 10.dp, end = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KamaeIcon(R.drawable.ic_timer, null, tint = colors.onMauveSoft, size = 18.dp)
                        Text(
                            clockText(rest.remaining(now), countdown = true),
                            color = colors.onMauveSoft,
                            style = KitaeType.chip.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
