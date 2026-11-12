// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.app.BackupState
import ca.gureet.kitae.app.SettingsActions
import ca.gureet.kitae.app.TextSize
import ca.gureet.kitae.exercises.Footer
import ca.gureet.kitae.exercises.FormCard
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.model.WeightUnit
import ca.gureet.kitae.settings.AppearanceRows
import ca.gureet.kitae.settings.BackupDialogs
import ca.gureet.kitae.settings.BackupRows
import ca.gureet.kitae.settings.WorkoutRows
import ca.gureet.kitae.ui.components.DisplayText
import ca.gureet.kitae.ui.components.Hairline
import ca.gureet.kitae.ui.components.MauveButton
import ca.gureet.kitae.ui.components.SectionCaption
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/** A set to show weights by, with a round weight in each unit. */
private class Example(val name: Int, val kilograms: Double, val pounds: Double, val reps: Int)

private val EXAMPLES = listOf(
    Example(R.string.example_bench_press, 62.5, 137.5, 8),
    Example(R.string.example_squat, 102.5, 225.0, 5),
)

/**
 * What Kamae shows first (the iPhone's `WelcomeView`): light or dark and how big its text is, how
 * weights are written and the usual rest, with two example sets to judge them by, and backups. A
 * backup can be restored from here, for someone coming from another phone or from Kitae on
 * iPhone. It's all in Settings too. Get started waits while a backup is being restored.
 */
@Composable
fun WelcomeScreen(
    settings: AppSettings,
    textSize: TextSize?,
    backup: BackupState,
    formatter: WeightFormatter,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    Box(modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 120.dp),
        ) {
            val spoken = stringResource(R.string.welcome_spoken)
            Column(Modifier.padding(horizontal = 8.dp).clearAndSetSemantics {
                contentDescription = spoken
                heading()
            }) {
                Text(stringResource(R.string.caption_welcome_to), color = colors.inkSoft, style = KitaeType.caption)
                DisplayText(stringResource(R.string.app_name), KitaeType.dayHero, colors.olive, minSize = 48.sp, maxLines = 1)
            }
            Text(
                stringResource(R.string.welcome_text),
                Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
                color = colors.inkSoft,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )

            Caption(stringResource(R.string.caption_example))
            FormCard {
                EXAMPLES.forEachIndexed { index, example ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(example.name), Modifier.weight(1f), color = colors.ink, fontSize = 16.sp)
                        val weight = if (formatter.unit == WeightUnit.Kilograms) example.kilograms else example.pounds
                        Text("${formatter.number(weight)} ${formatter.unit.symbol} × ${example.reps}", color = colors.inkSoft, style = KitaeType.step)
                    }
                }
            }

            Caption(stringResource(R.string.caption_appearance))
            FormCard { AppearanceRows(settings, textSize, actions) }

            Caption(stringResource(R.string.caption_workouts))
            FormCard { WorkoutRows(settings, actions, full = false) }
            Footer(stringResource(R.string.decimals_footer))

            Caption(stringResource(R.string.caption_backups))
            FormCard { BackupRows(settings, backup, actions, offersBackUpNow = false) }
            Footer(stringResource(R.string.welcome_backups_footer))
        }
        // Get started, over the end of the list as it fades into Paper; not while a backup is
        // being restored, so its answer isn't lost.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.paper.copy(alpha = 0f), colors.paper.copy(alpha = 0.86f), colors.paper)))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 12.dp),
        ) {
            val working = backup.isWorking
            MauveButton(
                onClick = { if (!working) actions.finishWelcome() },
                modifier = Modifier.fillMaxWidth().alpha(if (working) 0.5f else 1f),
            ) {
                Text(stringResource(R.string.get_started), style = KitaeType.bigButton)
            }
        }
    }
    BackupDialogs(backup, actions)
}

@Composable
private fun Caption(text: String) {
    SectionCaption(text, Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp))
}
