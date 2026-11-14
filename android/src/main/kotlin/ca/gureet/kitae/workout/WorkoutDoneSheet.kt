// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.Library
import ca.gureet.kitae.app.WorkoutDone
import ca.gureet.kitae.database.NewBest
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.activeKilocalories
import ca.gureet.kitae.model.roundedKilocalories
import ca.gureet.kitae.muscles.MuscleVolume
import ca.gureet.kitae.muscles.MuscleVolumeCard
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.MauveButton
import ca.gureet.kitae.ui.setSummary
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.ui.weightFirst
import ca.gureet.kitae.ui.workoutTitle
import kotlinx.coroutines.launch

/**
 * After a workout (the iPhone's `WorkoutDoneView`): its day, how long it took, its sets, the
 * energy it used, the records it beat (unless Settings leave them out) and the muscles it worked,
 * in a full-height sheet. [done] is null until it's read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDoneSheet(
    done: WorkoutDone?,
    bodyLog: BodyLog,
    library: Library?,
    formatter: WeightFormatter,
    today: Long,
    onAddBody: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun close(then: () -> Unit) {
        scope.launch {
            sheet.hide()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.paper) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            if (done != null) {
                val kilocalories = bodyLog.activeKilocalories(done.summary, done.sets)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
                        Text(stringResource(R.string.caption_workout_done), color = colors.inkSoft, style = KitaeType.caption)
                        DisplayText(
                            workoutTitle(done.summary),
                            KitaeType.daySummary,
                            colors.olive,
                            Modifier.padding(top = 2.dp).semantics { heading() },
                            minSize = 32.sp,
                            maxLines = 2,
                        )
                        Text(Dates.long(done.summary.workout.date, today), Modifier.padding(top = 6.dp), color = colors.inkSoft, style = KitaeType.caption)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val minutes = done.summary.minutes
                        DoneTile(
                            stringResource(R.string.tile_time),
                            when {
                                minutes == null -> "–"
                                minutes < 60 -> "$minutes"
                                else -> stringResource(R.string.hours_and_minutes, minutes / 60, minutes % 60)
                            },
                            Modifier.weight(1f),
                            unit = if (minutes != null) stringResource(R.string.unit_minutes) else null,
                        )
                        DoneTile(stringResource(R.string.tile_sets), "${done.summary.doneCount}/${done.summary.setCount}", Modifier.weight(1f))
                        if (kilocalories != null) {
                            val rounded = roundedKilocalories(kilocalories)
                            val spoken = stringResource(R.string.energy_spoken, rounded)
                            DoneTile(
                                stringResource(R.string.tile_energy),
                                stringResource(R.string.kcal_about_short, rounded),
                                Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
                                unit = stringResource(R.string.unit_kcal),
                            )
                        } else {
                            DoneTile(
                                stringResource(R.string.tile_energy),
                                "–",
                                Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.open_body), role = Role.Button) { close(onAddBody) },
                                note = stringResource(R.string.add_your_weight),
                            )
                        }
                    }
                    if (kilocalories != null) EnergyNote(hasHeight = bodyLog.profile.heightCm != null, onAddHeight = { close(onAddBody) })
                    if (done.bests.isNotEmpty()) NewBestsCard(done.bests, formatter)
                    MuscleVolumeCard(
                        stringResource(R.string.this_workout),
                        MuscleVolume.of(done.sets, library?.muscles),
                        library?.muscles?.map,
                        formatter,
                    )
                }
            }
            // Done, over the end of the list as it fades into Paper.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(colors.paper.copy(alpha = 0f), colors.paper.copy(alpha = 0.86f), colors.paper)))
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 20.dp),
            ) {
                MauveButton(onClick = { close(onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.done), style = KitaeType.bigButton)
                }
            }
        }
    }
}

/** One of the summary's figures, Time, Sets and Energy, as You's tiles: its name over a Space Mono value. */
@Composable
private fun DoneTile(title: String, value: String, modifier: Modifier = Modifier, unit: String? = null, note: String? = null) {
    val colors = kitaeColors
    Column(
        modifier
            .heightIn(min = 96.dp)
            .background(colors.card, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = colors.ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        // Smaller rather than cut off, as the iPhone's tiles shrink to fit.
        Column {
            BasicText(
                buildAnnotatedString {
                    append(value)
                    if (unit != null) withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = colors.inkSoft)) { append(" $unit") }
                },
                style = KitaeType.tileNumber.copy(fontSize = 24.sp, lineHeight = 30.sp, color = colors.ink),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp, stepSize = 1.sp),
            )
            if (note != null) {
                BasicText(
                    note,
                    style = TextStyle(color = colors.olive, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
                )
            }
        }
    }
}

/** Under the figures, once there's an energy figure: what it's worked out from, and that height makes it closer. */
@Composable
private fun EnergyNote(hasHeight: Boolean, onAddHeight: () -> Unit) {
    val colors = kitaeColors
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(
            stringResource(if (hasHeight) R.string.energy_note_height else R.string.energy_note_no_height),
            color = colors.inkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (!hasHeight) {
            Row(
                Modifier
                    .clickable(onClickLabel = stringResource(R.string.open_body), role = Role.Button, onClick = onAddHeight)
                    .heightIn(min = 44.dp)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.add_your_height), color = colors.olive, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.olive, size = 16.dp)
            }
        }
    }
}

/** The records the workout's sets beat, each with the best it beat. */
@Composable
private fun NewBestsCard(bests: List<NewBest>, formatter: WeightFormatter) {
    val colors = kitaeColors
    Column(
        Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp)).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.new_bests), Modifier.weight(1f).alignByBaseline().semantics { heading() }, color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(pluralStringResource(R.plurals.records, bests.size, bests.size), Modifier.alignByBaseline(), color = colors.inkSoft, style = KitaeType.sectionCaption)
        }
        Column(Modifier.padding(top = 8.dp)) {
            for (best in bests) {
                val record = stringResource(
                    when (best.record) {
                        NewBest.Record.Heaviest -> R.string.tile_heaviest_set
                        NewBest.Record.LeastHelp -> R.string.tile_least_help
                        NewBest.Record.MostReps -> R.string.tile_most_reps
                        NewBest.Record.LongestHold -> R.string.tile_longest_hold
                    },
                )
                Row(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KamaeIcon(R.drawable.ic_star, null, Modifier.padding(top = 2.dp), tint = colors.olive, size = 18.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(best.exercise.name, Modifier.weight(1f).alignByBaseline(), color = colors.ink, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                            Text(bestValue(best.set, best, formatter), Modifier.alignByBaseline(), color = colors.ink, style = KitaeType.step.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                        Text(
                            stringResource(R.string.best_was, record, bestValue(best.previous, best, formatter)),
                            color = colors.inkSoft,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

/** As the exercise's page shows its records: weight first for the heaviest set. */
@Composable
private fun bestValue(set: SetDraft, best: NewBest, formatter: WeightFormatter): String {
    val exercise = best.exercise
    val kilograms = exercise.weight(set.weightKg)
    return when (best.record) {
        NewBest.Record.Heaviest, NewBest.Record.LeastHelp -> weightFirst(formatter, set.reps, kilograms, exercise.isIsometric)
        NewBest.Record.MostReps, NewBest.Record.LongestHold -> setSummary(formatter, set.reps, kilograms, exercise.isIsometric)
    }
}
