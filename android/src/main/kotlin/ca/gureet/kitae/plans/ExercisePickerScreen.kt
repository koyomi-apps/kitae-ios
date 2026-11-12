// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.ExerciseList
import ca.gureet.kitae.app.exerciseGroups
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.exercises.ExerciseEditorSheet
import ca.gureet.kitae.exercises.ExerciseRow
import ca.gureet.kitae.exercises.SectionHeader
import ca.gureet.kitae.exercises.exerciseSections
import ca.gureet.kitae.exercises.startingMuscle
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.MuscleChips
import ca.gureet.kitae.ui.components.NoExerciseFound
import ca.gureet.kitae.ui.components.PageTopBar
import ca.gureet.kitae.ui.components.SearchField
import ca.gureet.kitae.ui.theme.kitaeColors

/** How many exercises done lately the picker offers first. */
const val RECENT_IN_PICKER = 5

/**
 * Picks an exercise to add to a day (the iPhone's `ExercisePicker`): one done lately, or search by
 * name, narrow it down by muscle, or make a new one, which is added straight away. The day's own
 * exercises are ticked and can't be added twice.
 */
@Composable
fun ExercisePickerScreen(
    list: ExerciseList?,
    dayName: String,
    added: Set<Long>,
    defaultRestSeconds: Int,
    onPick: (Long) -> Unit,
    onBack: () -> Unit,
    onSaveExercise: suspend (ExerciseDraft) -> Long,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var editing by remember { mutableStateOf<ExerciseDraft?>(null) }
    val exercises = list?.exercises.orEmpty()
    val groups = exerciseGroups(exercises, muscle, search)
    val recent = if (muscle == null && search.isBlank()) {
        val byId = exercises.associateBy { it.id }
        list?.recentIds.orEmpty().filter { it !in added }.mapNotNull { byId[it] }.take(RECENT_IN_PICKER)
    } else {
        emptyList()
    }
    val newDraft = { ExerciseDraft(name = search.trim(), muscle = startingMuscle(muscle)) }
    val row = @Composable { exercise: Exercise, index: Int, count: Int ->
        val isAdded = exercise.id in added
        val colors = kitaeColors
        ExerciseRow(exercise.name, index, count, onClick = { onPick(exercise.id) }, enabled = !isAdded, nameColor = if (isAdded) colors.inkSoft else colors.ink) {
            KamaeIcon(if (isAdded) R.drawable.ic_check else R.drawable.ic_add, null, tint = if (isAdded) colors.olive else colors.inkFaint)
        }
    }
    Column(modifier.fillMaxSize()) {
        PageTopBar(onBack, title = stringResource(R.string.add_to, dayName)) {
            IconButton(onClick = { editing = newDraft() }) { KamaeIcon(R.drawable.ic_add, stringResource(R.string.new_exercise)) }
        }
        SearchField(search, { search = it }, pluralStringResource(R.plurals.search_exercises, exercises.size, exercises.size), Modifier.padding(horizontal = 16.dp))
        MuscleChips(muscle, { muscle = it }, Modifier.padding(top = 12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list != null && groups.isEmpty() && search.isNotBlank()) {
                NoExerciseFound(search, onMake = { editing = newDraft() }, Modifier.align(Alignment.Center).padding(bottom = 64.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp)) {
                if (recent.isNotEmpty()) {
                    item(key = "recent") { SectionHeader(stringResource(R.string.caption_recent)) }
                    itemsIndexed(recent, key = { _, exercise -> "recent ${exercise.id}" }) { index, exercise -> row(exercise, index, recent.size) }
                }
                exerciseSections(groups, first = recent.isEmpty()) { exercise, index, count -> row(exercise, index, count) }
            }
        }
    }
    editing?.let { draft ->
        ExerciseEditorSheet(
            exercise = null,
            start = draft,
            defaultRestSeconds = defaultRestSeconds,
            canDelete = false,
            onSave = onSaveExercise,
            onSaved = onPick,
            onDelete = {},
            onDismiss = { editing = null },
        )
    }
}
