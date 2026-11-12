// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.ExerciseGroup
import ca.gureet.kitae.app.ExerciseList
import ca.gureet.kitae.app.exerciseGroups
import ca.gureet.kitae.database.Exercise
import ca.gureet.kitae.database.ExerciseDraft
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.MuscleChips
import ca.gureet.kitae.ui.components.NoExerciseFound
import ca.gureet.kitae.ui.components.SearchField
import ca.gureet.kitae.ui.components.cardRowShape
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.ui.weightFirst

/**
 * The third tab (the iPhone's `ExercisesView`): every exercise by muscle, with the heaviest set of
 * the last time it was done, a search that always shows, muscle chips, and + for an exercise of
 * your own. A search that finds nothing offers to make one with that name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    list: ExerciseList?,
    formatter: WeightFormatter,
    defaultRestSeconds: Int,
    onOpen: (Long) -> Unit,
    onSaveExercise: suspend (ExerciseDraft) -> Long,
    modifier: Modifier = Modifier,
    initialSearch: String = "",
) {
    var search by rememberSaveable { mutableStateOf(initialSearch) }
    var muscle by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var editing by remember { mutableStateOf<ExerciseDraft?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val groups = list?.let { exerciseGroups(it.exercises, muscle, search) }.orEmpty()
    val newDraft = { ExerciseDraft(name = search.trim(), muscle = startingMuscle(muscle)) }
    Column(modifier.fillMaxSize()) {
        BigTitleBar(stringResource(R.string.exercises_title), scrollBehavior) {
            IconButton(onClick = { editing = newDraft() }) { KamaeIcon(R.drawable.ic_add, stringResource(R.string.new_exercise)) }
        }
        SearchField(search, { search = it }, (list?.exercises?.size ?: 0).let { pluralStringResource(R.plurals.search_exercises, it, it) }, Modifier.padding(horizontal = 16.dp))
        MuscleChips(muscle, { muscle = it }, Modifier.padding(top = 12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list != null && groups.isEmpty() && search.isNotBlank()) {
                NoExerciseFound(search, onMake = { editing = newDraft() }, Modifier.align(Alignment.Center).padding(bottom = 64.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            ) {
                exerciseSections(groups) { exercise, index, count ->
                    val best = list?.lastSessions?.get(exercise.id)?.best(exercise.usesWeight, exercise.isAssisted)
                    val text = best?.let { weightFirst(formatter, it.reps, exercise.weight(it.weightKg), exercise.isIsometric) }
                    ExerciseRow(exercise.name, index, count, onClick = { onOpen(exercise.id) }) {
                        if (text != null) {
                            val label = stringResource(R.string.last_time, text)
                            Text(text, Modifier.semantics { contentDescription = label }, color = kitaeColors.inkSoft, style = KitaeType.sectionCaption, maxLines = 1)
                        }
                    }
                }
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
            onSaved = { id ->
                search = ""
                onOpen(id)
            },
            onDelete = {},
            onDismiss = { editing = null },
        )
    }
}

/** Sections of exercises, each headed like ".chest", shared with the picker. */
fun LazyListScope.exerciseSections(
    groups: List<ExerciseGroup>,
    first: Boolean = true,
    row: @Composable (Exercise, Int, Int) -> Unit,
) {
    groups.forEachIndexed { groupIndex, group ->
        item(key = "section ${group.muscle?.stored ?: "other"}") {
            SectionHeader(".${muscleName(group.muscle).lowercase()}", Modifier.padding(top = if (first && groupIndex == 0) 0.dp else 20.dp))
        }
        itemsIndexed(group.exercises, key = { _, exercise -> "${group.muscle?.stored} ${exercise.id}" }) { index, exercise ->
            row(exercise, index, group.exercises.size)
        }
    }
}

/** A section's caption over its card, Space Mono 13, as the canvas has it. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 8.dp, bottom = 8.dp), color = kitaeColors.inkSoft, style = KitaeType.smallCaption)
}

/** A row of a card of exercises, 56 tall, its name on the left and [trailing] on the right. */
@Composable
fun ExerciseRow(
    name: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    nameColor: androidx.compose.ui.graphics.Color = kitaeColors.ink,
    trailing: @Composable () -> Unit,
) {
    val colors = kitaeColors
    Column(Modifier.fillMaxWidth().clip(cardRowShape(index, count)).background(colors.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f).padding(end = 12.dp), color = nameColor, fontSize = 16.sp)
            trailing()
        }
        if (index < count - 1) Hairline()
    }
}
