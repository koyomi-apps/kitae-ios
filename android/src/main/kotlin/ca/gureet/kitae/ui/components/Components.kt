// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

// Kamae's small parts, each with an iPhone twin (`Kitae/Theme/Components.swift`).

/** One of Kamae's icons, Material Symbols Rounded. Decorative when [contentDescription] is null. */
@Composable
fun KamaeIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = kitaeColors.ink,
    size: Dp = 24.dp,
) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

/** The small Space Mono captions, like ".up next" and ".exercises", lowercase with a leading dot. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, style: TextStyle = KitaeType.caption, color: Color = kitaeColors.inkSoft) {
    Text(text, modifier, color = color, style = style)
}

/** A list section's header, like ".set type". */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Caption(text, modifier, style = KitaeType.sectionCaption)
}

/** A day you can pick, in a row of days: Field, or OliveSoft with bold text when picked. */
@Composable
fun DayChip(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp)
            .semantics { selected = isSelected },
        shape = CircleShape,
        color = if (isSelected) colors.oliveSoft else colors.field,
        contentColor = if (isSelected) colors.onOliveSoft else colors.ink,
    ) {
        Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(title, style = KitaeType.chip.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
        }
    }
}

/** The big mauve buttons: Start, Continue and Finish, 58 tall. */
@Composable
fun MauveButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = kitaeColors
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp).semantics { role = Role.Button },
        shape = CircleShape,
        color = colors.mauve,
        contentColor = colors.onMauve,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A card: flat on Paper, corners 24, its rows split by hairlines. */
@Composable
fun KitaeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(kitaeColors.card, RoundedCornerShape(24.dp)),
        content = content,
    )
}

/** A thin line between rows, inset [start] from the card's edge. */
@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 18.dp) {
    Box(
        modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(1.dp)
            .background(kitaeColors.rule),
    )
}

/** A centred icon, title, line and button, for screens with nothing to show yet. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = kitaeColors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KamaeIcon(icon, null, tint = colors.inkFaint, size = 48.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, Modifier.widthIn(max = 320.dp), color = colors.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/**
 * Big Darker Grotesque text set as the Kamae canvas's CSS sets it: each line exactly as tall as
 * [style]'s line height, shorter than the font's own height, with the difference shared above and
 * below, so the next line and what follows sit where the canvas has them. The text shrinks in steps
 * down to [minSize] to fit in [maxLines].
 */
@Composable
fun DisplayText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSize: TextUnit = style.fontSize,
    maxLines: Int = 1,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val lines = remember(text, style, width, minSize, maxLines) {
            var size = style.fontSize
            var layout = measurer.measure(text, style.copy(fontSize = size), maxLines = maxLines, constraints = Constraints(maxWidth = width))
            while (layout.hasVisualOverflow && size.value - 4 >= minSize.value) {
                size = (size.value - 4).sp
                layout = measurer.measure(text, style.copy(fontSize = size), maxLines = maxLines, constraints = Constraints(maxWidth = width))
            }
            val lineHeight = (style.lineHeight.value * size.value / style.fontSize.value).sp
            (0 until layout.lineCount).map { line ->
                Triple(text.substring(layout.getLineStart(line), layout.getLineEnd(line, visibleEnd = true)).trimEnd(), size, lineHeight)
            }
        }
        Column(Modifier.semantics(mergeDescendants = true) {}.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
            for ((line, size, lineHeight) in lines) {
                val lineStyle = style.copy(fontSize = size, lineHeight = TextUnit.Unspecified, color = color)
                Text(
                    line,
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        val height = with(density) { lineHeight.roundToPx() }
                        layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
                    },
                    style = lineStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
