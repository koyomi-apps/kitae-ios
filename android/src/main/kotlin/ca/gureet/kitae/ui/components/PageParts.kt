// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.muscleOrder
import ca.gureet.kitae.database.MuscleGroup
import ca.gureet.kitae.ui.muscleName
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * A tab's big title, Darker Grotesque 48, in Material's large top app bar, which collapses to a
 * small title as the page scrolls. Its baseline sits where the Kamae canvas has it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BigTitleBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = kitaeColors
    LargeTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { KamaeIcon(R.drawable.ic_back, stringResource(R.string.back)) }
        },
        actions = actions,
        expandedHeight = 144.dp,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.paper,
            scrolledContainerColor = colors.paper,
            titleContentColor = colors.ink,
            actionIconContentColor = colors.ink,
            navigationIconContentColor = colors.ink,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/** A pushed page's bar: back on the left and the page's actions on the right, with no title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(onBack: () -> Unit, title: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val colors = kitaeColors
    TopAppBar(
        title = { if (title != null) Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { KamaeIcon(R.drawable.ic_back, stringResource(R.string.back)) }
        },
        actions = actions,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper, titleContentColor = colors.ink),
    )
}

/** An olive text button for a bar, like Edit, Done or Save. */
@Composable
fun BarTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = kitaeColors
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text, color = if (enabled) colors.olive else colors.inkFaint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The search field that always shows above a list of exercises: Field, 56 tall, fully round. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.field, RoundedCornerShape(28.dp))
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KamaeIcon(R.drawable.ic_search, null, tint = colors.inkSoft)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = colors.inkSoft, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(colors.olive),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { KamaeIcon(R.drawable.ic_close, stringResource(R.string.close), tint = colors.inkSoft) }
        }
    }
}

/** Chips for every exercise, or one muscle's, scrolling sideways (`MuscleChips` on the iPhone). */
@Composable
fun MuscleChips(selection: MuscleGroup?, onSelect: (MuscleGroup?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { DayChip(stringResource(R.string.all), selection == null, onClick = { onSelect(null) }) }
        items(muscleOrder) { muscle ->
            DayChip(muscleName(muscle), selection == muscle, onClick = { onSelect(if (selection == muscle) null else muscle) })
        }
    }
}

/**
 * The shape of row [index] of [count] in a card made of separate rows, as lists draw cards: the
 * first row has the top corners, the last the bottom ones.
 */
fun cardRowShape(index: Int, count: Int, radius: Dp = 24.dp): Shape = when {
    count == 1 -> RoundedCornerShape(radius)
    index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
    index == count - 1 -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    else -> RectangleShape
}

/** Asks before something is deleted or left behind. [confirm] is red when [destructive]. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismiss: String = stringResource(R.string.cancel),
) {
    val colors = kitaeColors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm, color = if (destructive) colors.error else colors.olive, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = colors.olive) } },
        title = { Text(title, fontSize = 22.sp) },
        text = { Text(text, fontSize = 15.sp) },
        containerColor = colors.field,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSoft,
    )
}

/** Shown when a search finds no exercise: offers to make one with that name. */
@Composable
fun NoExerciseFound(search: String, onMake: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        R.drawable.ic_search,
        stringResource(R.string.no_exercise_found, search.trim()),
        stringResource(R.string.no_exercise_found_text),
        modifier,
        action = stringResource(R.string.new_exercise),
        onAction = onMake,
    )
}
