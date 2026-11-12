// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.ExerciseDetails
import ca.gureet.kitae.app.Library
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.ExerciseProgress
import ca.gureet.kitae.database.ExerciseSession
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.motion.ExerciseMotionCard
import ca.gureet.kitae.muscles.MuscleMapCard
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.EmptyState
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.PageTopBar
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.setSummary
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.ui.weightFirst
import kotlin.math.abs
import kotlin.math.roundToInt

/** How many of the exercise's workouts its page lists. */
private const val RECENT_COUNT = 10

/**
 * An exercise's page, as the Kamae canvas draws it (the iPhone's `ExerciseDetailView`): its name
 * and chips, how its heaviest set has gone, its best sets, how to do it, and the workouts it was
 * in. Edit opens the editor.
 */
@Composable
fun ExercisePageScreen(
    details: ExerciseDetails?,
    loaded: Boolean,
    formatter: WeightFormatter,
    defaultRestSeconds: Int,
    today: Long,
    canDelete: Boolean,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
    onSave: suspend (ExerciseDraft, Long) -> Long,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    library: Library? = null,
    note: String? = null,
    onSaveNote: (String) -> Unit = {},
    figureAt: Double? = null,
    bodyLog: BodyLog = BodyLog(),
) {
    val colors = kitaeColors
    var isEditing by remember { mutableStateOf(false) }
    var isWritingNote by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PageTopBar(onBack) {
            if (details != null) BarTextButton(stringResource(R.string.edit), onClick = { isEditing = true })
        }
        if (details == null) {
            if (loaded) {
                Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
                    EmptyState(R.drawable.ic_exercises, stringResource(R.string.exercise_gone), "")
                }
            }
            return@Column
        }
        val exercise = details.exercise
        val progress = details.progress
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                DisplayText(
                    exercise.name,
                    KitaeType.exerciseTitle.copy(lineHeight = 56.sp),
                    colors.olive,
                    Modifier.padding(top = 6.dp).semantics { heading() },
                    minSize = 30.sp,
                    maxLines = 3,
                )
                Row(Modifier.padding(top = 14.dp, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(muscleName(exercise.muscle).takeIf { exercise.muscle != null } ?: exercise.target)
                    val rest = exercise.restSeconds ?: defaultRestSeconds
                    Chip(stringResource(R.string.rest_chip, if (rest > 0) restLabel(rest) else stringResource(R.string.rest_off)))
                    if (exercise.isIsometric) Chip(stringResource(R.string.held))
                }
                NoteRow(note, onClick = { isWritingNote = true }, Modifier.padding(top = 8.dp))
            }
            if (progress.points.size > 1) {
                ProgressCard(progress, formatter, today, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp))
            }
            if (details.history.isNotEmpty()) {
                RecordTiles(details, formatter, bodyLog, Modifier.padding(start = 16.dp, end = 16.dp, top = if (progress.points.size > 1) 12.dp else 24.dp))
            }
            HowTo(details, library, figureAt, Modifier.padding(top = 28.dp))
            Recent(details, formatter, today, onOpen, Modifier.padding(top = 32.dp))
        }
    }
    if (isWritingNote && details != null) {
        ExerciseNoteSheet(details.exercise.name, note, onSave = onSaveNote, onDismiss = { isWritingNote = false })
    }
    if (isEditing && details != null) {
        ExerciseEditorSheet(
            exercise = details.exercise,
            start = ExerciseDraft(details.exercise),
            defaultRestSeconds = defaultRestSeconds,
            canDelete = canDelete,
            onSave = { draft -> onSave(draft, details.exercise.id) },
            onSaved = {},
            onDelete = {
                onDelete(details.exercise.id)
                onBack()
            },
            onDismiss = { isEditing = false },
        )
    }
}

@Composable
private fun Chip(text: String) {
    val colors = kitaeColors
    Box(Modifier.height(32.dp).background(colors.field, CircleShape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = colors.ink, style = KitaeType.smallCaption)
    }
}

/** The measure's name in a caption: ".heaviest set". */
@Composable
private fun measureCaption(measure: ExerciseProgress.Measure): String = stringResource(
    when (measure) {
        ExerciseProgress.Measure.Weight -> R.string.caption_heaviest_set
        ExerciseProgress.Measure.Help -> R.string.caption_least_help
        ExerciseProgress.Measure.Reps -> R.string.caption_most_reps
        ExerciseProgress.Measure.Seconds -> R.string.caption_longest_hold
    },
)

@Composable
private fun measureName(measure: ExerciseProgress.Measure): String = stringResource(
    when (measure) {
        ExerciseProgress.Measure.Weight -> R.string.measure_heaviest_set
        ExerciseProgress.Measure.Help -> R.string.measure_least_help
        ExerciseProgress.Measure.Reps -> R.string.measure_most_reps
        ExerciseProgress.Measure.Seconds -> R.string.measure_longest_hold
    },
)

/** A value in the unit people see: pounds when Settings says so. */
private fun display(value: Double, measure: ExerciseProgress.Measure, formatter: WeightFormatter) =
    if (measure.isWeight) formatter.unit.fromKilograms(value) else value

private fun axisLabel(value: Double, measure: ExerciseProgress.Measure, formatter: WeightFormatter): String = when {
    measure == ExerciseProgress.Measure.Seconds -> "${value.roundToInt()}s"
    value == value.roundToInt().toDouble() -> "${value.roundToInt()}"
    else -> formatter.number(value)
}

/** "August", or "Oct 3" when the chart starts in the month it ends. */
private fun sinceLabel(first: Long, last: Long, today: Long): String {
    val start = java.time.LocalDate.ofEpochDay(first)
    val end = java.time.LocalDate.ofEpochDay(last)
    if (start.year == end.year && start.monthValue == end.monthValue) return Dates.dayAndMonth(first)
    return Dates.monthTitle(start.year, start.monthValue, today)
}

/** "+7.5 kg since August", or on an assisted machine "5 kg less help since August". */
@Composable
private fun changeLabel(progress: ExerciseProgress, formatter: WeightFormatter, today: Long): String {
    val first = progress.points.firstOrNull() ?: return ""
    val last = progress.points.last()
    val since = sinceLabel(first.date, last.date, today)
    val change = display(progress.change, progress.measure, formatter)
    if (abs(change) < 0.01) return stringResource(R.string.no_change_since, since)
    val amount = when (progress.measure) {
        ExerciseProgress.Measure.Weight, ExerciseProgress.Measure.Help -> "${formatter.number(abs(change))} ${formatter.unit.symbol}"
        ExerciseProgress.Measure.Reps -> abs(change).roundToInt().let { pluralStringResource(R.plurals.reps, it, it) }
        ExerciseProgress.Measure.Seconds -> "${abs(change).roundToInt()}s"
    }
    if (progress.measure == ExerciseProgress.Measure.Help) {
        return stringResource(if (change < 0) R.string.less_help_since else R.string.more_help_since, amount, since)
    }
    return stringResource(R.string.change_since, (if (change > 0) "+" else "−") + amount, since)
}

/** The card with the line of the last 12 workouts' best, as the canvas draws it. */
@Composable
private fun ProgressCard(progress: ExerciseProgress, formatter: WeightFormatter, today: Long, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val values = progress.points.map { display(it.value, progress.measure, formatter) }
    val unit = if (progress.measure.isWeight) " ${formatter.unit.symbol}" else ""
    val first = progress.points.first()
    val last = progress.points.last()
    val description = stringResource(
        R.string.chart_description,
        measureName(progress.measure),
        axisLabel(values.first(), progress.measure, formatter) + unit,
        Dates.dayAndMonth(first.date),
        axisLabel(values.last(), progress.measure, formatter) + unit,
        Dates.dayAndMonth(last.date),
    )
    Column(modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(measureCaption(progress.measure), Modifier.weight(1f).alignByBaseline(), color = colors.inkSoft, style = KitaeType.sectionCaption)
            Text(changeLabel(progress, formatter, today), Modifier.alignByBaseline(), color = colors.olive, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        ProgressChart(
            values,
            minimumStep = if (progress.measure.isWeight) 0.5 else 1.0,
            label = { axisLabel(it, progress.measure, formatter) },
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(132.dp).clearAndSetSemantics { contentDescription = description },
        )
        Row(Modifier.padding(top = 6.dp).clearAndSetSemantics {}) {
            Text(Dates.dayAndMonth(first.date), Modifier.weight(1f), color = colors.inkSoft, style = KitaeType.fieldCaption)
            Text(Dates.dayAndMonth(last.date), color = colors.inkSoft, style = KitaeType.fieldCaption)
        }
    }
}

/** Best-set tiles: the heaviest set (or least help) and the most reps, or the most reps and the workouts. */
@Composable
private fun RecordTiles(details: ExerciseDetails, formatter: WeightFormatter, bodyLog: BodyLog, modifier: Modifier = Modifier) {
    val exercise = details.exercise
    val records = details.records
    val isIsometric = exercise.isIsometric
    val mostTitle = stringResource(if (isIsometric) R.string.tile_longest_hold else R.string.tile_most_reps)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (details.progress.measure) {
            ExerciseProgress.Measure.Weight, ExerciseProgress.Measure.Help -> {
                records.best?.let { best ->
                    val isHelp = details.progress.measure == ExerciseProgress.Measure.Help
                    // "1.25 × body weight", against the weight of the day it was lifted, once people weigh in.
                    val body = records.bestDate?.let(bodyLog::weightKg)?.takeIf { !isHelp && best.weightKg > 0 && it > 0 }
                    Tile(
                        stringResource(if (isHelp) R.string.tile_least_help else R.string.tile_heaviest_set),
                        weightFirst(formatter, best.reps, best.weightKg, isIsometric),
                        Modifier.weight(1f),
                        note = body?.let { stringResource(R.string.body_weight_ratio, formatter.number(best.weightKg / it)) },
                    )
                }
                records.mostReps?.let { most ->
                    Tile(mostTitle, setSummary(formatter, most.reps, most.weightKg, isIsometric), Modifier.weight(1f))
                }
            }
            ExerciseProgress.Measure.Reps, ExerciseProgress.Measure.Seconds -> {
                records.mostReps?.let { most ->
                    Tile(mostTitle, setSummary(formatter, most.reps, exercise.weight(most.weightKg), isIsometric), Modifier.weight(1f))
                }
                Tile(stringResource(R.string.tile_workouts), "${records.workoutCount}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    val colors = kitaeColors
    Column(
        modifier
            .heightIn(min = 96.dp)
            .background(colors.card, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = colors.ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        Text(value, color = colors.ink, style = KitaeType.step.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (note != null) Text(note, color = colors.olive, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
    }
}

/**
 * "How to do it": the animation and the muscle map, 16 from the edges like the cards above, then
 * the steps, numbered, and "See it done" for the reference link.
 */
@Composable
private fun HowTo(details: ExerciseDetails, library: Library?, figureAt: Double?, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val uriHandler = LocalUriHandler.current
    val exercise = details.exercise
    val steps = exercise.steps
    val text = Modifier.padding(start = 24.dp, end = 24.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.how_to_do_it),
            Modifier.padding(start = 22.dp, end = 24.dp).semantics { heading() },
            color = colors.ink,
            style = KitaeType.sectionTitle.copy(fontSize = 28.sp, lineHeight = 32.sp),
        )
        if (library != null) {
            val motion = remember(exercise.libraryKey, library) { library.motions.motion(exercise) }
            if (motion != null) {
                val figure = remember(motion, library) { motion.muscles(library.muscles) }
                ExerciseMotionCard(motion, figure, Modifier.padding(horizontal = 16.dp), at = figureAt)
            }
            library.muscles.work(exercise)?.let { work ->
                MuscleMapCard(library.muscles.map, work, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
            }
        }
        if (steps.isEmpty()) Text(stringResource(R.string.no_instructions), text, color = colors.inkSoft, fontSize = 16.sp)
        steps.forEachIndexed { index, step ->
            val label = stringResource(R.string.step_label, index + 1, step)
            Row(text.clearAndSetSemantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(26.dp).background(colors.oliveSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = colors.onOliveSoft, style = KitaeType.smallCaption.copy(fontWeight = FontWeight.Bold))
                }
                Text(step, Modifier.padding(top = 2.dp), color = colors.ink, fontSize = 16.sp, lineHeight = 22.sp)
            }
        }
        exercise.referenceLink?.let { link ->
            Row(
                Modifier.padding(start = 22.dp).clickable(role = Role.Button) { uriHandler.openUri(link) }.heightIn(min = 44.dp).padding(start = 2.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KamaeIcon(R.drawable.ic_start, null, tint = colors.olive, size = 20.dp)
                Text(stringResource(R.string.see_it_done), color = colors.olive, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** ".recent workouts": the last ten with their sets, each opening its page. */
@Composable
private fun Recent(details: ExerciseDetails, formatter: WeightFormatter, today: Long, onOpen: (Route) -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Column(modifier.fillMaxWidth()) {
        Caption(stringResource(R.string.caption_recent_workouts), Modifier.padding(start = 24.dp, bottom = 8.dp))
        if (details.history.isEmpty()) {
            Text(stringResource(R.string.no_recent_workouts), Modifier.padding(horizontal = 24.dp), color = colors.inkSoft, fontSize = 16.sp)
            return@Column
        }
        val sessions = details.history.take(RECENT_COUNT)
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp))) {
            sessions.forEachIndexed { index, session ->
                if (index > 0) Hairline(start = 16.dp)
                RecentRow(session, details, formatter, today, onClick = { onOpen(Route.PastWorkout(session.id)) })
            }
        }
    }
}

@Composable
private fun RecentRow(session: ExerciseSession, details: ExerciseDetails, formatter: WeightFormatter, today: Long, onClick: () -> Unit) {
    val colors = kitaeColors
    val exercise = details.exercise
    val date = Dates.short(session.date, today)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.dayName?.let { "$date · $it" } ?: date, color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                session.sets.map { setSummary(formatter, it.reps, exercise.weight(it.weightKg), exercise.isIsometric) }.joinToString(", "),
                color = colors.inkSoft,
                style = KitaeType.sectionCaption,
                maxLines = 2,
            )
        }
        KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.inkFaint, size = 20.dp)
    }
}
