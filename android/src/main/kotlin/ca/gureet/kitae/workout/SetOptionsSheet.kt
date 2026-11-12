// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.database.SetType
import ca.gureet.kitae.database.WorkoutExercise
import ca.gureet.kitae.database.WorkoutSet
import ca.gureet.kitae.database.setLabels
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/** The rests a set can have of its own, as the iPhone offers them. */
val restChoices = listOf(30, 45, 60, 75, 90, 120, 150, 180, 240, 300)

/** The name of a set's type, as the set options list them. */
@Composable
fun setTypeName(type: SetType): String = stringResource(
    when (type) {
        SetType.Warmup -> R.string.set_type_warmup
        SetType.Standard -> R.string.set_type_standard
        SetType.Failure -> R.string.set_type_failure
        SetType.Drop -> R.string.set_type_drop
        SetType.RestPause -> R.string.set_type_rest_pause
    },
)

/**
 * A set's options, from its label (the iPhone's `SetOptionsSheet`): its type, with its label
 * following the type as it changes, so 03 turns into F1; its rest; and deleting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetOptionsSheet(
    exercise: WorkoutExercise,
    set: WorkoutSet,
    defaultRestSeconds: Int,
    onType: (SetType) -> Unit,
    onRest: (Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = kitaeColors
    val type = SetType.fromStored(set.type)
    val labels = setLabels(exercise.sets.map { SetType.fromStored(it.type) })
    val label = labels.getOrNull(exercise.sets.indexOfFirst { it.id == set.id }) ?: ""
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(exercise.exercise.name, color = colors.olive, style = KitaeType.sectionTitle)
                Caption(stringResource(R.string.set_caption, label), style = KitaeType.smallCaption)
            }
            SectionCaption(stringResource(R.string.caption_set_type), Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp))
            for (option in SetType.entries) {
                val picked = option == type
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { if (!picked) onType(option) }
                        .semantics { selected = picked }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 32.dp)
                            .background(if (picked) colors.oliveSoft else colors.field, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (picked) label else option.letter ?: "01",
                            color = if (picked) colors.onOliveSoft else colors.inkSoft,
                            style = KitaeType.chip.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(setTypeName(option), Modifier.weight(1f), color = colors.ink, fontSize = 16.sp, fontWeight = if (picked) FontWeight.Medium else FontWeight.Normal)
                    if (picked) KamaeIcon(R.drawable.ic_check, null, tint = colors.olive)
                }
            }
            Hairline(Modifier.padding(vertical = 4.dp, horizontal = 24.dp), start = 0.dp)
            RestRow(set.restSeconds, defaultRestSeconds, onRest)
            Hairline(Modifier.padding(vertical = 4.dp, horizontal = 24.dp), start = 0.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onDelete)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KamaeIcon(R.drawable.ic_delete, null, tint = colors.error)
                Text(stringResource(R.string.delete_set), color = colors.error, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RestRow(rest: Int?, defaultRestSeconds: Int, onRest: (Int?) -> Unit) {
    val colors = kitaeColors
    var open by remember { mutableStateOf(false) }
    val defaultText = stringResource(R.string.rest_default, if (defaultRestSeconds > 0) restLabel(defaultRestSeconds) else stringResource(R.string.rest_off))
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { open = true }
                .height(56.dp)
                .padding(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.rest_after_set), Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
            Text(rest?.let(::restLabel) ?: defaultText, color = colors.inkSoft, style = KitaeType.chip)
            KamaeIcon(R.drawable.ic_chevron_down, null, tint = colors.inkSoft)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(defaultText) }, onClick = {
                open = false
                onRest(null)
            })
            for (seconds in restChoices) {
                DropdownMenuItem(text = { Text(restLabel(seconds), style = KitaeType.chip) }, onClick = {
                    open = false
                    onRest(seconds)
                })
            }
        }
    }
}
