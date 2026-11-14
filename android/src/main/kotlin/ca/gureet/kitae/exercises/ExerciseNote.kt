// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.kitaeColors
import kotlinx.coroutines.launch

/**
 * An exercise's note on its page, which opens to change it, or "Add a note" (the iPhone's
 * `ExerciseDetailView.note`). In the workout it's [compact]: three lines at most, in the small font.
 */
@Composable
fun NoteRow(note: String?, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = kitaeColors
    val spoken = note?.let { stringResource(R.string.note_spoken, it) } ?: stringResource(R.string.add_note)
    val action = stringResource(if (note == null) R.string.write_note else R.string.change_note)
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .heightIn(min = if (compact) 0.dp else 44.dp)
            .padding(start = if (compact) 4.dp else 2.dp, end = 8.dp, top = if (compact) 2.dp else 8.dp, bottom = if (compact) 2.dp else 8.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KamaeIcon(
            if (note == null) R.drawable.ic_add_note else R.drawable.ic_note,
            null,
            Modifier.padding(top = 1.dp),
            tint = colors.olive,
            size = if (compact) 16.dp else 20.dp,
        )
        Text(
            note ?: stringResource(R.string.add_note),
            color = if (note == null) colors.olive else colors.ink,
            fontSize = if (compact) 13.sp else 16.sp,
            lineHeight = if (compact) 18.sp else 22.sp,
            fontWeight = if (note == null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = if (compact) 3 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Writes an exercise's note (the iPhone's `ExerciseNoteSheet`): anything to remember for it,
 * like a seat height or a grip. Saving an empty note deletes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseNoteSheet(exerciseName: String, note: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(note.orEmpty()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.paper) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { KamaeIcon(R.drawable.ic_close, stringResource(R.string.cancel)) }
                Text(
                    exerciseName,
                    Modifier.weight(1f),
                    color = colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BarTextButton(stringResource(R.string.save), onClick = {
                    onSave(text)
                    scope.launch {
                        sheet.hide()
                        onDismiss()
                    }
                })
            }
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                FormCard {
                    FormField(
                        text,
                        { text = it },
                        stringResource(R.string.note_placeholder),
                        Modifier.focusRequester(focus),
                        singleLine = false,
                        capitalization = KeyboardCapitalization.Sentences,
                    )
                }
                Footer(stringResource(R.string.note_footer))
            }
        }
    }
}
