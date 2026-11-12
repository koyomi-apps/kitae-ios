// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.database.DayWithExercises
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.DayChip
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.EmptyState
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.KitaeCard
import ca.gureet.kitae.ui.components.MauveButton
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import kotlin.random.Random

/**
 * Today, as the iPhone has it (`TodayView.swift`) and the Kamae canvas draws it: the plan's name
 * with its menu, the day up next in big olive letters, the days to pick from, Start, a short line,
 * and the day's exercises.
 */
@Composable
fun TodayScreen(
    state: AppState,
    onPickDay: (Long) -> Unit,
    onStart: (DayWithExercises) -> Unit,
    onSelectPlan: (Long) -> Unit,
    onChoosePlan: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    line: Int = rememberSaveable { Random.nextInt(6) },
) {
    val plan = state.plan
    Column(modifier.fillMaxSize().padding(contentPadding)) {
        TopBar(state, onSelectPlan, onChoosePlan)
        when {
            !state.loaded -> Unit
            plan == null -> Centered {
                EmptyState(
                    R.drawable.ic_today,
                    stringResource(R.string.no_plan_title),
                    stringResource(R.string.no_plan_text),
                    action = stringResource(R.string.choose_plan),
                    onAction = onChoosePlan,
                )
            }
            plan.days.isEmpty() -> Centered {
                EmptyState(R.drawable.ic_today, stringResource(R.string.no_days_title), stringResource(R.string.no_days_text))
            }
            else -> Day(state, state.selectedDay ?: plan.days.first(), onPickDay, onStart, line)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun TopBar(state: AppState, onSelectPlan: (Long) -> Unit, onChoosePlan: () -> Unit) {
    val colors = kitaeColors
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val plan = state.plan ?: return@Row
        Box {
            TextButton(onClick = { menuOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    plan.plan.name,
                    color = colors.ink,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                KamaeIcon(R.drawable.ic_chevron_down, null, tint = colors.ink)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (state.workout == null) {
                    for (summary in state.plans) {
                        DropdownMenuItem(
                            text = { Text(summary.name) },
                            onClick = {
                                menuOpen = false
                                onSelectPlan(summary.id)
                            },
                            trailingIcon = {
                                if (summary.id == plan.plan.id) KamaeIcon(R.drawable.ic_check, null, tint = colors.olive)
                            },
                        )
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_plans)) },
                    onClick = {
                        menuOpen = false
                        onChoosePlan()
                    },
                )
            }
        }
    }
}

@Composable
private fun Day(
    state: AppState,
    day: DayWithExercises,
    onPickDay: (Long) -> Unit,
    onStart: (DayWithExercises) -> Unit,
    line: Int,
) {
    val colors = kitaeColors
    val workout = state.workout
    val isGoing = workout != null && workout.dayId == day.day.id
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            val caption = when {
                isGoing -> R.string.caption_in_progress
                state.hasDoneSets -> R.string.caption_up_next
                else -> R.string.caption_first_session
            }
            Caption(stringResource(caption))
            DisplayText(
                day.day.name,
                KitaeType.dayHero,
                colors.olive,
                Modifier.padding(top = 6.dp).offset(x = (-4).dp).semantics { heading() },
                minSize = 44.sp,
                maxLines = 2,
            )
            val summary = when {
                day.exercises.isEmpty() -> stringResource(R.string.no_exercises_yet)
                isGoing -> pluralStringResource(R.plurals.sets_done, workout.progress.setCount, workout.progress.doneCount, workout.progress.setCount)
                else -> stringResource(
                    R.string.day_summary,
                    pluralStringResource(R.plurals.exercise_count, day.exercises.size, day.exercises.size),
                    pluralStringResource(R.plurals.set_count, day.setCount, day.setCount),
                )
            }
            Caption(summary, Modifier.padding(top = 14.dp))
        }
        LazyRow(
            Modifier.padding(top = 20.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.days, key = { it.day.id }) { option ->
                DayChip(option.day.name, option.day.id == day.day.id, onClick = { onPickDay(option.day.id) })
            }
        }
        if (day.exercises.isNotEmpty()) {
            MauveButton(onClick = { onStart(day) }, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                Text(stringResource(if (isGoing) R.string.continue_workout else R.string.start), style = KitaeType.bigButton)
                KamaeIcon(R.drawable.ic_start, null, tint = colors.onMauve, size = 18.dp)
            }
        }
        Text(
            stringArrayResource(R.array.today_lines)[line % 6],
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp),
            color = colors.inkSoft,
            style = KitaeType.sectionCaption,
            textAlign = TextAlign.Center,
        )
        if (day.exercises.isNotEmpty()) {
            Caption(stringResource(R.string.caption_exercises), Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp), style = KitaeType.smallCaption)
            KitaeCard(Modifier.padding(horizontal = 16.dp)) {
                day.exercises.forEachIndexed { index, item ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.exercise.name, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
                        Spacer(Modifier.padding(start = 12.dp))
                        Caption(pluralStringResource(R.plurals.set_count, item.sets.size, item.sets.size), style = KitaeType.smallCaption)
                    }
                }
            }
        }
    }
}
