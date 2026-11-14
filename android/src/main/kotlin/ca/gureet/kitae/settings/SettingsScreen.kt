// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.SettingsActions
import ca.gureet.kitae.app.TextSize
import ca.gureet.kitae.backup.BackupProblem
import ca.gureet.kitae.exercises.Footer
import ca.gureet.kitae.exercises.FormCard
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.ui.components.BigTitleBar
import ca.gureet.kitae.ui.components.ConfirmDialog
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.theme.kitaeColors

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
 * its alerts, and the summary after a workout; the theme and the text size; backups, with
 * Android's folder picker; Reset Kamae; and the version.
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
    textSize: TextSize? = null,
) {
    val colors = kitaeColors
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var confirmsReset by remember { mutableStateOf(false) }

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
            FormCard { WorkoutRows(settings, actions) }
            Footer(stringResource(R.string.decimals_footer))

            Caption(stringResource(R.string.caption_appearance))
            FormCard { AppearanceRows(settings, textSize, actions) }

            Caption(stringResource(R.string.caption_backups))
            FormCard { BackupRows(settings, backup, actions) }
            val last = backup.place.lastBackupSeconds
            Footer(
                when {
                    backup.lastProblem != null -> stringResource(R.string.last_backup_failed, backupProblemText(backup.lastProblem))
                    settings.backUpAfterWorkouts && backup.place.folderUri == null -> stringResource(R.string.back_up_needs_folder)
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

    BackupDialogs(backup, actions)
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

@Composable
private fun Caption(text: String, first: Boolean = false) {
    SectionCaption(text, Modifier.padding(start = 8.dp, top = if (first) 0.dp else 20.dp, bottom = 8.dp))
}
