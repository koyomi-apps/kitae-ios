// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.BodyActions
import ca.gureet.kitae.exercises.ChoiceRow
import ca.gureet.kitae.exercises.Footer
import ca.gureet.kitae.exercises.FormCard
import ca.gureet.kitae.exercises.ProgressChart
import ca.gureet.kitae.model.ActivityLevel
import ca.gureet.kitae.model.BodyEntry
import ca.gureet.kitae.model.BodyLog
import ca.gureet.kitae.model.BodyProfile
import ca.gureet.kitae.model.HeightFormatter
import ca.gureet.kitae.model.Sex
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.settings.ChoiceBar
import ca.gureet.kitae.ui.Dates
import ca.gureet.kitae.ui.components.BarTextButton
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.components.cardRowShape
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private const val DAY_MILLIS = 86_400_000L

/** What people can type, in kilograms, percent and centimetres. */
private val WEIGHTS = 20.0..400.0
private val BODY_FATS = 2.0..70.0
private val HEIGHTS = 50.0..250.0

/** Which line the Body page's chart draws. */
private enum class BodyChart { Weight, BodyFat, Lean }

/**
 * The Body page, from You (the iPhone's `BodyView`): the latest weigh-in and how weight, body fat
 * or lean mass have gone, every weigh-in, and what people say about themselves. All of it is
 * optional, and stays on the phone and in Kamae's backups.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    log: BodyLog,
    formatter: WeightFormatter,
    today: Long,
    actions: BodyActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    // The weigh-in being changed, or a new one (an entry with id 0).
    var weighIn by remember { mutableStateOf<BodyEntry?>(null) }
    var editsHeight by remember { mutableStateOf(false) }
    var editsBirthDate by remember { mutableStateOf(false) }
    val newWeighIn = BodyEntry(id = 0, date = today)

    Column(modifier.fillMaxSize()) {
        BigTitleBar(stringResource(R.string.body), scrollBehavior, onBack = onBack) {
            IconButton(onClick = { weighIn = newWeighIn }) { KamaeIcon(R.drawable.ic_add, stringResource(R.string.add_weigh_in), tint = colors.inkSoft) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            FormCard { Latest(log, formatter, Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) }

            SectionCaption(stringResource(R.string.caption_weigh_ins), Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
            val entries = log.entries.reversed()
            if (entries.isEmpty()) {
                FormCard {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { weighIn = newWeighIn }.heightIn(min = 52.dp).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KamaeIcon(R.drawable.ic_add, null, tint = colors.olive, size = 20.dp)
                        Text(stringResource(R.string.add_a_weigh_in), color = colors.olive, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            entries.forEachIndexed { index, entry ->
                WeighInRow(entry, formatter, today, index, entries.size, onOpen = { weighIn = entry }, onDelete = { actions.deleteWeighIn(entry.id) })
            }

            SectionCaption(stringResource(R.string.caption_about_you), Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
            FormCard { ProfileRows(log.profile, formatter, today, actions, onHeight = { editsHeight = true }, onBirthDate = { editsBirthDate = true }) }
            Footer(stringResource(R.string.about_you_footer))
        }
    }
    weighIn?.let { entry ->
        WeighInSheet(entry.takeIf { it.id != 0L }, log, formatter, today, actions, onDismiss = { weighIn = null })
    }
    if (editsHeight) HeightSheet(log.profile, HeightFormatter(formatter.unit), actions, onDismiss = { editsHeight = false })
    if (editsBirthDate) BirthDateDialog(log.profile, today, actions, onDismiss = { editsBirthDate = false })
}

/** The latest weigh-in, its body fat and what it's made of, and the chart; or an invitation to weigh in. */
@Composable
private fun Latest(log: BodyLog, formatter: WeightFormatter, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val latest = log.latestWeight
        val kilograms = latest?.weightKg
        if (latest != null && kilograms != null) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.caption_latest, Dates.dayAndMonth(latest.date)), color = colors.inkSoft, style = KitaeType.sectionCaption)
                Text(formatter.body(kilograms), color = colors.olive, style = KitaeType.pageTitle)
                composition(log, formatter)?.let { Text(it, color = colors.inkSoft, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.caption_no_weigh_ins), color = colors.inkSoft, style = KitaeType.sectionCaption)
                Text(stringResource(R.string.track_your_weight), color = colors.ink, style = KitaeType.workoutTitle.copy(fontSize = 30.sp, lineHeight = 32.sp))
                Text(stringResource(R.string.body_empty_text), color = colors.inkSoft, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        ChartSection(log, formatter)
    }
}

/** "18.5% body fat · 64.1 kg lean · 14.3 kg fat", from the latest weigh-in with body fat. */
@Composable
private fun composition(log: BodyLog, formatter: WeightFormatter): String? {
    val entry = log.latestBodyFat ?: return null
    val percent = entry.bodyFatPercent ?: return null
    val parts = mutableListOf(stringResource(R.string.body_fat_part, formatter.percent(percent)))
    val lean = entry.leanKg
    val fat = entry.fatKg
    if (lean != null && fat != null) {
        parts += stringResource(R.string.lean_part, formatter.body(lean))
        parts += stringResource(R.string.fat_part, formatter.body(fat))
    }
    return parts.joinToString(stringResource(R.string.details_separator))
}

/** A chart of weight, body fat or lean mass, with a choice bar when more than one has two points. */
@Composable
private fun ChartSection(log: BodyLog, formatter: WeightFormatter) {
    val colors = kitaeColors
    var chosen by rememberSaveable { mutableStateOf(BodyChart.Weight) }
    fun points(kind: BodyChart): List<Pair<Long, Double>> = log.entries.mapNotNull { entry ->
        when (kind) {
            BodyChart.Weight -> entry.weightKg?.let { entry.date to formatter.unit.fromKilograms(it) }
            BodyChart.BodyFat -> entry.bodyFatPercent?.let { entry.date to it }
            BodyChart.Lean -> entry.leanKg?.let { entry.date to formatter.unit.fromKilograms(it) }
        }
    }
    val kinds = BodyChart.entries.filter { points(it).size >= 2 }
    val kind = if (chosen in kinds) chosen else kinds.firstOrNull() ?: return
    val points = points(kind)
    val names = mapOf(
        BodyChart.Weight to stringResource(R.string.chart_weight),
        BodyChart.BodyFat to stringResource(R.string.chart_body_fat),
        BodyChart.Lean to stringResource(R.string.chart_lean),
    )
    fun value(number: Double) = if (kind == BodyChart.BodyFat) formatter.percent(number) else "${formatter.tenthsOrWhole(number)} ${formatter.unit.symbol}"
    val (firstDate, firstValue) = points.first()
    val (lastDate, lastValue) = points.last()
    val spoken = stringResource(
        R.string.body_chart_spoken,
        names.getValue(kind).lowercase(),
        value(firstValue),
        Dates.dayAndMonth(firstDate),
        value(lastValue),
        Dates.dayAndMonth(lastDate),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (kinds.size > 1) ChoiceBar(kinds.map { it to names.getValue(it) }, kind, onSelect = { chosen = it })
        ProgressChart(
            points.map { it.second },
            minimumStep = 0.5,
            label = { formatter.tenthsOrWhole(it) },
            modifier = Modifier.fillMaxWidth().height(132.dp).clearAndSetSemantics { contentDescription = spoken },
        )
        Row(Modifier.clearAndSetSemantics {}) {
            Text(Dates.dayAndMonth(firstDate), Modifier.weight(1f), color = colors.inkSoft, style = KitaeType.fieldCaption)
            Text(Dates.dayAndMonth(lastDate), color = colors.inkSoft, style = KitaeType.fieldCaption)
        }
    }
}

/** A weigh-in: its day, and its weight and body fat. Tapping changes it; swiping deletes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeighInRow(entry: BodyEntry, formatter: WeightFormatter, today: Long, index: Int, count: Int, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = kitaeColors
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            swipe.reset()
        }
    }
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.clip(cardRowShape(index, count)),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().background(colors.error).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                    KamaeIcon(R.drawable.ic_delete, stringResource(R.string.delete), tint = colors.paper)
                }
            }
        },
    ) {
        Column(Modifier.background(colors.card)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.change_weigh_in), role = Role.Button, onClick = onOpen)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Dates.short(entry.date, today), Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
                Text(
                    listOfNotNull(entry.weightKg?.let(formatter::body), entry.bodyFatPercent?.let(formatter::percent)).joinToString(stringResource(R.string.details_separator)),
                    color = colors.inkSoft,
                    style = KitaeType.step,
                    maxLines = 1,
                )
            }
            if (index < count - 1) Hairline()
        }
    }
}

/** Height, date of birth (with age), sex and activity level. */
@Composable
private fun ProfileRows(
    profile: BodyProfile,
    formatter: WeightFormatter,
    today: Long,
    actions: BodyActions,
    onHeight: () -> Unit,
    onBirthDate: () -> Unit,
) {
    val notSet = stringResource(R.string.not_set)
    ProfileRow(stringResource(R.string.height), profile.heightCm?.let { HeightFormatter(formatter.unit).string(it) } ?: notSet, onHeight)
    Hairline()
    val birthDate = profile.birthDate?.let { day ->
        val date = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        profile.age(today)?.let { stringResource(R.string.birth_date_age, date, it) } ?: date
    }
    ProfileRow(stringResource(R.string.date_of_birth), birthDate ?: notSet, onBirthDate)
    Hairline()
    val sexes = mapOf(Sex.Female to stringResource(R.string.sex_female), Sex.Male to stringResource(R.string.sex_male))
    ChoiceRow(
        stringResource(R.string.sex),
        profile.sex?.let(sexes::getValue) ?: notSet,
        listOf(notSet to null) + Sex.entries.map { sexes.getValue(it) to it },
    ) { sex -> actions.saveProfile(profile.copy(sex = sex)) }
    Hairline()
    val levels = activityLevelNames()
    ChoiceRow(
        stringResource(R.string.activity_level),
        profile.activityLevel?.let(levels::getValue) ?: notSet,
        listOf(notSet to null) + ActivityLevel.entries.map { levels.getValue(it) to it },
    ) { level -> actions.saveProfile(profile.copy(activityLevel = level)) }
}

@Composable
private fun activityLevelNames(): Map<ActivityLevel, String> = ActivityLevel.entries.associateWith {
    val (name, detail) = when (it) {
        ActivityLevel.Sedentary -> R.string.activity_sedentary to R.string.activity_sedentary_detail
        ActivityLevel.LightlyActive -> R.string.activity_lightly_active to R.string.activity_lightly_active_detail
        ActivityLevel.ModeratelyActive -> R.string.activity_moderately_active to R.string.activity_moderately_active_detail
        ActivityLevel.VeryActive -> R.string.activity_very_active to R.string.activity_very_active_detail
        ActivityLevel.ExtraActive -> R.string.activity_extra_active to R.string.activity_extra_active_detail
    }
    stringResource(name) + stringResource(R.string.details_separator) + stringResource(detail)
}

@Composable
private fun ProfileRow(label: String, value: String, onClick: () -> Unit) {
    val colors = kitaeColors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(start = 18.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
        Text(value, color = colors.inkSoft, fontSize = 16.sp, maxLines = 1)
        KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.inkFaint, size = 20.dp)
    }
}

/** A sheet with Cancel, a title and Save over its form, as the note sheet has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(title: String, canSave: Boolean, onSave: () -> Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = kitaeColors
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = colors.paper) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { KamaeIcon(R.drawable.ic_close, stringResource(R.string.cancel)) }
                Text(title, Modifier.weight(1f), color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BarTextButton(stringResource(R.string.save), enabled = canSave, onClick = {
                    if (onSave()) {
                        scope.launch {
                            sheet.hide()
                            onDismiss()
                        }
                    }
                })
            }
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), content = content)
        }
    }
}

/** A number in a form: its name, the field on the right in Space Mono, and its unit. */
@Composable
private fun NumberRow(label: String, value: String, unit: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
        Box(Modifier.width(110.dp), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text("–", color = colors.inkFaint, style = KitaeType.tileNumber.copy(fontSize = 17.sp))
            BasicTextField(
                value,
                onChange,
                modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = KitaeType.tileNumber.copy(fontSize = 17.sp, color = colors.ink, textAlign = TextAlign.End),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(colors.olive),
            )
        }
        Text(unit, color = colors.inkSoft, style = KitaeType.step)
    }
}

/**
 * Adds a weigh-in, or changes [entry] (the iPhone's `WeighInSheet`): the day, the weight, and
 * body fat if it's known. A day has one weigh-in, so saving on a day that has one replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeighInSheet(entry: BodyEntry?, log: BodyLog, formatter: WeightFormatter, today: Long, actions: BodyActions, onDismiss: () -> Unit) {
    val colors = kitaeColors
    var date by rememberSaveable { mutableStateOf(entry?.date ?: today) }
    var weight by rememberSaveable { mutableStateOf(entry?.weightKg?.let(formatter::bodyNumber).orEmpty()) }
    var bodyFat by rememberSaveable { mutableStateOf(entry?.bodyFatPercent?.let(formatter::tenthsOrWhole).orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var picksDay by remember { mutableStateOf(false) }
    val weightFocus = remember { FocusRequester() }
    val weightProblem = stringResource(R.string.weight_range, formatter.body(WEIGHTS.start), formatter.body(WEIGHTS.endInclusive))
    val bodyFatProblem = stringResource(R.string.body_fat_range)
    val replaced = log.entries.firstOrNull { it.date == date && it.id != entry?.id }

    fun save(): Boolean {
        val typedWeight = weight.trim()
        val typedFat = bodyFat.trim()
        val kilograms = if (typedWeight.isEmpty()) null else formatter.kilograms(typedWeight)?.takeIf { it in WEIGHTS }
        if (typedWeight.isNotEmpty() && kilograms == null) {
            problem = weightProblem
            return false
        }
        val percent = if (typedFat.isEmpty()) null else WeightFormatter.number(typedFat)?.takeIf { it in BODY_FATS }
        if (typedFat.isNotEmpty() && percent == null) {
            problem = bodyFatProblem
            return false
        }
        actions.saveWeighIn(date, kilograms, percent, entry?.id)
        return true
    }

    FormSheet(
        stringResource(if (entry == null) R.string.add_weigh_in else R.string.weigh_in),
        canSave = weight.isNotBlank() || bodyFat.isNotBlank(),
        onSave = ::save,
        onDismiss = onDismiss,
    ) {
        if (entry == null) LaunchedEffect(Unit) { runCatching { weightFocus.requestFocus() } }
        FormCard {
            ProfileRow(stringResource(R.string.day), Dates.short(date, today)) { picksDay = true }
            Hairline()
            NumberRow(stringResource(R.string.weight), weight, formatter.unit.symbol, {
                weight = it
                problem = null
            }, Modifier.focusRequester(weightFocus))
            Hairline()
            NumberRow(stringResource(R.string.body_fat), bodyFat, "%", {
                bodyFat = it
                problem = null
            })
        }
        problem?.let { Footer(it, isProblem = true) }
        replaced?.let { Footer(stringResource(R.string.replaces_weigh_in, Dates.dayAndMonth(it.date))) }
        Footer(stringResource(R.string.weigh_in_footer))
        if (entry != null) {
            FormCard(Modifier.padding(top = 24.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            actions.deleteWeighIn(entry.id)
                            onDismiss()
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.delete_weigh_in), color = colors.error, fontSize = 16.sp)
                }
            }
        }
    }
    if (picksDay) {
        DayDialog(date, today, latest = today, earliest = null, onPick = { date = it }, onDismiss = { picksDay = false })
    }
}

/** Asks for a height (the iPhone's `HeightSheet`): in centimetres, or in feet and inches for people who weigh in pounds. */
@Composable
private fun HeightSheet(profile: BodyProfile, formatter: HeightFormatter, actions: BodyActions, onDismiss: () -> Unit) {
    val colors = kitaeColors
    val parts = profile.heightCm?.let(HeightFormatter::feetAndInches)
    var centimetres by rememberSaveable { mutableStateOf(profile.heightCm?.let { "${Math.round(it)}" }.orEmpty()) }
    var feet by rememberSaveable { mutableStateOf(parts?.first?.toString().orEmpty()) }
    var inches by rememberSaveable { mutableStateOf(parts?.second?.toString().orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val rangeProblem = stringResource(if (formatter.usesFeet) R.string.height_range_feet else R.string.height_range_cm)

    fun save(): Boolean {
        val height = if (formatter.usesFeet) {
            WeightFormatter.number(feet)?.toInt()?.let { HeightFormatter.centimetres(it, WeightFormatter.number(inches) ?: 0.0) }
        } else {
            WeightFormatter.number(centimetres)
        }
        if (height == null || height !in HEIGHTS) {
            problem = rangeProblem
            return false
        }
        actions.saveProfile(profile.copy(heightCm = height))
        return true
    }

    FormSheet(stringResource(R.string.height), canSave = true, onSave = ::save, onDismiss = onDismiss) {
        FormCard {
            if (formatter.usesFeet) {
                NumberRow(stringResource(R.string.feet), feet, "ft", {
                    feet = it
                    problem = null
                })
                Hairline()
                NumberRow(stringResource(R.string.inches), inches, "in", {
                    inches = it
                    problem = null
                })
            } else {
                NumberRow(stringResource(R.string.height), centimetres, "cm", {
                    centimetres = it
                    problem = null
                })
            }
        }
        problem?.let { Footer(it, isProblem = true) }
        if (profile.heightCm != null) {
            FormCard(Modifier.padding(top = 24.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            actions.saveProfile(profile.copy(heightCm = null))
                            onDismiss()
                        }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.clear_height), color = colors.error, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Asks for a date of birth, from 1900 to five years ago, for the age energy estimates use. */
@Composable
private fun BirthDateDialog(profile: BodyProfile, today: Long, actions: BodyActions, onDismiss: () -> Unit) {
    val latest = LocalDate.ofEpochDay(today).minusYears(5).toEpochDay()
    val start = profile.birthDate ?: LocalDate.ofEpochDay(today).minusYears(30).toEpochDay()
    DayDialog(
        start,
        today,
        latest = latest,
        earliest = LocalDate.of(1900, 1, 1).toEpochDay(),
        onPick = { actions.saveProfile(profile.copy(birthDate = it)) },
        onDismiss = onDismiss,
        onClear = profile.birthDate?.let { { actions.saveProfile(profile.copy(birthDate = null)) } },
    )
}

/** Android's date picker, for a day from [earliest] to [latest]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDialog(day: Long, today: Long, latest: Long, earliest: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null) {
    val colors = kitaeColors
    val lastYear = LocalDate.ofEpochDay(latest).year
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = day * DAY_MILLIS,
        yearRange = (earliest?.let { LocalDate.ofEpochDay(it).year } ?: (LocalDate.ofEpochDay(today).year - 10))..lastYear,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val picked = Math.floorDiv(utcTimeMillis, DAY_MILLIS)
                return picked <= latest && (earliest == null || picked >= earliest)
            }

            override fun isSelectableYear(year: Int): Boolean = year <= lastYear
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Math.floorDiv(it, DAY_MILLIS)) }
                onDismiss()
            }) { Text(stringResource(R.string.ok), color = colors.olive) }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text(stringResource(R.string.clear_birth_date), color = colors.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.olive) }
            }
        },
        colors = DatePickerDefaults.colors(containerColor = colors.field),
    ) {
        DatePicker(
            picker,
            colors = DatePickerDefaults.colors(
                containerColor = colors.field,
                selectedDayContainerColor = colors.olive,
                selectedDayContentColor = colors.onOlive,
                todayDateBorderColor = colors.olive,
                todayContentColor = colors.olive,
                selectedYearContainerColor = colors.olive,
                selectedYearContentColor = colors.onOlive,
            ),
        )
    }
}
