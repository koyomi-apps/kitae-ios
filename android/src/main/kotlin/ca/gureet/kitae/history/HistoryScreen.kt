// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.WorkoutState
import ca.gureet.kitae.database.HistoryMonth
import ca.gureet.kitae.database.WorkoutSummary
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.EmptyState
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.cardRowShape
import ca.gureet.kitae.ui.durationLabel
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.ui.workoutDetails
import ca.gureet.kitae.ui.workoutTitle

/**
 * The second tab (the iPhone's `HistoryView`): every workout, newest first, a month at a time.
 * The one going opens the workout; the others open their page, and swiping one deletes it after
 * asking. [months] is null until they're read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    months: List<HistoryMonth>?,
    workout: WorkoutState?,
    today: Long,
    onOpen: (WorkoutSummary) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    kilocalories: (WorkoutSummary) -> Double? = { null },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var deleting by remember { mutableStateOf<WorkoutSummary?>(null) }
    Column(modifier.fillMaxSize()) {
        BigTitleBar(stringResource(R.string.history_title), scrollBehavior)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (months != null && months.isEmpty()) {
                EmptyState(
                    R.drawable.ic_history,
                    stringResource(R.string.history_empty_title),
                    stringResource(R.string.history_empty_text),
                    Modifier.align(Alignment.Center).padding(bottom = 64.dp),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            ) {
                for ((monthIndex, month) in months.orEmpty().withIndex()) {
                    item(key = "month ${month.year} ${month.month}") {
                        MonthHeader(month, today, Modifier.padding(top = if (monthIndex == 0) 0.dp else 20.dp))
                    }
                    itemsIndexed(month.workouts, key = { _, summary -> summary.workout.id }) { index, summary ->
                        val isActive = summary.workout.id == workout?.id
                        WorkoutRow(
                            summary,
                            workout.takeIf { isActive },
                            index,
                            month.workouts.size,
                            onOpen = { onOpen(summary) },
                            onDelete = { deleting = summary },
                            kilocalories = kilocalories(summary),
                        )
                    }
                }
            }
        }
    }
    deleting?.let { summary ->
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_question),
            text = stringResource(R.string.delete_workout_named, workoutTitle(summary), Dates.long(summary.workout.date, today)),
            confirm = stringResource(R.string.delete_workout),
            onConfirm = { onDelete(summary.workout.id) },
            onDismiss = { deleting = null },
        )
    }
}

/** "October" in Darker Grotesque 26, and "11 workouts · 9 h 57 min" in Space Mono 12. */
@Composable
private fun MonthHeader(month: HistoryMonth, today: Long, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            Dates.monthTitle(month.year, month.month, today),
            Modifier.weight(1f).alignByBaseline().semantics { heading() },
            color = colors.ink,
            style = KitaeType.sectionTitle,
        )
        if (month.doneCount > 0) {
            val count = pluralStringResource(R.plurals.workout_count, month.doneCount, month.doneCount)
            Text(
                if (month.minutes > 0) count + stringResource(R.string.details_separator) + durationLabel(month.minutes) else count,
                Modifier.alignByBaseline(),
                color = colors.inkSoft,
                style = KitaeType.sectionCaption,
            )
        }
    }
}

/**
 * A workout on Card: the day of the month over its weekday, the day's name, and its length and
 * sets, ".skipped", or ".in progress" for the one going.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutRow(
    summary: WorkoutSummary,
    active: WorkoutState?,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    kilocalories: Double?,
) {
    val colors = kitaeColors
    val shape = cardRowShape(index, count)
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            swipe.reset()
        }
    }
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        gesturesEnabled = active == null,
        backgroundContent = {
            // Only while swiping, so no red shows at the card's round corners.
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().background(colors.error).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                    KamaeIcon(R.drawable.ic_delete, stringResource(R.string.delete), tint = colors.paper)
                }
            }
        },
    ) {
        Column(Modifier.background(colors.card)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(if (active != null) R.string.opens_workout else R.string.shows_sets),
                        role = Role.Button,
                        onClick = onOpen,
                    )
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Dates.dayOfMonth(summary.workout.date), color = colors.ink, style = KitaeType.restCountdown.copy(lineHeight = 26.sp))
                    Text(
                        Dates.shortWeekday(summary.workout.date),
                        color = colors.inkSoft,
                        style = KitaeType.fieldCaption.copy(lineHeight = 12.sp, letterSpacing = 1.sp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(workoutTitle(summary), color = if (summary.isSkipped) colors.inkSoft else colors.ink, fontSize = 16.sp, lineHeight = 22.sp)
                    when {
                        active != null -> Text(
                            pluralStringResource(R.plurals.in_progress_sets, active.progress.setCount, active.progress.doneCount, active.progress.setCount),
                            color = colors.olive,
                            style = KitaeType.sectionCaption.copy(lineHeight = 18.sp),
                            maxLines = 1,
                        )
                        summary.isSkipped -> Text(stringResource(R.string.skipped), color = colors.inkSoft, style = KitaeType.sectionCaption.copy(lineHeight = 18.sp))
                        else -> Text(workoutDetails(summary, kilocalories), color = colors.inkSoft, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1)
                    }
                }
                KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.inkFaint, size = 20.dp)
            }
            if (index < count - 1) Hairline(start = 0.dp)
        }
    }
}
