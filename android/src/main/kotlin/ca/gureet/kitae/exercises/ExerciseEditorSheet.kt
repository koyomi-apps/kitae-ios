// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.muscleOrder
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.Exercises
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.database.NameProblem
import ca.gureet.kitae.database.NameProblemException
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.workout.restChoices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Why a name can't be used, in the iPhone's words. */
@Composable
fun nameProblemText(problem: NameProblem): String = stringResource(
    when (problem) {
        NameProblem.Empty -> R.string.name_empty
        NameProblem.Taken -> R.string.plan_name_taken
        NameProblem.ExerciseTaken -> R.string.exercise_name_taken
    },
)

/** "Default, 1:30", or "Default, Off" when rests are off. */
@Composable
fun defaultRestText(seconds: Int): String =
    stringResource(R.string.rest_default, if (seconds > 0) restLabel(seconds) else stringResource(R.string.rest_off))

/**
 * Makes a new exercise, or changes [exercise] (the iPhone's `ExerciseEditor`): its name, muscle,
 * whether it's held or uses weight, its rest, how to do it and a link to see it done; and deleting
 * it, after asking, unless it's in the workout going. [onSave] throws a `NameProblemException`
 * for a name that can't be used, shown under the fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorSheet(
    exercise: Exercise?,
    start: ExerciseDraft,
    defaultRestSeconds: Int,
    canDelete: Boolean,
    onSave: suspend (ExerciseDraft) -> Long,
    onSaved: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(start.name) }
    var muscle by rememberSaveable { mutableStateOf(start.muscle) }
    var isIsometric by rememberSaveable { mutableStateOf(start.isIsometric) }
    var usesWeight by rememberSaveable { mutableStateOf(start.usesWeight) }
    var restSeconds by rememberSaveable { mutableStateOf(start.restSeconds) }
    var instructions by rememberSaveable { mutableStateOf(start.instructions) }
    var reference by rememberSaveable { mutableStateOf(start.reference) }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmsDelete by remember { mutableStateOf(false) }
    val savedReference = Exercises.savedReference(reference)
    val isReferenceValid = savedReference == null || Exercise(name = "", target = "", reference = savedReference).referenceLink != null
    val canSave = name.isNotBlank() && isReferenceValid
    val couldntSave = stringResource(R.string.couldnt_save, "%s")
    val nameProblems = NameProblem.entries.associateWith { nameProblemText(it) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameFocus = remember { FocusRequester() }

    fun save() {
        if (!canSave) return
        val draft = ExerciseDraft(name, muscle, isIsometric, usesWeight, restSeconds, instructions, reference)
        scope.launch {
            try {
                val id = onSave(draft)
                sheet.hide()
                onDismiss()
                onSaved(id)
            } catch (nameProblem: NameProblemException) {
                problem = nameProblems[nameProblem.problem]
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                problem = couldntSave.format(error.message.orEmpty())
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.paper) {
        // A new exercise starts in its name, unless a search gave it one.
        LaunchedEffect(Unit) {
            if (exercise == null && start.name.isEmpty()) runCatching { nameFocus.requestFocus() }
        }
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { KamaeIcon(R.drawable.ic_close, stringResource(R.string.cancel)) }
                Text(
                    stringResource(if (exercise == null) R.string.new_exercise else R.string.edit_exercise),
                    Modifier.weight(1f),
                    color = colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                BarTextButton(stringResource(R.string.save), ::save, enabled = canSave)
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                FormCard {
                    FormField(
                        name,
                        {
                            name = it
                            problem = null
                        },
                        stringResource(R.string.name),
                        Modifier.focusRequester(nameFocus),
                        capitalization = KeyboardCapitalization.Words,
                    )
                    Hairline()
                    ChoiceRow(stringResource(R.string.muscle), muscleName(muscle), muscleOrder.map { muscleName(it) to it }, onChoose = { muscle = it })
                    Hairline()
                    SwitchRow(stringResource(R.string.isometric), isIsometric) { isIsometric = it }
                    Hairline()
                    SwitchRow(stringResource(R.string.uses_weight), usesWeight) { usesWeight = it }
                }
                Footer(problem ?: stringResource(R.string.exercise_kinds_footer), isProblem = problem != null)

                val choices = restSeconds?.takeIf { it !in restChoices }?.let { (restChoices + it).sorted() } ?: restChoices
                FormCard(Modifier.padding(top = 20.dp)) {
                    ChoiceRow(
                        stringResource(R.string.rest_after_each_set),
                        restSeconds?.let(::restLabel) ?: defaultRestText(defaultRestSeconds),
                        listOf(defaultRestText(defaultRestSeconds) to null) + choices.map { restLabel(it) to it },
                        onChoose = { restSeconds = it },
                    )
                }
                Footer(stringResource(R.string.rest_footer))

                SectionCaption(stringResource(R.string.caption_how_to), Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
                FormCard {
                    FormField(
                        instructions,
                        { instructions = it },
                        stringResource(R.string.one_step_per_line),
                        singleLine = false,
                        capitalization = KeyboardCapitalization.Sentences,
                    )
                }

                SectionCaption(stringResource(R.string.caption_see_it_done), Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
                FormCard {
                    FormField(reference, { reference = it }, stringResource(R.string.link_placeholder), keyboardType = KeyboardType.Uri)
                }
                if (!isReferenceValid) Footer(stringResource(R.string.not_a_link), isProblem = true)

                if (exercise != null) {
                    FormCard(Modifier.padding(top = 28.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canDelete, role = Role.Button) { confirmsDelete = true }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.delete_exercise), color = if (canDelete) colors.error else colors.inkFaint, fontSize = 16.sp)
                        }
                    }
                    if (!canDelete) Footer(stringResource(R.string.cant_delete_exercise))
                }
            }
        }
    }
    if (confirmsDelete && exercise != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_named, name.trim().ifEmpty { exercise.name }),
            text = stringResource(R.string.delete_exercise_text),
            confirm = stringResource(R.string.delete_exercise),
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = { confirmsDelete = false },
        )
    }
}

/** Rows on Card, corners 24, as the iPhone's grouped forms draw them. */
@Composable
fun FormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(kitaeColors.card, RoundedCornerShape(24.dp)), content = content)
}

/** A form's footer: what a choice does, or in red, what's wrong. */
@Composable
fun Footer(text: String, isProblem: Boolean = false) {
    val colors = kitaeColors
    Text(text, Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp), color = if (isProblem) colors.error else colors.inkSoft, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val colors = kitaeColors
    Box(
        Modifier.fillMaxWidth().heightIn(min = if (singleLine) 52.dp else 112.dp).padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = colors.inkFaint, fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            textStyle = TextStyle(color = colors.ink, fontSize = 16.sp, lineHeight = 22.sp),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
            cursorBrush = SolidColor(colors.olive),
        )
    }
}

/** A setting with a few choices, as a row whose value opens a menu. */
@Composable
fun <T> ChoiceRow(label: String, value: String, choices: List<Pair<String, T>>, onChoose: (T) -> Unit) {
    val colors = kitaeColors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { open = true }
                .heightIn(min = 52.dp)
                .padding(start = 18.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
            Text(value, color = colors.inkSoft, style = KitaeType.chip)
            KamaeIcon(R.drawable.ic_chevron_down, null, tint = colors.inkSoft)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            for ((text, choice) in choices) {
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onChoose(choice)
                })
            }
        }
    }
}

/** A setting that's on or off, with Material's switch in Kamae's olive. */
@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = kitaeColors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = if (enabled) colors.ink else colors.inkFaint, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onOlive,
                checkedTrackColor = colors.olive,
                uncheckedThumbColor = colors.inkSoft,
                uncheckedTrackColor = colors.field,
                uncheckedBorderColor = colors.inkFaint,
            ),
        )
    }
}

/** A muscle as the editor starts with it for a search in progress: the chip picked, or chest. */
fun startingMuscle(selection: MuscleGroup?): MuscleGroup = selection ?: MuscleGroup.Chest
