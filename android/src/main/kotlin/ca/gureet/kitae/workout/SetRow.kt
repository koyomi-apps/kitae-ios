// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.R
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.SetDraft
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.WorkoutSet
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/** A field being typed in: a set's reps or its weight. */
data class FieldKey(val setId: Long, val isWeight: Boolean)

/** A set's values to save, from the set as it's stored. */
fun WorkoutSet.draft(reps: Int = this.reps, weightKg: Double = this.weightKg) =
    SetDraft(reps, weightKg, SetType.fromStored(type), restSeconds)

/**
 * A set as the workout shows it (the iPhone's `SetRow`), at least 60 tall: its label, which opens
 * its options; its reps, or seconds for a hold; its weight, left out for exercises without weight;
 * and its done button. Typing goes straight into the fields and is saved as it's typed; a field
 * left empty goes back to what it was.
 */
@Composable
fun SetRow(
    set: WorkoutSet,
    label: String,
    exercise: Exercise,
    formatter: WeightFormatter,
    focused: FieldKey?,
    onFocus: (FieldKey?) -> Unit,
    onOptions: () -> Unit,
    onChange: (SetDraft) -> Unit,
    onToggleDone: () -> Unit,
) {
    val colors = kitaeColors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val optionsLabel = stringResource(R.string.set_options, label)
        Box(
            Modifier
                .width(52.dp)
                .fillMaxHeight()
                .clickable(onClickLabel = optionsLabel, role = Role.Button, onClick = onOptions)
                .semantics { contentDescription = optionsLabel },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(label, Modifier.padding(start = 2.dp), color = colors.inkSoft, style = KitaeType.setLabel, maxLines = 1)
        }
        val reps = FieldKey(set.id, isWeight = false)
        NumberField(
            caption = stringResource(if (exercise.isIsometric) R.string.field_seconds else R.string.field_reps),
            value = set.reps.toString(),
            keyboardType = KeyboardType.Number,
            isDone = set.isDone,
            isFocused = focused == reps,
            onFocus = { onFocus(if (it) reps else null) },
            parse = { text -> text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 4 }?.toInt() },
            onValue = { value -> if (value != set.reps) onChange(set.draft(reps = value)) },
            modifier = if (exercise.usesWeight) Modifier.width(100.dp) else Modifier.weight(1f),
        )
        if (exercise.usesWeight) {
            val weight = FieldKey(set.id, isWeight = true)
            NumberField(
                caption = stringResource(R.string.field_weight),
                value = formatter.string(set.weightKg),
                unit = formatter.unit.symbol,
                keyboardType = KeyboardType.Decimal,
                isDone = set.isDone,
                isFocused = focused == weight,
                onFocus = { onFocus(if (it) weight else null) },
                parse = formatter::kilograms,
                onValue = { kg -> if (formatter.string(kg) != formatter.string(set.weightKg)) onChange(set.draft(weightKg = kg)) },
                modifier = Modifier.weight(1f),
            )
        }
        val doneLabel = stringResource(if (set.isDone) R.string.mark_set_not_done else R.string.mark_set_done, label)
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = doneLabel, role = Role.Checkbox) {
                    if (!set.isDone) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggleDone()
                }
                .semantics { stateDescription = if (set.isDone) "✓" else "" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(44.dp).background(if (set.isDone) colors.olive else colors.field, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                KamaeIcon(R.drawable.ic_check, doneLabel, tint = if (set.isDone) colors.onOlive else colors.inkFaint, size = 22.dp)
            }
        }
    }
}

/**
 * A number typed in place: Field, or OliveSoft once the set is done, with a 2 dp olive border
 * while typing. [parse] reads what's typed; [onValue] gets each value that can be read.
 */
@Composable
internal fun <T> NumberField(
    caption: String,
    value: String,
    keyboardType: KeyboardType,
    isDone: Boolean,
    isFocused: Boolean,
    onFocus: (Boolean) -> Unit,
    parse: (String) -> T?,
    onValue: (T) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val colors = kitaeColors
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // What's stored changes from elsewhere too, like the steps above the keyboard.
    LaunchedEffect(value) {
        if (parse(text.text) != parse(value)) text = TextFieldValue(value, TextRange(value.length))
    }
    val ink = if (isDone) colors.onOliveSoft else colors.ink
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(56.dp)
            .background(if (isDone) colors.oliveSoft else colors.field, shape)
            .then(if (isFocused) Modifier.border(2.dp, colors.olive, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(caption, color = ink, style = KitaeType.fieldCaption)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { changed ->
                    text = changed
                    parse(changed.text)?.let(onValue)
                },
                modifier = Modifier
                    .weight(1f, fill = unit == null)
                    .then(if (unit != null) Modifier.width(56.dp) else Modifier)
                    .onFocusChanged { state ->
                        onFocus(state.isFocused)
                        if (!state.isFocused && parse(text.text) == null) text = TextFieldValue(value, TextRange(value.length))
                    }
                    .semantics { contentDescription = caption.removePrefix(".") },
                textStyle = KitaeType.typed.copy(color = ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                cursorBrush = SolidColor(colors.olive),
            )
            if (unit != null) Text(unit, Modifier.padding(start = 4.dp), color = ink, style = KitaeType.sectionCaption)
        }
    }
}
