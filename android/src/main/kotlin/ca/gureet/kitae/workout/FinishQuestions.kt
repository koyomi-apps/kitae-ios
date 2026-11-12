// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.kitae.R
import ca.gureet.kitae.database.FinishMode
import ca.gureet.kitae.model.WorkoutProgress
import ca.gureet.kitae.ui.theme.kitaeColors

/**
 * What Finish asks before a workout ends, in the same order as Kitae for Android and the iPhone
 * (`FinishQuestions.swift`): first what happens to the sets that weren't done, then whether sets
 * added during the workout join the day for next time.
 */
sealed interface FinishQuestion {
    /** Nothing was done: keep the workout as skipped, or discard it. */
    data object NothingDone : FinishQuestion

    /** Some sets weren't done: keep them as skipped, or remove them from the plan too. */
    data class Unfinished(val count: Int) : FinishQuestion

    /** Sets were added and done: add them to the day, or keep them in this workout only. */
    data class AddedSets(val count: Int, val mode: FinishMode, override val isFirst: Boolean) : FinishQuestion

    /** Everything planned was done, but another day is being started. */
    data object Confirm : FinishQuestion

    /** Whether this is the first question asked, rather than a follow-up. */
    val isFirst: Boolean
        get() = true

    companion object {
        /** The first question for this workout, or null when there's nothing to ask. */
        fun first(progress: WorkoutProgress, startingAnotherDay: Boolean): FinishQuestion? = when {
            progress.notDoneCount > 0 -> if (progress.doneCount == 0) NothingDone else Unfinished(progress.notDoneCount)
            progress.addedDoneCount > 0 -> AddedSets(progress.addedDoneCount, FinishMode.KeepSkipped, isFirst = true)
            startingAnotherDay -> Confirm
            else -> null
        }
    }
}

/**
 * Asks [question] about the workout of [dayName]. [onQuestion] gets the follow-up, or null when
 * the questions are over; [onFinish] ends the workout. With [nextDayName], another day starts
 * afterwards, and [onContinue] goes back to the workout instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishQuestionDialog(
    question: FinishQuestion,
    dayName: String,
    addedDoneCount: Int,
    onQuestion: (FinishQuestion?) -> Unit,
    onFinish: (FinishMode, Boolean) -> Unit,
    nextDayName: String? = null,
    onContinue: (() -> Unit)? = null,
) {
    val colors = kitaeColors
    val title = when {
        nextDayName != null -> stringResource(R.string.workout_in_progress)
        question is FinishQuestion.AddedSets -> stringResource(R.string.keep_new_sets_question)
        else -> stringResource(R.string.finish_question)
    }
    val parts = buildList {
        if (nextDayName != null && question.isFirst) add(stringResource(R.string.not_finished_start_other, dayName, nextDayName))
        when (question) {
            FinishQuestion.NothingDone -> add(stringResource(R.string.nothing_done))
            is FinishQuestion.Unfinished -> add(pluralStringResource(R.plurals.sets_not_done, question.count, question.count, dayName))
            is FinishQuestion.AddedSets -> add(pluralStringResource(R.plurals.sets_added, question.count, question.count, dayName))
            FinishQuestion.Confirm -> Unit
        }
    }
    fun finishing(mode: FinishMode, addNewSets: Boolean) {
        onQuestion(null)
        onFinish(mode, addNewSets)
    }
    fun askAboutAddedSets(mode: FinishMode) {
        if (addedDoneCount > 0) onQuestion(FinishQuestion.AddedSets(addedDoneCount, mode, isFirst = false)) else finishing(mode, false)
    }
    BasicAlertDialog(onDismissRequest = { onQuestion(null) }) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.field, modifier = Modifier.semantics { paneTitle = title }) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(title, color = colors.ink, fontSize = 24.sp, lineHeight = 32.sp)
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString("\n\n"), Modifier.padding(top = 16.dp), color = colors.inkSoft, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    @Composable
                    fun Choice(text: String, onClick: () -> Unit) {
                        TextButton(onClick = onClick) { Text(text, color = colors.olive, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                    when (question) {
                        FinishQuestion.NothingDone -> {
                            Choice(stringResource(R.string.keep_as_skipped)) { finishing(FinishMode.KeepSkipped, false) }
                            Choice(stringResource(R.string.discard_workout)) { finishing(FinishMode.Discard, false) }
                        }
                        is FinishQuestion.Unfinished -> {
                            Choice(stringResource(R.string.keep_as_skipped)) { askAboutAddedSets(FinishMode.KeepSkipped) }
                            Choice(stringResource(R.string.remove_from_plan)) { askAboutAddedSets(FinishMode.RemoveFromPlan) }
                        }
                        is FinishQuestion.AddedSets -> {
                            Choice(stringResource(R.string.add_to_day, dayName)) { finishing(question.mode, true) }
                            Choice(stringResource(R.string.only_this_workout)) { finishing(question.mode, false) }
                        }
                        FinishQuestion.Confirm -> Choice(stringResource(R.string.finish_and_start, nextDayName.orEmpty())) {
                            finishing(FinishMode.KeepSkipped, false)
                        }
                    }
                    if (onContinue != null && question.isFirst) {
                        Choice(stringResource(R.string.continue_day, dayName)) {
                            onQuestion(null)
                            onContinue()
                        }
                    }
                    Choice(stringResource(R.string.cancel)) { onQuestion(null) }
                }
            }
        }
    }
}
