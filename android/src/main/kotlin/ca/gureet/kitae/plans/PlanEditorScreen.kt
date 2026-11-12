// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.EditActions
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.database.PlanWithDays
import ca.gureet.kitae.database.PlannedSet
import ca.gureet.kitae.database.setLabels
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.DayChip
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.setSummary
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import kotlinx.coroutines.launch

/** "5 days · 23 exercises". */
@Composable
fun planSizeLabel(days: Int, exercises: Int): String = stringResource(
    R.string.plan_summary,
    androidx.compose.ui.res.pluralStringResource(R.plurals.day_count, days, days),
    androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, exercises, exercises),
)

private enum class NameRequest { NewDay, RenameDay, RenamePlan }

private enum class Confirmation { DeleteDay, DeletePlan, EmptyPlan }

/**
 * Changes a plan (the iPhone's `PlanEditView`): its days as chips, each day's exercises as cards
 * with their planned sets, and a menu for the day and the plan. Every change is saved straight
 * away. Leaving a plan without exercises asks whether to delete it. [busyDayId] and [busyPlanId]
 * are the workout in progress's, which can't be deleted while it's going.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanEditorScreen(
    plan: PlanWithDays?,
    route: Route.PlanEditor,
    busyDayId: Long?,
    busyPlanId: Long?,
    formatter: WeightFormatter,
    defaultRestSeconds: Int,
    actions: EditActions,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    var selectedDayId by rememberSaveable { mutableStateOf(route.dayId) }
    var setSheet by rememberSaveable { mutableStateOf<Long?>(null) }
    var nameSheet by rememberSaveable { mutableStateOf<NameRequest?>(null) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val days = plan?.days.orEmpty()
    val day = days.firstOrNull { it.day.id == selectedDayId } ?: days.firstOrNull()
    val planId = route.planId
    val canDeletePlan = planId != busyPlanId
    val canDeleteDay = day != null && day.day.id != busyDayId
    val asksBeforeLeaving = plan != null && plan.exerciseCount == 0 && canDeletePlan
    val suggestions = stringArrayResource(R.array.day_name_suggestions).toList()
    val unusedDayNames = suggestions.filter { name -> days.none { it.day.name.equals(name, ignoreCase = true) } }

    fun leave() = if (asksBeforeLeaving) confirmation = Confirmation.EmptyPlan else onBack()

    fun moveDay(by: Int) {
        val index = days.indexOfFirst { it.day.id == day?.day?.id }
        if (index < 0 || index + by !in days.indices) return
        val order = days.map { it.day.id }.toMutableList()
        order.add(index + by, order.removeAt(index))
        actions.orderDays(order)
    }

    BackHandler(enabled = asksBeforeLeaving) { leave() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::leave) { KamaeIcon(R.drawable.ic_back, stringResource(R.string.back)) }
            Box(Modifier.weight(1f))
            if (plan != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { KamaeIcon(R.drawable.ic_more, stringResource(R.string.options)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (day != null) {
                            MenuHeader(day.day.name)
                            MenuItem(stringResource(R.string.rename_day), R.drawable.ic_edit) {
                                menuOpen = false
                                nameSheet = NameRequest.RenameDay
                            }
                            val index = days.indexOf(day)
                            MenuItem(stringResource(R.string.move_earlier), R.drawable.ic_back, enabled = index > 0) {
                                menuOpen = false
                                moveDay(-1)
                            }
                            MenuItem(stringResource(R.string.move_later), R.drawable.ic_chevron_forward, enabled = index < days.lastIndex) {
                                menuOpen = false
                                moveDay(1)
                            }
                            MenuItem(stringResource(R.string.reorder_exercises), R.drawable.ic_drag, enabled = day.exercises.size > 1) {
                                menuOpen = false
                                reordering = true
                            }
                            MenuItem(stringResource(R.string.delete_day), R.drawable.ic_delete, enabled = canDeleteDay, destructive = true) {
                                menuOpen = false
                                confirmation = Confirmation.DeleteDay
                            }
                            HorizontalDivider()
                        }
                        MenuItem(stringResource(R.string.new_day), R.drawable.ic_add) {
                            menuOpen = false
                            nameSheet = NameRequest.NewDay
                        }
                        HorizontalDivider()
                        MenuHeader(plan.plan.name)
                        MenuItem(stringResource(R.string.rename_plan), R.drawable.ic_edit) {
                            menuOpen = false
                            nameSheet = NameRequest.RenamePlan
                        }
                        MenuItem(stringResource(R.string.delete_plan), R.drawable.ic_delete, enabled = canDeletePlan, destructive = true) {
                            menuOpen = false
                            confirmation = Confirmation.DeletePlan
                        }
                    }
                }
                BarTextButton(stringResource(R.string.done), ::leave)
            }
        }
        if (plan == null) return@Column
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
                item(key = "header") {
                    Column(Modifier.padding(bottom = 16.dp)) {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            DisplayText(plan.plan.name, KitaeType.pageTitle, colors.ink, Modifier.semantics { heading() }, minSize = 24.sp, maxLines = 2)
                            Caption(planSizeLabel(days.size, plan.exerciseCount), Modifier.padding(top = 2.dp))
                        }
                        if (days.isNotEmpty()) {
                            LazyRow(
                                Modifier.padding(top = 20.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(days, key = { it.day.id }) { option ->
                                    DayChip(option.day.name, option.day.id == day?.day?.id, onClick = { selectedDayId = option.day.id })
                                }
                                item(key = "new day") {
                                    Surface(onClick = { nameSheet = NameRequest.NewDay }, shape = CircleShape, color = colors.field, contentColor = colors.ink) {
                                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                            KamaeIcon(R.drawable.ic_add, stringResource(R.string.new_day), size = 20.dp)
                                        }
                                    }
                                }
                            }
                        } else {
                            FirstDay(unusedDayNames, onName = { name ->
                                scope.launch { runCatching { actions.addDay(planId, name) }.onSuccess { selectedDayId = it } }
                            }, onAnotherName = { nameSheet = NameRequest.NewDay })
                        }
                    }
                }
                if (day != null && day.exercises.isEmpty()) {
                    item(key = "empty") { EmptyDay() }
                }
                if (day != null) {
                    items(day.exercises, key = { it.dayExercise.id }) { item ->
                        ExerciseCard(
                            item,
                            day,
                            formatter,
                            actions,
                            onSet = { setSheet = it.id },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        )
                    }
                }
            }
            AddButton(
                text = day?.let { stringResource(R.string.add_exercise_to, it.day.name) } ?: stringResource(R.string.add_a_day),
                onClick = { if (day != null) onOpen(Route.Picker(planId, day.day.id)) else nameSheet = NameRequest.NewDay },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Sheets and questions

    val editedSet = setSheet?.let { id -> day?.exercises?.firstNotNullOfOrNull { item -> item.sets.firstOrNull { it.id == id }?.let { item to it } } }
    if (editedSet != null) {
        PlannedSetSheet(
            editedSet.first,
            editedSet.second,
            formatter,
            defaultRestSeconds,
            onChange = { actions.updatePlannedSet(editedSet.second.id, it) },
            onDelete = { actions.deletePlannedSet(editedSet.second.id) },
            onDismiss = { setSheet = null },
        )
    }
    if (reordering && day != null) {
        ReorderSheet(day.day.name, day.exercises, onOrder = actions::orderExercises, onDismiss = { reordering = false })
    }
    when (nameSheet) {
        NameRequest.NewDay -> NameSheet(
            stringResource(R.string.new_day),
            stringResource(R.string.day_name),
            stringResource(R.string.add),
            onSave = { selectedDayId = actions.addDay(planId, it) },
            onDismiss = { nameSheet = null },
            suggestions = unusedDayNames,
        )
        NameRequest.RenameDay -> if (day != null) NameSheet(
            stringResource(R.string.rename_day),
            stringResource(R.string.day_name),
            stringResource(R.string.save),
            onSave = { actions.renameDay(day.day.id, it) },
            onDismiss = { nameSheet = null },
            initial = day.day.name,
        )
        NameRequest.RenamePlan -> if (plan != null) NameSheet(
            stringResource(R.string.rename_plan),
            stringResource(R.string.plan_name),
            stringResource(R.string.save),
            onSave = { actions.renamePlan(planId, it) },
            onDismiss = { nameSheet = null },
            initial = plan.plan.name,
        )
        null -> Unit
    }
    when (confirmation) {
        Confirmation.DeleteDay -> if (day != null) ConfirmDialog(
            title = stringResource(R.string.delete_named, day.day.name),
            text = stringResource(R.string.delete_day_text, day.day.name),
            confirm = stringResource(R.string.delete_day),
            onConfirm = {
                val index = days.indexOf(day)
                actions.deleteDay(day.day.id)
                val left = days - day
                selectedDayId = left.getOrNull(minOf(index, left.lastIndex))?.day?.id
            },
            onDismiss = { confirmation = null },
        )
        Confirmation.DeletePlan -> ConfirmDialog(
            title = stringResource(R.string.delete_named, plan?.plan?.name.orEmpty()),
            text = stringResource(R.string.delete_plan_text),
            confirm = stringResource(R.string.delete_plan),
            onConfirm = {
                actions.deletePlan(planId)
                onBack()
            },
            onDismiss = { confirmation = null },
        )
        Confirmation.EmptyPlan -> ConfirmDialog(
            title = stringResource(R.string.empty_plan_title),
            text = stringResource(R.string.empty_plan_text),
            confirm = stringResource(R.string.delete_plan),
            onConfirm = {
                actions.deletePlan(planId)
                onBack()
            },
            onDismiss = { confirmation = null },
            dismiss = stringResource(R.string.keep_editing),
        )
        null -> Unit
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = kitaeColors.inkSoft,
        style = KitaeType.sectionCaption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MenuItem(text: String, icon: Int?, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = kitaeColors
    val tint = when {
        !enabled -> colors.inkFaint
        destructive -> colors.error
        else -> colors.ink
    }
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = icon?.let { { KamaeIcon(it, null, tint = tint) } },
        onClick = onClick,
        enabled = enabled,
    )
}

/** A new plan starts with naming its first day. */
@Composable
private fun FirstDay(suggestions: List<String>, onName: (String) -> Unit, onAnotherName: () -> Unit) {
    val colors = kitaeColors
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.plan_your_days),
            Modifier.padding(horizontal = 24.dp),
            color = colors.olive,
            style = KitaeType.sectionTitle.copy(fontSize = 30.sp, lineHeight = 34.sp),
        )
        Text(stringResource(R.string.plan_your_days_text), Modifier.padding(horizontal = 24.dp), color = colors.inkSoft, fontSize = 15.sp)
        LazyRow(
            Modifier.padding(top = 6.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(suggestions) { name -> DayChip(name, false, onClick = { onName(name) }) }
        }
        TextButton(onClick = onAnotherName, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.another_name), color = colors.olive, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyDay() {
    val colors = kitaeColors
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_exercises_title), color = colors.inkSoft, style = KitaeType.sectionTitle.copy(fontSize = 28.sp, lineHeight = 32.sp))
        Text(stringResource(R.string.add_one_below), Modifier.padding(top = 6.dp), color = colors.inkSoft, fontSize = 15.sp)
    }
}

/**
 * An exercise of the day on Card: its name and muscle, which offer Move up, Move down and
 * Remove, its planned sets, each opening its sheet, and Add set. Swiping takes a set, or the
 * exercise, out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseCard(
    item: DayExerciseWithSets,
    day: DayWithExercises,
    formatter: WeightFormatter,
    actions: EditActions,
    onSet: (PlannedSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    val index = day.exercises.indexOf(item)
    val labels = setLabels(item.sets.map { it.setType })
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp))) {
        Swipeable(onSwiped = { actions.removeFromDay(item.dayExercise.id) }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                        .heightIn(min = 54.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.exercise.name, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(muscleName(item.exercise.muscle).takeIf { item.exercise.muscle != null } ?: item.exercise.target, color = colors.inkSoft, fontSize = 13.sp)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(stringResource(R.string.move_up), null, enabled = index > 0) {
                        menuOpen = false
                        actions.orderExercises(day.exercises.map { it.dayExercise.id }.toMutableList().apply { add(index - 1, removeAt(index)) })
                    }
                    MenuItem(stringResource(R.string.move_down), null, enabled = index < day.exercises.lastIndex) {
                        menuOpen = false
                        actions.orderExercises(day.exercises.map { it.dayExercise.id }.toMutableList().apply { add(index + 1, removeAt(index)) })
                    }
                    HorizontalDivider()
                    MenuItem(stringResource(R.string.remove_from_day, day.day.name), R.drawable.ic_delete, destructive = true) {
                        menuOpen = false
                        actions.removeFromDay(item.dayExercise.id)
                    }
                }
            }
        }
        item.sets.forEachIndexed { setIndex, set ->
            Hairline(start = 44.dp)
            Swipeable(onSwiped = { actions.deletePlannedSet(set.id) }) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.set_options, labels[setIndex]), role = Role.Button) { onSet(set) }
                        .heightIn(min = 44.dp)
                        .padding(start = 44.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(labels[setIndex], Modifier.width(28.dp), color = colors.inkSoft, style = KitaeType.step.copy(fontWeight = FontWeight.Bold))
                    Text(
                        setSummary(formatter, set.reps, item.exercise.weight(set.weightKg), item.exercise.isIsometric),
                        Modifier.weight(1f),
                        color = colors.ink,
                        style = KitaeType.step,
                    )
                    set.restSeconds?.let { rest ->
                        Text(
                            stringResource(R.string.rest_chip, if (rest > 0) restLabel(rest) else stringResource(R.string.rest_off)),
                            color = colors.inkSoft,
                            style = KitaeType.sectionCaption,
                        )
                    }
                }
            }
        }
        Hairline(start = 44.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { actions.addPlannedSet(item.dayExercise.id, item.sets.lastOrNull()?.draft()) }
                .heightIn(min = 44.dp)
                .padding(start = 44.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KamaeIcon(R.drawable.ic_add, null, tint = colors.olive, size = 20.dp)
            Text(stringResource(R.string.add_set), color = colors.olive, fontSize = 16.sp)
        }
    }
}

/** A row that's taken out by swiping it to the start, over red. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Swipeable(onSwiped: () -> Unit, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp), content: @Composable () -> Unit) {
    val colors = kitaeColors
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onSwiped()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().background(colors.error, shape).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    KamaeIcon(R.drawable.ic_delete, stringResource(R.string.remove), tint = colors.paper, size = 20.dp)
                }
            }
        },
    ) {
        Box(Modifier.background(colors.card, shape)) { content() }
    }
}

/** "Add exercise to Push A" at the bottom, over a fade, as the workout's buttons sit. */
@Composable
private fun AddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.paper.copy(alpha = 0f), 0.4f to colors.paper.copy(alpha = 0.9f), 1f to colors.paper))
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
    ) {
        Surface(onClick = onClick, shape = CircleShape, color = colors.field, contentColor = colors.olive, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.heightIn(min = 54.dp).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KamaeIcon(R.drawable.ic_add, null, tint = colors.olive, size = 20.dp)
                Text(text, color = colors.olive, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
