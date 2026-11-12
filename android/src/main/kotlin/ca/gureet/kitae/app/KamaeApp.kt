// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.gureet.kitae.R
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.EpochDay
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.exercises.ExercisePageScreen
import ca.gureet.kitae.exercises.ExercisesScreen
import ca.gureet.kitae.history.HistoryScreen
import ca.gureet.kitae.history.PastWorkoutScreen
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.plans.ExercisePickerScreen
import ca.gureet.kitae.plans.PlanEditorScreen
import ca.gureet.kitae.plans.PlansSheet
import ca.gureet.kitae.plans.RECENT_IN_PICKER
import ca.gureet.kitae.settings.SettingsScreen
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
import ca.gureet.kitae.workout.phoneDecimalSeparator
import ca.gureet.kitae.workout.rememberNow
import ca.gureet.kitae.workout.startedAt
import ca.gureet.kitae.you.YouScreen
import java.util.Calendar
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** What people do around the app, beside the workout's own actions. */
interface AppActions : WorkoutActions, EditActions, SettingsActions {
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
 * screens, each tab keeping its own stack of pages, at most 600 wide. The workout opens over
 * everything, sliding up, with its own stack for the pages it opens; while it's minimized a bar
 * above the tabs opens it again.
 */
@Composable
fun KamaeRoot(
    state: AppState,
    settings: AppSettings,
    actions: AppActions,
    pages: PageSource,
    frozenNow: Long? = null,
    navigator: Navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() },
) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    var switchingTo by remember { mutableStateOf<DayWithExercises?>(null) }
    var question by remember { mutableStateOf<FinishQuestion?>(null) }
    val formatter = remember(settings.unit, settings.decimals) { WeightFormatter(settings.unit, settings.decimals, phoneDecimalSeparator()) }
    val today = EpochDay.of(Instant.fromEpochMilliseconds(frozenNow ?: System.currentTimeMillis()), TimeZone.currentSystemDefault())
    val context = LocalContext.current
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }

    fun start(day: DayWithExercises) {
        val going = state.workout
        if (going == null || going.dayId == day.day.id) {
            actions.start(day)
            return
        }
        switchingTo = day
        question = FinishQuestion.first(going.progress, startingAnotherDay = true)
    }

    val page: @Composable (Route, PageNavigation) -> Unit = { route, nav ->
        when (route) {
            is Route.Home -> when (route.tab) {
                Tab.Today -> TodayScreen(
                    state,
                    onPickDay = actions::pickDay,
                    onStart = ::start,
                    onSelectPlan = actions::selectPlan,
                    onChoosePlan = { actions.choosePlan(true) },
                    onEditPlan = { dayId -> state.plan?.let { nav.open(Route.PlanEditor(it.plan.id, dayId)) } },
                )
                Tab.History -> {
                    val months = rememberPage(state.revision) { pages.historyMonths() }
                    HistoryScreen(
                        months?.value,
                        state.workout,
                        today,
                        onOpen = { summary ->
                            if (summary.workout.id == state.workout?.id) actions.presentWorkout(true) else nav.open(Route.PastWorkout(summary.workout.id))
                        },
                        onDelete = actions::deleteWorkout,
                    )
                }
                Tab.Exercises -> {
                    val list = rememberPage(state.revision) { pages.exerciseList() }
                    ExercisesScreen(
                        list?.value,
                        formatter,
                        settings.defaultRestSeconds,
                        onOpen = { nav.open(Route.ExercisePage(it)) },
                        onSaveExercise = { actions.saveExercise(it, null) },
                    )
                }
                Tab.You -> {
                    val firstWeekday = remember { Calendar.getInstance().firstDayOfWeek }
                    val data = rememberPage(state.revision, state.workout?.progress?.doneCount, today) { pages.youData(today, firstWeekday) }
                    YouScreen(
                        state,
                        data?.value,
                        onSettings = { nav.open(Route.Settings) },
                        onEditPlan = { state.plan?.let { nav.open(Route.PlanEditor(it.plan.id, state.selectedDay?.day?.id)) } },
                        onChoosePlan = { actions.choosePlan(true) },
                    )
                }
            }
            Route.Settings -> SettingsScreen(settings, state.backup, version, actions, onBack = nav.back)
            Route.Workout -> state.workout?.let { workout ->
                WorkoutScreen(workout, settings, actions, state.restsFinished, frozenNow, onOpen = nav.open)
            }
            is Route.PastWorkout -> {
                val record = rememberPage(route, state.revision) { pages.workoutRecord(route.id) }
                PastWorkoutScreen(record?.value, record != null, formatter, today, nav.open, nav.back, actions::deleteWorkout)
            }
            is Route.ExercisePage -> {
                val details = rememberPage(route, state.revision) { pages.exerciseDetails(route.id) }
                ExercisePageScreen(
                    details?.value,
                    details != null,
                    formatter,
                    settings.defaultRestSeconds,
                    today,
                    canDelete = state.workout?.exercises?.none { it.exercise.id == route.id } ?: true,
                    onOpen = nav.open,
                    onBack = nav.back,
                    onSave = { draft, id -> actions.saveExercise(draft, id) },
                    onDelete = actions::deleteExercise,
                )
            }
            is Route.PlanEditor -> {
                val plan = rememberPage(route.planId, state.revision) { pages.plan(route.planId) }
                if (plan != null) {
                    PlanEditorScreen(
                        plan.value,
                        route,
                        busyDayId = state.workout?.dayId,
                        busyPlanId = state.workout?.workout?.planId,
                        formatter = formatter,
                        defaultRestSeconds = settings.defaultRestSeconds,
                        actions = actions,
                        onOpen = nav.open,
                        onBack = nav.back,
                    )
                }
            }
            is Route.Picker -> {
                val loaded = rememberPage(route, state.revision) {
                    val plan = pages.plan(route.planId)
                    val day = plan?.days?.firstOrNull { it.day.id == route.dayId }
                    day to pages.exerciseList(recent = RECENT_IN_PICKER + (day?.exercises?.size ?: 0))
                }
                val day = loaded?.value?.first
                ExercisePickerScreen(
                    loaded?.value?.second,
                    day?.day?.name.orEmpty(),
                    day?.exercises?.map { it.exercise.id }?.toSet().orEmpty(),
                    settings.defaultRestSeconds,
                    onPick = { exerciseId ->
                        scope.launch {
                            actions.addExerciseToDay(route.dayId, exerciseId)
                            nav.back()
                        }
                    },
                    onBack = nav.back,
                    onSaveExercise = { actions.saveExercise(it, null) },
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val content = @Composable { modifier: Modifier ->
                Box(modifier.fillMaxWidth().statusBarsPadding()) {
                    val holder = rememberSaveableStateHolder()
                    holder.SaveableStateProvider(navigator.tab.name) {
                        val stack = navigator.stack(navigator.tab)
                        val nav = navigator.on(stack)
                        Pages(stack, nav, page)
                    }
                }
            }
            val select = { item: Tab -> navigator.select(item) }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = colors.paper, modifier = Modifier.width(96.dp)) {
                        Spacer(Modifier.height(32.dp))
                        for (item in Tab.entries) {
                            val selected = item == navigator.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { select(item) },
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
                            val selected = item == navigator.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { select(item) },
                                icon = { KamaeIcon(item.icon, null, tint = if (selected) colors.onOliveSoft else colors.inkSoft) },
                                label = { TabLabel(item, selected) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = colors.oliveSoft),
                            )
                        }
                    }
                }
            }
        }
        // Back from another tab's first page goes to Today, as Android's tabs do.
        BackHandler(enabled = navigator.tab != Tab.Today && navigator.stack(navigator.tab).size == 1) { navigator.tab = Tab.Today }
        AnimatedVisibility(
            visible = state.isWorkoutPresented && state.workout != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val stack = navigator.workoutStack
            Pages(stack, navigator.on(stack)) { route, nav ->
                if (route == Route.Workout) {
                    page(route, nav)
                } else {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) { page(route, nav) }
                }
            }
        }
    }

    if (state.isChoosingPlan) {
        PlansSheet(
            state,
            onSelect = actions::selectPlan,
            onEdit = { planId ->
                actions.choosePlan(false)
                val dayId = if (planId == state.plan?.plan?.id) state.selectedDay?.day?.id else null
                navigator.on(navigator.stack(navigator.tab)).open(Route.PlanEditor(planId, dayId))
            },
            onCreate = actions::createPlan,
            onDelete = actions::deletePlan,
            onDismiss = { actions.choosePlan(false) },
        )
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

/** What a page has read, once it has: [value] may be null for something that's gone. */
class Loaded<T>(val value: T)

/** Reads a page's data, and reads it again whenever one of [keys] changes. Null until it's read. */
@Composable
fun <T> rememberPage(vararg keys: Any?, load: suspend () -> T): Loaded<T>? {
    val loaded by produceState<Loaded<T>?>(null, *keys) { value = Loaded(load()) }
    return loaded
}

/** A stack of pages, each on Paper, at most 600 wide and centred, with Android's back gestures. */
@Composable
private fun Pages(stack: SnapshotStateList<Route>, nav: PageNavigation, page: @Composable (Route, PageNavigation) -> Unit) {
    val colors = kitaeColors
    NavDisplay(
        backStack = stack,
        onBack = { nav.back() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = { route ->
            NavEntry(route) {
                Box(Modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) { page(route, nav) }
                }
            }
        },
    )
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
