// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import ca.gureet.kitae.R
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.SettingsActions
import ca.gureet.kitae.backup.BackupProblem
import ca.gureet.kitae.backup.Backups
import ca.gureet.kitae.exercises.ChoiceRow
import ca.gureet.kitae.exercises.FormCard
import ca.gureet.kitae.exercises.Footer
import ca.gureet.kitae.exercises.SwitchRow
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.AppTheme
import ca.gureet.kitae.model.DecimalStyle
import ca.gureet.kitae.model.WeightUnit
import ca.gureet.kitae.model.restLabel
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors
import ca.gureet.kitae.workout.restChoices

/** Where Kamae's code is. */
const val SOURCE_CODE = "https://github.com/koyomi-apps/kitae-ios"

/** A backup problem in the iPhone's words. */
@Composable
fun backupProblemText(problem: BackupProblem): String = stringResource(
    when (problem) {
        BackupProblem.NotKitae -> R.string.problem_not_kitae
        BackupProblem.OlderVersion -> R.string.problem_older
        BackupProblem.NewerVersion -> R.string.problem_newer
        BackupProblem.Damaged -> R.string.problem_damaged
        BackupProblem.WorkoutInProgress -> R.string.problem_workout
        BackupProblem.FolderUnavailable -> R.string.problem_folder
    },
)

/**
 * Settings, pushed from You (the iPhone's `SettingsView`): units, decimals, the default rest and
 * its alerts; the theme; backups, with Android's folder picker; Reset Kamae; and the version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    backup: BackupState,
    version: String,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var confirmsReset by remember { mutableStateOf(false) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { actions.setBackupFolder(it.toString()) }
    }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.openBackup(it.toString()) }
    }
    val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()

    Column(modifier.fillMaxSize()) {
        BigTitleBar(stringResource(R.string.settings), scrollBehavior, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Caption(stringResource(R.string.caption_workouts), first = true)
            FormCard {
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
                Hairline()
                SwitchRow(stringResource(R.string.rest_timer_alerts), settings.restAlerts) { on -> actions.updateSettings { it.copy(restAlerts = on) } }
                if (settings.restAlerts && !notificationsAllowed) {
                    Hairline()
                    ActionRow(stringResource(R.string.allow_alerts)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }
            Footer(stringResource(R.string.decimals_footer))

            Caption(stringResource(R.string.caption_appearance))
            FormCard {
                ChoiceBarRow(
                    stringResource(R.string.theme),
                    listOf(
                        AppTheme.System to stringResource(R.string.theme_system),
                        AppTheme.Light to stringResource(R.string.theme_light),
                        AppTheme.Dark to stringResource(R.string.theme_dark),
                    ),
                    settings.theme,
                ) { theme -> actions.updateSettings { it.copy(theme = theme) } }
            }

            Caption(stringResource(R.string.caption_backups))
            val hasFolder = backup.place.folderUri != null
            FormCard {
                SwitchRow(stringResource(R.string.back_up_after_workouts), settings.backUpAfterWorkouts) { on -> actions.updateSettings { it.copy(backUpAfterWorkouts = on) } }
                Hairline()
                ValueRow(stringResource(R.string.backup_folder), backup.folderName ?: stringResource(R.string.choose_folder)) { chooseFolder.launch(null) }
                Hairline()
                ActionRow(stringResource(R.string.back_up_now), enabled = hasFolder && !backup.isWorking, working = backup.isWorking) { actions.backUpNow() }
                Hairline()
                ActionRow(stringResource(R.string.restore_backup), enabled = !backup.isWorking) {
                    chooseBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
            }
            val last = backup.place.lastBackupSeconds
            Footer(
                when {
                    backup.lastProblem != null -> stringResource(R.string.last_backup_failed, backupProblemText(backup.lastProblem))
                    settings.backUpAfterWorkouts && !hasFolder -> stringResource(R.string.back_up_needs_folder)
                    last != null -> stringResource(
                        R.string.last_backup,
                        DateUtils.getRelativeDateTimeString(context, last * 1000, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString(),
                    )
                    else -> stringResource(R.string.no_backups_yet)
                },
                isProblem = backup.lastProblem != null,
            )

            Caption(stringResource(R.string.caption_reset))
            FormCard {
                ActionRow(stringResource(R.string.reset_kamae), destructive = true) { confirmsReset = true }
            }
            Footer(stringResource(R.string.reset_footer))

            Caption(stringResource(R.string.caption_about))
            FormCard {
                ValueRow(stringResource(R.string.version), version, onClick = null)
                Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { uriHandler.openUri(SOURCE_CODE) }
                        .heightIn(min = 52.dp)
                        .padding(start = 18.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.source_code), Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
                    KamaeIcon(R.drawable.ic_start, null, tint = colors.inkSoft, size = 20.dp)
                }
            }
        }
    }

    backup.restoring?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.restore_question),
            text = if (hasFolderFor(backup)) {
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
    if (confirmsReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_question),
            text = stringResource(R.string.reset_text),
            confirm = stringResource(R.string.reset),
            onConfirm = actions::resetKamae,
            onDismiss = { confirmsReset = false },
        )
    }
}

private fun hasFolderFor(backup: BackupState) = backup.place.folderUri != null

@Composable
private fun Caption(text: String, first: Boolean = false) {
    SectionCaption(text, Modifier.padding(start = 8.dp, top = if (first) 0.dp else 20.dp, bottom = 8.dp))
}

/**
 * A few choices side by side in a Field capsule, the picked one OliveSoft and bold (the iPhone's
 * `ChoiceBar`), beside the setting's name.
 */
@Composable
private fun <T> ChoiceBarRow(label: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = kitaeColors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
        Row(
            Modifier.background(colors.field, CircleShape).padding(4.dp).selectableGroup(),
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
}

/** A setting's name and its value, opening something when tapped. */
@Composable
private fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
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
private fun ActionRow(text: String, enabled: Boolean = true, destructive: Boolean = false, working: Boolean = false, onClick: () -> Unit) {
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
