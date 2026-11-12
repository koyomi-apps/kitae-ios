// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.plans

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.database.PlanSummary
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.KitaeCard
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * The list of plans (the iPhone's `PlansView`): "Choose a plan" until one is followed, then
 * "Plans". Tapping one follows it from today, except while a workout is going; the pencil, or
 * holding one, edits it, and holding one offers Delete. New plan asks for a name and opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(
    state: AppState,
    onSelect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onCreate: suspend (String) -> Long,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = kitaeColors
    var namesNewPlan by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlanSummary?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        PlansList(state, onSelect, onEdit = onEdit, onDelete = { deleting = it }, onNew = { namesNewPlan = true })
    }
    if (namesNewPlan) {
        NameSheet(
            stringResource(R.string.new_plan),
            stringResource(R.string.plan_name),
            stringResource(R.string.create),
            onSave = { name -> onEdit(onCreate(name)) },
            onDismiss = { namesNewPlan = false },
        )
    }
    deleting?.let { plan ->
        ConfirmDialog(
            title = stringResource(R.string.delete_named, plan.name),
            text = stringResource(R.string.delete_plan_text),
            confirm = stringResource(R.string.delete_plan),
            onConfirm = { onDelete(plan.id) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlansList(
    state: AppState,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (Long) -> Unit = {},
    onDelete: (PlanSummary) -> Unit = {},
    onNew: () -> Unit = {},
) {
    val colors = kitaeColors
    val current = state.plan?.plan?.id
    val locked = state.workout != null
    val busyPlanId = state.workout?.workout?.planId
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (current == null) R.string.choose_plan else R.string.plans),
                Modifier.weight(1f),
                color = colors.ink,
                style = KitaeType.bigTitle,
            )
            IconButton(onClick = onNew) { KamaeIcon(R.drawable.ic_add, stringResource(R.string.new_plan)) }
        }
        Text(
            stringResource(if (locked) R.string.finish_to_switch_plans else R.string.plans_intro),
            Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            color = colors.inkSoft,
            fontSize = 15.sp,
        )
        KitaeCard(Modifier.padding(horizontal = 16.dp)) {
            state.plans.forEachIndexed { index, plan ->
                if (index > 0) Hairline()
                val isCurrent = plan.id == current
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = !locked || isCurrent,
                                onClick = { onSelect(plan.id) },
                                onLongClick = { menuOpen = true },
                            )
                            .semantics { selected = isCurrent }
                            .heightIn(min = 64.dp)
                            .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, color = colors.ink, fontSize = 17.sp)
                            Caption(planSizeLabel(plan.dayCount, plan.exerciseCount), style = KitaeType.smallCaption)
                        }
                        if (isCurrent) KamaeIcon(R.drawable.ic_check, stringResource(R.string.following), tint = colors.olive)
                        IconButton(onClick = { onEdit(plan.id) }) {
                            KamaeIcon(R.drawable.ic_edit, stringResource(R.string.edit_named, plan.name), tint = colors.olive)
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { KamaeIcon(R.drawable.ic_edit, null) },
                            onClick = {
                                menuOpen = false
                                onEdit(plan.id)
                            },
                        )
                        if (plan.id != busyPlanId) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = colors.error) },
                                leadingIcon = { KamaeIcon(R.drawable.ic_delete, null, tint = colors.error) },
                                onClick = {
                                    menuOpen = false
                                    onDelete(plan)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
