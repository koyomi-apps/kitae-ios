// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import ca.gureet.kitae.R
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.SettingsActions
import ca.gureet.kitae.app.TextSize
import ca.gureet.kitae.backup.BackupProblem
import ca.gureet.kitae.backup.Backups
import ca.gureet.kitae.exercises.ChoiceRow
import ca.gureet.kitae.exercises.SwitchRow
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.AppTheme
import ca.gureet.kitae.model.DecimalStyle
import ca.gureet.kitae.model.WeightUnit
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.workout.restChoices
import kotlin.math.roundToInt

/**
 * The rows Settings and the welcome share (the iPhone's `SettingRows` and `BackupRows`), each
 * group to go in a card.
 */

/** Units, decimals and the default rest; in Settings, also the rest alerts and the summary's switches. */
@Composable
fun WorkoutRows(settings: AppSettings, actions: SettingsActions, full: Boolean = true) {
    val context = LocalContext.current
    ChoiceBarRow(
        stringResource(R.string.units),
        listOf(WeightUnit.Kilograms to "kg", WeightUnit.Pounds to "lb"),
        settings.unit,
    ) { unit -> actions.updateSettings { it.copy(unit = unit) } }
    Hairline()
    ChoiceBarRow(
        stringResource(R.string.decimals),
        listOf(DecimalStyle.Automatic to stringResource(R.string.decimals_auto), DecimalStyle.Point to "22.5", DecimalStyle.Comma to "22,5"),
        settings.decimals,
    ) { decimals -> actions.updateSettings { it.copy(decimals = decimals) } }
    Hairline()
    val off = stringResource(R.string.rest_off)
    ChoiceRow(
        stringResource(R.string.default_rest),
        if (settings.defaultRestSeconds > 0) restLabel(settings.defaultRestSeconds) else off,
        listOf(off to 0) + restChoices.map { restLabel(it) to it },
    ) { seconds -> actions.updateSettings { it.copy(defaultRestSeconds = seconds) } }
    if (!full) return
    Hairline()
    SwitchRow(stringResource(R.string.rest_timer_alerts), settings.restAlerts) { on -> actions.updateSettings { it.copy(restAlerts = on) } }
    if (settings.restAlerts && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        Hairline()
        ActionRow(stringResource(R.string.allow_alerts)) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    Hairline()
    SwitchRow(stringResource(R.string.workout_summary), settings.showsWorkoutSummary) { on -> actions.updateSettings { it.copy(showsWorkoutSummary = on) } }
    Hairline()
    SwitchRow(stringResource(R.string.new_bests_in_summary), settings.showsNewBests, enabled = settings.showsWorkoutSummary) { on ->
        actions.updateSettings { it.copy(showsNewBests = on) }
    }
}

/**
 * The theme, and the text size: the phone's, or Kamae's own from a slider between a small and a
 * big "A". Moving the slider gives Kamae a size of its own; following the phone again keeps the
 * slider on the size nearest the phone's.
 */
@Composable
fun AppearanceRows(settings: AppSettings, textSize: TextSize?, actions: SettingsActions) {
    val colors = kitaeColors
    ChoiceBarRow(
        stringResource(R.string.theme),
        listOf(
            AppTheme.System to stringResource(R.string.theme_system),
            AppTheme.Light to stringResource(R.string.theme_light),
            AppTheme.Dark to stringResource(R.string.theme_dark),
        ),
        settings.theme,
    ) { theme -> actions.updateSettings { it.copy(theme = theme) } }
    Hairline()
    // The phone's own scale, not the one Kamae gives its text.
    val shown = textSize ?: TextSize.nearest(LocalConfiguration.current.fontScale)
    SwitchRow(stringResource(R.string.same_text_size), textSize == null) { same -> actions.setTextSize(if (same) null else shown) }
    Hairline()
    val names = textSizeNames()
    val label = stringResource(R.string.text_size)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Always the same sizes, so the slider stays put while the text changes size.
        Text("A", Modifier.clearAndSetSemantics {}, color = colors.inkSoft, fontSize = 13.sp / fontScale(), fontWeight = FontWeight.SemiBold)
        Slider(
            value = shown.ordinal.toFloat(),
            onValueChange = { actions.setTextSize(TextSize.entries[it.roundToInt().coerceIn(0, TextSize.entries.lastIndex)]) },
            modifier = Modifier
                .weight(1f)
                .alpha(if (textSize == null) 0.45f else 1f)
                .semantics {
                    contentDescription = label
                    stateDescription = names.getValue(shown)
                },
            valueRange = 0f..TextSize.entries.lastIndex.toFloat(),
            steps = TextSize.entries.size - 2,
            colors = SliderDefaults.colors(thumbColor = colors.olive, activeTrackColor = colors.olive, inactiveTrackColor = colors.field),
        )
        Text("A", Modifier.clearAndSetSemantics {}, color = colors.inkSoft, fontSize = 22.sp / fontScale(), fontWeight = FontWeight.SemiBold)
    }
}

/** The font scale text is drawn at here, to keep the slider's two "A"s one size. */
@Composable
private fun fontScale(): Float = LocalDensity.current.fontScale

@Composable
private fun textSizeNames(): Map<TextSize, String> = TextSize.entries.associateWith {
    stringResource(
        when (it) {
            TextSize.ExtraSmall -> R.string.text_size_extra_small
            TextSize.Small -> R.string.text_size_small
            TextSize.Medium -> R.string.text_size_medium
            TextSize.Standard -> R.string.text_size_standard
            TextSize.Large -> R.string.text_size_large
            TextSize.ExtraLarge -> R.string.text_size_extra_large
            TextSize.Largest -> R.string.text_size_largest
        },
    )
}

/**
 * Backing up after each workout, the folder backups go in, Back up now (in Settings), and
 * Restore from a backup, with Android's pickers.
 */
@Composable
fun BackupRows(settings: AppSettings, backup: BackupState, actions: SettingsActions, offersBackUpNow: Boolean = true) {
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { actions.setBackupFolder(it.toString()) }
    }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.openBackup(it.toString()) }
    }
    SwitchRow(stringResource(R.string.back_up_after_workouts), settings.backUpAfterWorkouts) { on -> actions.updateSettings { it.copy(backUpAfterWorkouts = on) } }
    Hairline()
    ValueRow(stringResource(R.string.backup_folder), backup.folderName ?: stringResource(R.string.choose_folder)) { chooseFolder.launch(null) }
    if (offersBackUpNow) {
        Hairline()
        ActionRow(stringResource(R.string.back_up_now), enabled = backup.place.folderUri != null && !backup.isWorking, working = backup.isWorking) {
            actions.backUpNow()
        }
    }
    Hairline()
    ActionRow(stringResource(R.string.restore_backup), enabled = !backup.isWorking, working = backup.isWorking && !offersBackUpNow) {
        chooseBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }
}

/** "Restore this backup?", and why one can't be restored. */
@Composable
fun BackupDialogs(backup: BackupState, actions: SettingsActions) {
    val colors = kitaeColors
    backup.restoring?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.restore_question),
            text = if (backup.place.folderUri != null) {
                stringResource(R.string.restore_text, pending.name, Backups.BEFORE_RESTORE_NAME)
            } else {
                stringResource(R.string.restore_text_no_folder, pending.name)
            },
            confirm = stringResource(R.string.restore),
            onConfirm = actions::restoreBackup,
            onDismiss = actions::cancelRestore,
        )
    }
    backup.restoreProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = actions::dismissBackupProblem,
            confirmButton = { TextButton(onClick = actions::dismissBackupProblem) { Text(stringResource(R.string.ok), color = colors.olive) } },
            title = {
                Text(
                    stringResource(if (problem == BackupProblem.WorkoutInProgress) R.string.cant_restore_now else R.string.couldnt_open_that),
                    fontSize = 22.sp,
                )
            },
            text = { Text(backupProblemText(problem), fontSize = 15.sp) },
            containerColor = colors.field,
            titleContentColor = colors.ink,
            textContentColor = colors.inkSoft,
        )
    }
}

/**
 * A few choices side by side in a Field capsule, the picked one OliveSoft and bold (the iPhone's
 * `ChoiceBar`).
 */
@Composable
fun <T> ChoiceBar(choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Row(
        modifier.background(colors.field, CircleShape).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, text) in choices) {
            val picked = value == selected
            Box(
                Modifier
                    .height(32.dp)
                    .widthIn(min = 48.dp)
                    .background(if (picked) colors.oliveSoft else colors.field, CircleShape)
                    .selectable(selected = picked, role = Role.RadioButton) { if (!picked) onSelect(value) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (picked) colors.onOliveSoft else colors.ink,
                    style = KitaeType.chip.copy(fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A choice bar beside the setting's name. */
@Composable
internal fun <T> ChoiceBarRow(label: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = kitaeColors.ink, fontSize = 16.sp)
        ChoiceBar(choices, selected, onSelect)
    }
}

/** A setting's name and its value, opening something when tapped. */
@Composable
internal fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    val colors = kitaeColors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(start = 18.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
        Text(value, color = colors.inkSoft, style = KitaeType.chip, maxLines = 1)
        if (onClick != null) KamaeIcon(R.drawable.ic_chevron_forward, null, tint = colors.inkSoft, size = 20.dp)
    }
}

/** A button in a form: olive, or red for what deletes. */
@Composable
internal fun ActionRow(text: String, enabled: Boolean = true, destructive: Boolean = false, working: Boolean = false, onClick: () -> Unit) {
    val colors = kitaeColors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f),
            color = when {
                !enabled -> colors.inkFaint
                destructive -> colors.error
                else -> colors.olive
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        if (working) CircularProgressIndicator(Modifier.height(20.dp).widthIn(max = 20.dp), color = colors.olive, strokeWidth = 2.dp)
    }
}
