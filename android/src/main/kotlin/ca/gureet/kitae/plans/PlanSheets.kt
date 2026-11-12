// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ca.gureet.kitae.R
import ca.gureet.kitae.database.DayExerciseWithSets
import ca.gureet.kitae.database.NameProblem
import ca.gureet.kitae.database.NameProblemException
import ca.gureet.kitae.database.PlannedSet
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.setLabels
import ca.gureet.kitae.exercises.nameProblemText
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.DayChip
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.workout.DeleteSetRow
import ca.gureet.kitae.workout.NumberField
import ca.gureet.kitae.workout.RestRow
import ca.gureet.kitae.workout.SetTypeChoices
import ca.gureet.kitae.workout.StepButton
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A planned set's values to save. */
fun PlannedSet.draft() = SetDraft(reps, weightKg, setType, restSeconds)

/**
 * A planned set's sheet, from the plan editor (the iPhone's `PlannedSetSheet`): its reps and
 * weight, typed in place with the steps above the keyboard, its type, its rest, and Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedSetSheet(
    item: DayExerciseWithSets,
    set: PlannedSet,
    formatter: WeightFormatter,
    defaultRestSeconds: Int,
    onChange: (SetDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = kitaeColors
    val focusManager = LocalFocusManager.current
    val exercise = item.exercise
    val draft = set.draft()
    val label = setLabels(item.sets.map { it.setType }).getOrNull(item.sets.indexOfFirst { it.id == set.id }).orEmpty()
    var focused by remember { mutableStateOf<Boolean?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(exercise.name, color = colors.olive, style = KitaeType.sectionTitle)
                    Caption(stringResource(R.string.set_caption, label), style = KitaeType.smallCaption)
                }
                Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        stringResource(if (exercise.isIsometric) R.string.field_seconds else R.string.field_reps),
                        set.reps.toString(),
                        KeyboardType.Number,
                        isDone = false,
                        isFocused = focused == false,
                        onFocus = { if (it) focused = false else if (focused == false) focused = null },
                        parse = { text -> text.toIntOrNull()?.takeIf { it >= 0 } },
                        onValue = { reps -> if (reps != set.reps) onChange(draft.copy(reps = reps)) },
                        modifier = Modifier.weight(1f),
                    )
                    if (exercise.usesWeight) {
                        NumberField(
                            stringResource(R.string.field_weight),
                            formatter.string(set.weightKg),
                            KeyboardType.Decimal,
                            isDone = false,
                            isFocused = focused == true,
                            onFocus = { if (it) focused = true else if (focused == true) focused = null },
                            parse = { text -> formatter.kilograms(text)?.takeIf { it >= 0 } },
                            onValue = { kg -> if (kotlin.math.abs(kg - set.weightKg) >= 0.0001) onChange(draft.copy(weightKg = kg)) },
                            modifier = Modifier.weight(1f),
                            unit = formatter.unit.symbol,
                        )
                    }
                }
                SetTypeChoices(label, set.setType) { onChange(draft.copy(type = it)) }
                Hairline(Modifier.padding(vertical = 4.dp, horizontal = 24.dp), start = 0.dp)
                RestRow(set.restSeconds, exercise.restSeconds ?: defaultRestSeconds) { onChange(draft.copy(restSeconds = it)) }
                Hairline(Modifier.padding(vertical = 4.dp, horizontal = 24.dp), start = 0.dp)
                DeleteSetRow {
                    onDismiss()
                    onDelete()
                }
            }
            val isWeight = focused
            if (isWeight != null) {
                Hairline(start = 0.dp)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isWeight) {
                        for (step in formatter.unit.steps) {
                            StepButton(formatter.step(step)) {
                                val shown = maxOf(formatter.unit.fromKilograms(set.weightKg) + step, 0.0)
                                onChange(draft.copy(weightKg = formatter.unit.toKilograms(shown)))
                            }
                        }
                    } else {
                        StepButton("−1") { onChange(draft.copy(reps = maxOf(set.reps - 1, 0))) }
                        StepButton("+1") { onChange(draft.copy(reps = set.reps + 1)) }
                    }
                    Box(Modifier.weight(1f))
                    Surface(onClick = { focusManager.clearFocus() }, shape = CircleShape, color = colors.olive, contentColor = colors.onOlive) {
                        Text(stringResource(R.string.keyboard_done), Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

/**
 * Asks for a name (the iPhone's `NameSheet`): for a new plan or day, or a new name for one. A
 * suggestion fills it in. [onSave] throws a `NameProblemException` for a name that can't be used,
 * shown under the field; otherwise the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NameSheet(
    title: String,
    placeholder: String,
    action: String,
    onSave: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    suggestions: List<String> = emptyList(),
) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var name by rememberSaveable { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<NameProblem?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun submit() {
        if (name.isBlank()) return
        scope.launch {
            try {
                onSave(name)
                sheet.hide()
                onDismiss()
            } catch (nameProblem: NameProblemException) {
                problem = nameProblem.problem
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.paper) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { KamaeIcon(R.drawable.ic_close, stringResource(R.string.cancel)) }
                Text(title, Modifier.weight(1f), color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                BarTextButton(action, ::submit, enabled = name.isNotBlank())
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(colors.field, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) Text(placeholder, color = colors.inkFaint, fontSize = 20.sp)
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        problem = null
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
                    textStyle = TextStyle(color = colors.ink, fontSize = 20.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    cursorBrush = SolidColor(colors.olive),
                )
            }
            problem?.let { Text(nameProblemText(it), Modifier.padding(horizontal = 24.dp), color = colors.error, fontSize = 13.sp) }
            if (suggestions.isNotEmpty()) {
                LazyRow(
                    Modifier.padding(top = 6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(suggestions) { suggestion -> DayChip(suggestion, name == suggestion, onClick = { name = suggestion }) }
                }
            }
        }
        // Inside the sheet, so the field is there by the time it's asked to take the keyboard.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/**
 * The day's exercises with handles, to drag them into another order (the iPhone's
 * `ReorderExercisesSheet`). Each row also offers Move up and Move down to accessibility services.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSheet(dayName: String, exercises: List<DayExerciseWithSets>, onOrder: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val colors = kitaeColors
    val order = remember(exercises) { mutableStateListOf(*exercises.toTypedArray()) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    fun commit() {
        val ids = order.map { it.dayExercise.id }
        if (ids != exercises.map { it.dayExercise.id }) onOrder(ids)
    }
    fun move(from: Int, to: Int) {
        if (to !in order.indices || from == to) return
        order.add(to, order.removeAt(from))
    }
    ModalBottomSheet(
        onDismissRequest = {
            commit()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.order_of, dayName), Modifier.weight(1f), color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                BarTextButton(stringResource(R.string.done), {
                    commit()
                    onDismiss()
                })
            }
            Column(Modifier.padding(horizontal = 16.dp).background(colors.card, RoundedCornerShape(24.dp))) {
                order.forEachIndexed { index, item ->
                    key(item.dayExercise.id) {
                        val id = item.dayExercise.id
                        val isDragged = dragging == id
                        Column(
                            Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .offset { IntOffset(0, if (isDragged) offset.roundToInt() else 0) }
                                .background(if (isDragged) colors.field else colors.card, RoundedCornerShape(if (isDragged) 16.dp else 0.dp))
                                .semantics(mergeDescendants = true) {
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUp) {
                                            move(index, index - 1)
                                            commit()
                                            true
                                        },
                                        CustomAccessibilityAction(moveDown) {
                                            move(index, index + 1)
                                            commit()
                                            true
                                        },
                                    )
                                },
                        ) {
                            if (index > 0 && !isDragged) Hairline()
                            Row(
                                Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.exercise.name, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
                                Text("${item.sets.size}", color = colors.inkSoft, style = KitaeType.smallCaption)
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .pointerInput(id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragging = id
                                                    offset = 0f
                                                },
                                                onDragEnd = {
                                                    dragging = null
                                                    offset = 0f
                                                    commit()
                                                },
                                                onDragCancel = {
                                                    dragging = null
                                                    offset = 0f
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                offset += amount.y
                                                val from = order.indexOfFirst { it.dayExercise.id == id }
                                                if (offset > rowHeight / 2 && from < order.lastIndex) {
                                                    move(from, from + 1)
                                                    offset -= rowHeight
                                                } else if (offset < -rowHeight / 2 && from > 0) {
                                                    move(from, from - 1)
                                                    offset += rowHeight
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    KamaeIcon(R.drawable.ic_drag, null, tint = colors.inkSoft)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
