// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.Library
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.app.WorkoutState
import ca.gureet.kitae.database.EpochDay
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.setLabels
import ca.gureet.kitae.exercises.ExerciseNoteSheet
import ca.gureet.kitae.exercises.NoteRow
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.muscles.MuscleLine
import ca.gureet.kitae.muscles.MuscleWork
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.MauveButton
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import java.text.DecimalFormatSymbols
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone

/** What the workout's screen does with the workout. */
interface WorkoutActions {
    fun minimize()
    fun discard()
    fun finish(mode: FinishMode, addNewSets: Boolean)
    fun toggleDone(setId: Long)
    fun updateSet(setId: Long, draft: SetDraft)
    fun addSet(exerciseId: Long)
    fun deleteSet(setId: Long)
    fun pauseRest()
    fun resumeRest()
    fun addRest(seconds: Int)
    fun skipRest()

    /** Saves an exercise's note; an empty one deletes it. */
    fun saveNote(exerciseId: Long, note: String)
}

/** The phone's decimal separator, which Settings › Decimals › Auto follows. */
fun phoneDecimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator

/** The time now, ticking every second, unless [frozen] gives one, as screenshots do. */
@Composable
fun rememberNow(frozen: Long? = null): Long {
    var now by remember { mutableLongStateOf(frozen ?: System.currentTimeMillis()) }
    if (frozen == null) {
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000 - now % 1000)
            }
        }
    }
    return frozen ?: now
}

/** When the workout started, or the start of its day for workouts from before times were kept. */
val WorkoutState.startedAt: Long
    get() = workout.startedAt ?: EpochDay.start(workout.date, TimeZone.currentSystemDefault()).toEpochMilliseconds()

/**
 * The workout, over the tabs (the iPhone's `WorkoutView`): its exercises and sets, typed in place,
 * with the rest timer and Finish at the bottom, or the steps above the keyboard while typing.
 */
@Composable
fun WorkoutScreen(
    workout: WorkoutState,
    settings: AppSettings,
    actions: WorkoutActions,
    restsFinished: Int = 0,
    frozenNow: Long? = null,
    onOpen: (Route) -> Unit = {},
    library: Library? = null,
    notes: Map<Long, String> = emptyMap(),
) {
    val colors = kitaeColors
    var noteFor by remember { mutableStateOf<Exercise?>(null) }
    val now = rememberNow(frozenNow)
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var focused by remember { mutableStateOf<FieldKey?>(null) }
    var optionsFor by remember { mutableStateOf<Long?>(null) }
    var question by remember { mutableStateOf<FinishQuestion?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val formatter = remember(settings.unit, settings.decimals) { WeightFormatter(settings.unit, settings.decimals, phoneDecimalSeparator()) }
    val list = rememberLazyListState()
    val dayName = workout.dayName ?: stringResource(R.string.workout)

    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focusManager.clearFocus() }
    val finishedBefore = remember { restsFinished }
    LaunchedEffect(restsFinished) { if (restsFinished > finishedBefore) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    BackHandler(enabled = focused == null) { actions.minimize() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        val narrow = maxWidth < 380.dp
        Column(Modifier.fillMaxSize()) {
            Header(
                workout,
                dayName,
                now,
                typing = focused != null,
                onMinimize = actions::minimize,
                onDiscard = { confirmDiscard = true },
                onEdit = { planId, dayId -> onOpen(Route.PlanEditor(planId, dayId)) },
            )
            LazyColumn(
                Modifier.fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(workout.exercises, key = { it.exercise.id }) { exercise ->
                    ExerciseSection(
                        exercise,
                        formatter,
                        focused,
                        onFocus = { focused = it },
                        onOptions = { optionsFor = it },
                        onOpen = { onOpen(Route.ExercisePage(exercise.exercise.id)) },
                        actions,
                        Modifier.widthIn(max = 600.dp),
                        work = library?.muscles?.work(exercise.exercise),
                        note = notes[exercise.exercise.id],
                        onNote = {
                            focusManager.clearFocus()
                            noteFor = exercise.exercise
                        },
                    )
                }
            }
        }
        if (focused == null) {
            BottomBar(workout, now, narrow, actions, onFinish = {
                question = FinishQuestion.first(workout.progress, startingAnotherDay = false)
                if (question == null) actions.finish(FinishMode.KeepSkipped, false)
            })
        } else {
            KeyboardBar(workout, focused!!, formatter, actions, onDone = { focusManager.clearFocus() })
        }
    }

    val options = optionsFor?.let { id -> workout.exercises.firstNotNullOfOrNull { exercise -> exercise.sets.firstOrNull { it.id == id }?.let { exercise to it } } }
    if (options != null) {
        val (exercise, set) = options
        SetOptionsSheet(
            exercise,
            set,
            settings.defaultRestSeconds,
            onType = { type -> actions.updateSet(set.id, set.draft().copy(type = type)) },
            onRest = { seconds -> actions.updateSet(set.id, set.draft().copy(restSeconds = seconds)) },
            onDelete = {
                optionsFor = null
                actions.deleteSet(set.id)
            },
            onDismiss = { optionsFor = null },
        )
    }
    noteFor?.let { exercise ->
        ExerciseNoteSheet(exercise.name, notes[exercise.id], onSave = { actions.saveNote(exercise.id, it) }, onDismiss = { noteFor = null })
    }
    question?.let { asked ->
        FinishQuestionDialog(asked, dayName, workout.progress.addedDoneCount, onQuestion = { question = it }, onFinish = actions::finish)
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_workout_question)) },
            text = { Text(stringResource(R.string.discard_workout_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.discard()
                }) { Text(stringResource(R.string.discard), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = colors.field,
        )
    }
}

@Composable
private fun Header(
    workout: WorkoutState,
    dayName: String,
    now: Long,
    typing: Boolean,
    onMinimize: () -> Unit,
    onDiscard: () -> Unit,
    onEdit: (planId: Long, dayId: Long) -> Unit,
) {
    val colors = kitaeColors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (typing) colors.card else colors.paper)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(100.dp)) {
            IconButton(onClick = onMinimize) { KamaeIcon(R.drawable.ic_chevron_down, stringResource(R.string.minimize_workout), tint = colors.inkSoft) }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayName,
                Modifier.semantics { heading() },
                color = colors.ink,
                style = KitaeType.workoutTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            val progress = workout.progress
            Text(
                pluralStringResource(R.plurals.workout_progress, progress.setCount, clockText(now - workout.startedAt), progress.doneCount, progress.setCount),
                color = colors.inkSoft,
                style = KitaeType.sectionCaption,
                maxLines = 1,
            )
        }
        Row(Modifier.width(100.dp), horizontalArrangement = Arrangement.End) {
            val planId = workout.workout.planId
            val dayId = workout.dayId
            if (planId != null && dayId != null && workout.dayName != null) {
                IconButton(onClick = { onEdit(planId, dayId) }) {
                    KamaeIcon(R.drawable.ic_edit, stringResource(R.string.edit_named, dayName), tint = colors.inkSoft)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { KamaeIcon(R.drawable.ic_more, stringResource(R.string.more_options), tint = colors.inkSoft) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.discard_workout), color = colors.error) },
                        leadingIcon = { KamaeIcon(R.drawable.ic_delete, null, tint = colors.error) },
                        onClick = {
                            menuOpen = false
                            onDiscard()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseSection(
    exercise: WorkoutExercise,
    formatter: WeightFormatter,
    focused: FieldKey?,
    onFocus: (FieldKey?) -> Unit,
    onOptions: (Long) -> Unit,
    onOpen: () -> Unit,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
    work: MuscleWork? = null,
    note: String? = null,
    onNote: () -> Unit = {},
) {
    val colors = kitaeColors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // The name, its muscles under it, then its note, which opens to change it.
            Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(R.string.shows_exercise), role = Role.Button, onClick = onOpen)
                        .padding(start = 4.dp, top = 2.dp, end = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        exercise.exercise.name,
                        Modifier.weight(1f, fill = false).semantics { heading() },
                        color = colors.olive,
                        style = KitaeType.sectionTitle,
                    )
                    KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.inkFaint, size = 16.dp)
                }
                if (work != null) MuscleLine(work, Modifier.padding(start = 4.dp, end = 8.dp))
                if (note != null) NoteRow(note, onClick = onNote, compact = true)
            }
            val addLabel = stringResource(R.string.add_set_to, exercise.exercise.name)
            IconButton(onClick = { actions.addSet(exercise.exercise.id) }, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(36.dp).background(colors.oliveSoft, CircleShape), contentAlignment = Alignment.Center) {
                    KamaeIcon(R.drawable.ic_add, addLabel, tint = colors.onOliveSoft, size = 20.dp)
                }
            }
        }
        val labels = setLabels(exercise.sets.map { SetType.fromStored(it.type) })
        exercise.sets.forEachIndexed { index, set ->
            SetRow(
                set,
                labels[index],
                exercise.exercise,
                formatter,
                focused,
                onFocus,
                onOptions = { onOptions(set.id) },
                onChange = { actions.updateSet(set.id, it) },
                onToggleDone = { actions.toggleDone(set.id) },
            )
        }
    }
}

@Composable
private fun BoxScope.BottomBar(
    workout: WorkoutState,
    now: Long,
    narrow: Boolean,
    actions: WorkoutActions,
    onFinish: () -> Unit,
) {
    val colors = kitaeColors
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to colors.paper.copy(alpha = 0f), 0.5f to colors.paper.copy(alpha = 0.86f), 1f to colors.paper))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val rest = workout.rest
        Row(Modifier.widthIn(max = 600.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rest.isActive) {
                RestBar(rest, now, actions::pauseRest, actions::resumeRest, actions::addRest, actions::skipRest, Modifier.weight(1f))
                FinishButton(onFinish, Modifier.width(if (narrow) 58.dp else 96.dp), showsText = false)
            } else {
                FinishButton(onFinish, Modifier.weight(1f), showsText = true)
            }
        }
    }
}

@Composable
private fun FinishButton(onClick: () -> Unit, modifier: Modifier, showsText: Boolean) {
    val colors = kitaeColors
    MauveButton(onClick = onClick, modifier = modifier) {
        KamaeIcon(R.drawable.ic_check, if (showsText) null else stringResource(R.string.finish_workout), tint = colors.onMauve)
        if (showsText) Text(stringResource(R.string.finish), style = KitaeType.bigButton.copy(fontSize = KitaeType.bigButton.fontSize * 16 / 17))
    }
}

/** Above the keyboard: −5, −2.5, +2.5 and +5 for a weight, −1 and +1 for reps, and Done. */
@Composable
private fun BoxScope.KeyboardBar(
    workout: WorkoutState,
    focused: FieldKey,
    formatter: WeightFormatter,
    actions: WorkoutActions,
    onDone: () -> Unit,
) {
    val colors = kitaeColors
    val set = workout.exercises.flatMap { it.sets }.firstOrNull { it.id == focused.setId }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
        Hairline(start = 0.dp)
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(colors.card).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (set != null) {
                if (focused.isWeight) {
                    for (step in listOf(-5.0, -2.5, 2.5, 5.0)) {
                        StepButton(formatter.step(step)) {
                            val shown = maxOf(formatter.unit.fromKilograms(set.weightKg) + step, 0.0)
                            actions.updateSet(set.id, set.draft(weightKg = formatter.unit.toKilograms(shown)))
                        }
                    }
                } else {
                    for (step in listOf(-1, 1)) {
                        StepButton(if (step < 0) "−1" else "+1") { actions.updateSet(set.id, set.draft(reps = maxOf(set.reps + step, 0))) }
                    }
                }
            }
            Box(Modifier.weight(1f))
            Surface(onClick = onDone, shape = CircleShape, color = colors.olive, contentColor = colors.onOlive) {
                Text(stringResource(R.string.keyboard_done), Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
internal fun StepButton(text: String, onClick: () -> Unit) {
    val colors = kitaeColors
    Surface(onClick = onClick, shape = CircleShape, color = colors.field, contentColor = colors.ink) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = KitaeType.step)
    }
}
