// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.muscles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.model.WeightFormatter
import ca.gureet.kitae.ui.theme.KitaeType
import ca.gureet.kitae.ui.theme.kitaeColors

/** A muscle's name, as the iPhone's `Muscle.name` has it. */
@Composable
@ReadOnlyComposable
fun muscleName(muscle: Muscle): String = stringResource(
    when (muscle) {
        Muscle.Quads -> R.string.m_quads
        Muscle.Hamstrings -> R.string.m_hamstrings
        Muscle.Glutes -> R.string.m_glutes
        Muscle.Calves -> R.string.m_calves
        Muscle.Chest -> R.string.m_chest
        Muscle.Abs -> R.string.m_abs
        Muscle.Obliques -> R.string.m_obliques
        Muscle.Lats -> R.string.m_lats
        Muscle.Traps -> R.string.m_traps
        Muscle.LowerBack -> R.string.m_lower_back
        Muscle.FrontDelts -> R.string.m_front_delts
        Muscle.SideDelts -> R.string.m_side_delts
        Muscle.RearDelts -> R.string.m_rear_delts
        Muscle.Biceps -> R.string.m_biceps
        Muscle.Triceps -> R.string.m_triceps
        Muscle.Forearms -> R.string.m_forearms
        Muscle.UpperBack -> R.string.m_upper_back
        Muscle.Adductors -> R.string.m_adductors
        Muscle.Abductors -> R.string.m_abductors
    },
)

/** "Chest, front delts, triceps": the first capitalised, the others not. */
@Composable
@ReadOnlyComposable
fun muscleList(muscles: List<Muscle>): String =
    muscles.mapIndexed { index, muscle -> muscleName(muscle).let { if (index == 0) it else it.lowercase() } }.joinToString(", ")

/** What TalkBack reads for an exercise's muscles: "Works most: Chest. Helped by: Front delts, triceps". */
@Composable
@ReadOnlyComposable
fun spokenMuscles(work: MuscleWork): String {
    val most = stringResource(R.string.works_most, muscleList(work.primary))
    if (work.secondary.isEmpty()) return most
    return most + ". " + stringResource(R.string.helped_by, muscleList(work.secondary))
}

/** A shape list from `ExerciseMuscles.json` as a path: each shape a start point, then cubic Béziers. */
fun musclePath(shapes: List<List<Double>>): Path = Path().apply {
    for (shape in shapes) {
        if (shape.size < 8) continue
        moveTo(shape[0].toFloat(), shape[1].toFloat())
        var i = 2
        while (i + 5 < shape.size) {
            cubicTo(
                shape[i].toFloat(), shape[i + 1].toFloat(),
                shape[i + 2].toFloat(), shape[i + 3].toFloat(),
                shape[i + 4].toFloat(), shape[i + 5].toFloat(),
            )
            i += 6
        }
        close()
    }
}

/**
 * One side of the body (the iPhone's `MuscleFigure`): [work]'s primary muscles in olive, its
 * secondary ones lighter, the rest idle, with a thin gap round each in the body's colour.
 */
@Composable
fun MuscleFigure(map: MuscleMap, side: MuscleMap.Side, work: MuscleWork, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val outline = musclePath(map.body)
    val muscles = map.muscles(side).map { (muscle, shapes) -> muscle to musclePath(shapes) }
    Canvas(modifier.aspectRatio((map.width / map.height).toFloat())) {
        val scale = minOf(size.width / map.width.toFloat(), size.height / map.height.toFloat())
        translate((size.width - map.width.toFloat() * scale) / 2, (size.height - map.height.toFloat() * scale) / 2) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                drawPath(outline, colors.muscleMapBase)
                clipPath(outline) {
                    for ((muscle, path) in muscles) {
                        val color = when (muscle) {
                            in work.primary -> colors.olive
                            in work.secondary -> colors.muscleMapSecondary
                            else -> colors.muscleMapIdle
                        }
                        drawPath(path, color)
                        drawPath(path, colors.muscleMapBase, style = Stroke(width = 1.1f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/** The front and the back side by side, 172 tall. */
@Composable
private fun BodyPair(map: MuscleMap, work: MuscleWork) {
    Row(Modifier.height(172.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MuscleFigure(map, MuscleMap.Side.Front, work)
        MuscleFigure(map, MuscleMap.Side.Back, work)
    }
}

@Composable
private fun MuscleGroupText(caption: String, text: String, color: Color) {
    val colors = kitaeColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
            Text(caption, color = colors.inkSoft, style = KitaeType.fieldCaption.copy(fontSize = 11.sp, lineHeight = 16.sp))
        }
        Text(text, color = colors.ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * The muscle map on an exercise's page, under its animation (the iPhone's `MuscleMapView`): the
 * body from the front and the back, and the muscles it works most and the ones that help.
 */
@Composable
fun MuscleMapCard(map: MuscleMap, work: MuscleWork, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val spoken = spokenMuscles(work)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(24.dp))
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BodyPair(map, work)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (work.primary.isNotEmpty()) MuscleGroupText(stringResource(R.string.caption_primary), muscleList(work.primary), colors.olive)
            if (work.secondary.isNotEmpty()) {
                MuscleGroupText(stringResource(R.string.caption_secondary), muscleList(work.secondary), colors.muscleMapSecondary)
            }
        }
    }
}

/**
 * An exercise's muscles under its name in the workout (the iPhone's `MuscleLine`): an olive
 * swatch and the muscles it works most, then a lighter one and the ones that help, one under the
 * other when they don't fit side by side.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleLine(work: MuscleWork, modifier: Modifier = Modifier) {
    val colors = kitaeColors
    val spoken = spokenMuscles(work)
    FlowRow(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((muscles, color) in listOf(work.primary to colors.olive, work.secondary to colors.muscleMapSecondary)) {
            if (muscles.isEmpty()) continue
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(color, RoundedCornerShape(2.5.dp)))
                Text(muscleList(muscles), color = colors.inkSoft, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/**
 * Muscles worked on the map (the iPhone's `MuscleVolumeCard`): those with the most sets in olive,
 * the others lighter, each with its sets. You shows the last 7 days', the summary a workout's.
 */
@Composable
fun MuscleVolumeCard(
    subtitle: String,
    volume: MuscleVolume,
    map: MuscleMap?,
    formatter: WeightFormatter,
    modifier: Modifier = Modifier,
    empty: String = stringResource(R.string.no_sets_yet),
) {
    val colors = kitaeColors
    val shading = volume.shading
    val names = Muscle.entries.associateWith { muscleName(it) }
    fun list(muscles: List<Muscle>) = muscles.mapIndexed { index, muscle ->
        val name = names.getValue(muscle).let { if (index == 0) it else it.lowercase() }
        "$name ${formatter.tenthsOrWhole(volume.sets[muscle] ?: 0.0)}"
    }.joinToString(", ")
    val spoken = if (volume.totalSets == 0) {
        stringResource(R.string.muscles_spoken_empty, empty)
    } else {
        stringResource(R.string.muscles_spoken, subtitle.lowercase(), list(shading.primary)) +
            if (shading.secondary.isNotEmpty()) ". " + stringResource(R.string.muscles_spoken_fewer, list(shading.secondary)) else ""
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.muscles),
                Modifier.weight(1f).alignByBaseline().semantics { heading() },
                color = colors.ink,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(subtitle, Modifier.alignByBaseline(), color = colors.inkSoft, style = KitaeType.sectionCaption)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (map != null) BodyPair(map, shading)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (volume.totalSets == 0) {
                    Text(empty, color = colors.inkSoft, fontSize = 16.sp)
                } else {
                    if (shading.primary.isNotEmpty()) MuscleGroupText(stringResource(R.string.caption_most_sets), list(shading.primary), colors.olive)
                    if (shading.secondary.isNotEmpty()) {
                        MuscleGroupText(stringResource(R.string.caption_fewer_sets), list(shading.secondary), colors.muscleMapSecondary)
                    }
                }
            }
        }
        if (volume.totalSets > 0) {
            Text(
                stringResource(R.string.sets_count_note, pluralStringResource(R.plurals.set_total, volume.totalSets, volume.totalSets)),
                color = colors.inkSoft,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
