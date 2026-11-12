// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.ui.components.Caption
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.KitaeCard
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * The list of plans (the iPhone's `PlansView`): "Choose a plan" until one is followed, then
 * "Plans". Tapping one follows it from today, except while a workout is going.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(state: AppState, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = kitaeColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        PlansList(state, onSelect)
    }
}

@Composable
fun PlansList(state: AppState, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val current = state.plan?.plan?.id
    val locked = state.workout != null
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        Text(
            stringResource(if (current == null) R.string.choose_plan else R.string.plans),
            Modifier.padding(horizontal = 24.dp),
            color = colors.ink,
            style = KitaeType.bigTitle,
        )
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked) { onSelect(plan.id) }
                        .semantics { selected = isCurrent }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, color = colors.ink, fontSize = 17.sp)
                        Caption(
                            stringResource(
                                R.string.plan_summary,
                                pluralStringResource(R.plurals.day_count, plan.dayCount, plan.dayCount),
                                pluralStringResource(R.plurals.exercise_count, plan.exerciseCount, plan.exerciseCount),
                            ),
                            style = KitaeType.smallCaption,
                        )
                    }
                    if (isCurrent) {
                        Spacer(Modifier.width(12.dp))
                        KamaeIcon(R.drawable.ic_check, stringResource(R.string.following), tint = colors.olive)
                    }
                }
            }
        }
    }
}
