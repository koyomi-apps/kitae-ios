// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.you

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.app.YouData
import ca.gureet.kitae.database.ActivityWeeks
import ca.gureet.kitae.plans.planSizeLabel
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.random.Random

/**
 * The fourth tab (the iPhone's `YouView`): the plan followed, which opens its editor, the totals,
 * the last 16 weeks of training, and a short line. The gear opens Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    state: AppState,
    data: YouData?,
    onSettings: () -> Unit,
    onEditPlan: () -> Unit,
    onChoosePlan: () -> Unit,
    modifier: Modifier = Modifier,
    line: Int = rememberSaveable { Random.nextInt(5) },
) {
    val colors = kitaeColors
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Column(modifier.fillMaxSize()) {
        BigTitleBar(stringResource(R.string.you_title), scrollBehavior) {
            IconButton(onClick = onSettings) { KamaeIcon(R.drawable.ic_settings, stringResource(R.string.settings), tint = colors.inkSoft) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlanCard(state, onEditPlan, onChoosePlan)
            val totals = data?.totals
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.tile_lifts), totals?.lifts, Modifier.weight(1f))
                StatTile(stringResource(R.string.tile_days), totals?.days, Modifier.weight(1f))
                StatTile(stringResource(R.string.tab_exercises), totals?.exercises, Modifier.weight(1f))
            }
            data?.weeks?.let { ActivityCard(it) }
            if (data != null) {
                Text(
                    if (data.totals.lifts == 0) stringResource(R.string.you_line_first) else stringArrayResource(R.array.you_lines)[line % 5],
                    Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp),
                    color = colors.inkSoft,
                    style = KitaeType.sectionCaption,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The plan followed, on OliveSoft: it opens the plan editor; holding it offers All plans too. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanCard(state: AppState, onEditPlan: () -> Unit, onChoosePlan: () -> Unit) {
    val colors = kitaeColors
    val plan = state.plan
    var menuOpen by remember { mutableStateOf(false) }
    val openLabel = stringResource(R.string.opens_plan)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .background(colors.oliveSoft, RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClickLabel = if (plan != null) openLabel else null,
                    onClick = { if (plan != null) onEditPlan() else onChoosePlan() },
                    onLongClick = { if (plan != null) menuOpen = true },
                )
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (plan != null) R.string.caption_current_plan else R.string.caption_no_plan_yet),
                    color = colors.onOliveSoft,
                    style = KitaeType.sectionCaption,
                )
                DisplayText(
                    plan?.plan?.name ?: stringResource(R.string.choose_plan),
                    KitaeType.cardTitle.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    colors.onOliveSoft,
                    minSize = 22.sp,
                    maxLines = 2,
                )
                Text(
                    plan?.let { planSizeLabel(it.days.size, it.exerciseCount) } ?: stringResource(R.string.pick_plan_text),
                    color = colors.onOliveSoft,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.onOliveSoft)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_plan)) },
                leadingIcon = { KamaeIcon(R.drawable.ic_edit, null) },
                onClick = {
                    menuOpen = false
                    onEditPlan()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_plans)) },
                leadingIcon = { KamaeIcon(R.drawable.ic_exercises, null) },
                onClick = {
                    menuOpen = false
                    onChoosePlan()
                },
            )
        }
    }
}

/** One of the totals, as the canvas draws them: its name over a big Space Mono number. */
@Composable
private fun StatTile(title: String, value: Int?, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Column(
        modifier
            .height(96.dp)
            .background(colors.card, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = colors.ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(value?.toString() ?: "", color = colors.ink, style = KitaeType.tileNumber.copy(lineHeight = 32.sp, letterSpacing = (-1).sp), maxLines = 1)
    }
}

/**
 * The last 16 weeks: a column per week and a row per weekday, Olive for each day with a done set,
 * DayOff for the rest, and this week's days still to come faded. The squares fill the card.
 */
@Composable
private fun ActivityCard(weeks: ActivityWeeks) {
    val colors = kitaeColors
    val locale = LocalConfiguration.current.locales[0]
    val description = stringResource(R.string.days_trained, weeks.trainedCount)
    Column(
        Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.activity), Modifier.weight(1f).alignByBaseline().semantics { heading() }, color = colors.ink, style = KitaeType.workoutTitle)
            Text(stringResource(R.string.last_16_weeks), Modifier.alignByBaseline(), color = colors.inkSoft, fontSize = 13.sp)
        }
        Column(Modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val spacing = 4.dp
                val labelWidth = 12.dp
                val cell = (maxWidth - labelWidth - 8.dp - spacing * (ActivityWeeks.COUNT - 1)) / ActivityWeeks.COUNT
                Row {
                    Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        for (row in 0 until 7) {
                            Box(Modifier.size(labelWidth, cell), contentAlignment = Alignment.Center) {
                                Text(
                                    LocalDate.ofEpochDay(weeks.day(0, row)).dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                                    color = colors.inkSoft,
                                    style = KitaeType.fieldCaption,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Box(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        for (week in 0 until ActivityWeeks.COUNT) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                                for (row in 0 until 7) {
                                    val day = weeks.day(week, row)
                                    Box(
                                        Modifier
                                            .size(cell)
                                            .alpha(if (day > weeks.today) 0.4f else 1f)
                                            .background(if (day in weeks.trained) colors.olive else colors.dayOff, RoundedCornerShape(4.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 20.dp)) {
                Text(Dates.dayAndMonth(weeks.first), Modifier.weight(1f), color = colors.inkSoft, style = KitaeType.fieldCaption.copy(fontSize = 11.sp))
                Text(Dates.dayAndMonth(weeks.today), color = colors.inkSoft, style = KitaeType.fieldCaption.copy(fontSize = 11.sp))
            }
        }
    }
}
