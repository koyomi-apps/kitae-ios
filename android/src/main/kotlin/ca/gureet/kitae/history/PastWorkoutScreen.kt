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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.app.WorkoutRecord
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.setLabels
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.activeKilocalories
import ca.gureet.kitae.model.doneSets
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.EmptyState
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.PageTopBar
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.setSummary
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.ui.workoutDetails
import ca.gureet.kitae.ui.workoutTitle

/**
 * A past workout as it was saved (the iPhone's `WorkoutDetailView`): its name, date and length,
 * then each exercise's sets, with the ones not done marked ".skipped". At the top, the workout of
 * the same day before it, and a menu to edit the day or delete the workout.
 */
@Composable
fun PastWorkoutScreen(
    record: WorkoutRecord?,
    loaded: Boolean,
    formatter: WeightFormatter,
    today: Long,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bodyLog: BodyLog = BodyLog(),
) {
    val colors = kitaeColors
    var menuOpen by remember { mutableStateOf(false) }
    var confirmsDelete by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PageTopBar(onBack) {
            if (record != null) {
                val title = workoutTitle(record.summary)
                record.previousId?.let { previous ->
                    IconButton(onClick = { onOpen(Route.PastWorkout(previous)) }) {
                        KamaeIcon(R.drawable.ic_history, stringResource(R.string.previous_workout, title))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { KamaeIcon(R.drawable.ic_more, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val planId = record.summary.workout.planId
                        val dayId = record.summary.workout.dayId
                        if (planId != null && dayId != null && record.summary.dayName != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_named, title)) },
                                leadingIcon = { KamaeIcon(R.drawable.ic_edit, null) },
                                onClick = {
                                    menuOpen = false
                                    onOpen(Route.PlanEditor(planId, dayId))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_workout), color = colors.error) },
                            leadingIcon = { KamaeIcon(R.drawable.ic_delete, null, tint = colors.error) },
                            onClick = {
                                menuOpen = false
                                confirmsDelete = true
                            },
                        )
                    }
                }
            }
        }
        when {
            record != null -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") { Header(record, today, bodyLog) }
                if (record.exercises.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.no_sets_saved), Modifier.padding(horizontal = 8.dp), color = colors.inkSoft, fontSize = 15.sp)
                    }
                }
                items(record.exercises, key = { it.exercise.id }) { exercise ->
                    ExerciseCard(exercise, formatter, onOpen = { onOpen(Route.ExercisePage(exercise.exercise.id)) })
                }
            }
            loaded -> Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
                EmptyState(R.drawable.ic_history, stringResource(R.string.workout_deleted), "")
            }
        }
    }
    if (confirmsDelete && record != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_question),
            text = stringResource(R.string.delete_workout_text),
            confirm = stringResource(R.string.delete_workout),
            onConfirm = {
                onDelete(record.summary.workout.id)
                onBack()
            },
            onDismiss = { confirmsDelete = false },
        )
    }
}

@Composable
private fun Header(record: WorkoutRecord, today: Long, bodyLog: BodyLog) {
    val colors = kitaeColors
    val summary = record.summary
    val kilocalories = bodyLog.activeKilocalories(summary, record.exercises.flatMap { it.doneSets })
    Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp)) {
        DisplayText(
            workoutTitle(summary),
            KitaeType.pageTitle,
            if (summary.isSkipped) colors.inkSoft else colors.ink,
            Modifier.semantics { heading() },
            minSize = 24.sp,
            maxLines = 2,
        )
        Caption(Dates.long(summary.workout.date, today), Modifier.padding(top = 2.dp))
        Caption(if (summary.isSkipped) stringResource(R.string.skipped) else workoutDetails(summary, kilocalories))
    }
}

/** An exercise's sets on Card, its name opening its page. */
@Composable
private fun ExerciseCard(item: WorkoutExercise, formatter: WeightFormatter, onOpen: () -> Unit) {
    val colors = kitaeColors
    val labels = setLabels(item.sets.map { SetType.fromStored(it.type) })
    Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp))) {
        ExerciseHeader(item.exercise, onOpen)
        item.sets.forEachIndexed { index, set ->
            Hairline(start = 44.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 44.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels[index], Modifier.width(28.dp), color = colors.inkSoft, style = KitaeType.step.copy(fontWeight = FontWeight.Bold))
                Text(
                    setSummary(formatter, set.reps, item.exercise.weight(set.weightKg), item.exercise.isIsometric),
                    Modifier.weight(1f),
                    color = if (set.isDone) colors.ink else colors.inkSoft,
                    style = KitaeType.step,
                )
                if (!set.isDone) Text(stringResource(R.string.skipped), color = colors.inkSoft, style = KitaeType.sectionCaption)
            }
        }
    }
}

/** An exercise's name and muscle, opening its page; shared with the plan editor's cards. */
@Composable
fun ExerciseHeader(exercise: Exercise, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClickLabel = stringResource(R.string.shows_exercise), role = Role.Button, onClick = onOpen) else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(exercise.name, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(muscleName(exercise.muscle).takeIf { exercise.muscle != null } ?: exercise.target, color = colors.inkSoft, fontSize = 13.sp)
    }
}
