// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ca.gureet.kitae.R
import ca.gureet.kitae.model.RestTimer
import ca.gureet.kitae.ui.components.KamaeIcon
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * The rest between sets, in a MauveSoft capsule 58 tall (the iPhone's `RestTimerBar`): the time
 * left, pause or resume, −15s, +15s and skip. The time keeps the room its widest clock needs, so
 * it's never cut short.
 */
@Composable
fun RestBar(
    rest: RestTimer,
    now: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdd: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = kitaeColors
    val left = clockText(rest.remaining(now), countdown = true)
    val measurer = rememberTextMeasurer()
    val clockWidth = with(LocalDensity.current) { measurer.measure(rest.widestClock, KitaeType.restCountdown).size.width.toDp() }
    val description = stringResource(if (rest.isPaused) R.string.rest_paused else R.string.rest_left, left)
    Row(
        modifier.height(58.dp).background(colors.mauveSoft, CircleShape).padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left,
            Modifier
                .widthIn(min = clockWidth + 4.dp)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            color = colors.onMauveSoft,
            style = KitaeType.restCountdown,
            maxLines = 1,
        )
        Box(Modifier.weight(1f))
        if (rest.isPaused) {
            BarButton(stringResource(R.string.resume_rest), onResume, Modifier.width(44.dp)) {
                KamaeIcon(R.drawable.ic_play, null, tint = colors.onMauveSoft, size = 22.dp)
            }
        } else {
            BarButton(stringResource(R.string.pause_rest), onPause, Modifier.width(44.dp)) {
                KamaeIcon(R.drawable.ic_pause, null, tint = colors.onMauveSoft, size = 22.dp)
            }
        }
        BarButton(stringResource(R.string.less_rest_description), { onAdd(-15) }, Modifier.width(50.dp)) {
            Text(stringResource(R.string.less_rest), color = colors.onMauveSoft, style = KitaeType.chip)
        }
        BarButton(stringResource(R.string.more_rest_description), { onAdd(15) }, Modifier.width(50.dp)) {
            Text(stringResource(R.string.more_rest), color = colors.onMauveSoft, style = KitaeType.chip)
        }
        BarButton(stringResource(R.string.skip_rest), onSkip, Modifier.width(44.dp)) {
            KamaeIcon(R.drawable.ic_close, null, tint = colors.onMauveSoft, size = 22.dp)
        }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
