// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.gureet.kitae.app.AppState
import ca.gureet.kitae.app.KamaeRoot
import ca.gureet.kitae.app.Navigator
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.app.Tab
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.model.AppTheme
import ca.gureet.kitae.ui.theme.KamaeTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kamae's screens drawn off screen, light and dark, to compare with the Kamae canvas, and what
 * tapping them asks for. They're drawn on Android 14, as Robolectric needs Java 21 for later ones
 * and the build runs on Java 17. `./gradlew :android:recordRoborazziDebug` saves the pictures under
 * android/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordedActions()

    private fun show(state: AppState, theme: AppTheme = AppTheme.Light, navigator: Navigator = Navigator()) {
        compose.setContent {
            KamaeTheme(theme) { KamaeRoot(state, AppSettings(), actions, SamplePages, frozenNow = Samples.NOW, navigator = navigator) }
        }
        compose.waitForIdle()
    }

    /** [tab] showing, with [routes] opened on it. */
    private fun on(tab: Tab, vararg routes: Route) = Navigator(tab).apply { stack(tab).addAll(routes) }

    /** The whole screen, sheets and dialogs too. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) = captureScreenRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun today() {
        show(Samples.today)
        capture("today")
        compose.onNodeWithText("Pull A").performClick()
        compose.onNodeWithText("Start").performClick()
        assertEquals(listOf("pickDay 2", "start Push A"), actions.calls)
    }

    @Test
    fun todayDark() {
        show(Samples.today, AppTheme.Dark)
        capture("today-dark")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun todayOnATablet() {
        show(Samples.today)
        capture("today-tablet")
    }

    @Test
    fun workout() {
        show(Samples.inProgress)
        capture("workout")
        compose.onNodeWithContentDescription("Pause rest").performClick()
        compose.onNodeWithContentDescription("15 seconds more").performClick()
        compose.onAllNodesWithContentDescription("Mark set 01 done")[0].performClick()
        assertEquals(listOf("pauseRest", "addRest 15", "toggleDone 106"), actions.calls)
    }

    @Test
    fun workoutDark() {
        show(Samples.inProgress, AppTheme.Dark)
        capture("workout-dark")
    }

    @Test
    fun setOptions() {
        show(Samples.inProgress)
        compose.onAllNodesWithContentDescription("Set 03 options")[1].performClick()
        compose.waitForIdle()
        capture("set-options")
        compose.onNodeWithText("Failure").performClick()
        assertTrue(actions.calls.single().startsWith("updateSet 105 SetDraft(reps=12, weightKg=20.0, type=Failure"))
    }

    @Test
    fun finishing() {
        show(Samples.inProgress)
        compose.onNodeWithContentDescription("Finish workout").performClick()
        compose.waitForIdle()
        capture("finish")
        compose.onNodeWithText("Keep as skipped").performClick()
        assertEquals(listOf("finish KeepSkipped false"), actions.calls)
    }

    @Test
    fun minimized() {
        show(Samples.inProgress.copy(isWorkoutPresented = false))
        capture("minimized")
        compose.onNodeWithText("23:40 · 6/15 sets").performClick()
        assertEquals(listOf("presentWorkout true"), actions.calls)
    }

    @Test
    fun choosingAPlan() {
        show(Samples.today.copy(isChoosingPlan = true))
        compose.waitForIdle()
        capture("plans")
        compose.onNodeWithText("Upper Lower").performClick()
        assertEquals(listOf("selectPlan 2"), actions.calls)
    }

    @Test
    fun history() {
        show(Samples.inProgress.copy(isWorkoutPresented = false), navigator = on(Tab.History))
        capture("history")
        compose.onNodeWithText(".in progress · 6/15 sets").performClick()
        assertEquals(listOf("presentWorkout true"), actions.calls)
    }

    @Test
    fun historyDark() {
        show(Samples.inProgress.copy(isWorkoutPresented = false), AppTheme.Dark, on(Tab.History))
        capture("history-dark")
    }

    @Test
    fun pastWorkout() {
        show(Samples.today, navigator = on(Tab.History, Route.PastWorkout(6)))
        capture("past-workout")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete workout").performClick()
        compose.waitForIdle()
        capture("past-workout-delete")
    }

    @Test
    fun exercises() {
        show(Samples.today, navigator = on(Tab.Exercises))
        capture("exercises")
    }

    @Test
    fun exercisePage() {
        show(Samples.today, navigator = on(Tab.Exercises, Route.ExercisePage(101)))
        capture("exercise")
    }

    @Test
    fun exercisePageDark() {
        show(Samples.today, AppTheme.Dark, on(Tab.Exercises, Route.ExercisePage(101)))
        capture("exercise-dark")
    }

    @Test
    fun exerciseEditor() {
        show(Samples.today, navigator = on(Tab.Exercises, Route.ExercisePage(101)))
        compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
        capture("exercise-editor")
    }

    @Test
    fun planEditor() {
        show(Samples.today, navigator = on(Tab.Today, Route.PlanEditor(1, 1)))
        capture("plan-editor")
        compose.onAllNodesWithText("Add set")[0].performClick()
        assertEquals(listOf("addPlannedSet 1 SetDraft(reps=10, weightKg=20.0, type=Standard, restSeconds=null)"), actions.calls)
    }

    @Test
    fun plannedSet() {
        show(Samples.today, navigator = on(Tab.Today, Route.PlanEditor(1, 1)))
        compose.onAllNodesWithText("10 × 20.0 kg")[0].performClick()
        compose.waitForIdle()
        capture("planned-set")
    }

    @Test
    fun picker() {
        show(Samples.today, navigator = on(Tab.Today, Route.PlanEditor(1, 1), Route.Picker(1, 1)))
        capture("picker")
        compose.onNodeWithText("Lat Pull-down").performClick()
        compose.waitForIdle()
        assertEquals(listOf("addExerciseToDay 1 122"), actions.calls)
    }

    @Test
    fun you() {
        show(Samples.today, navigator = on(Tab.You))
        capture("you")
    }

    @Test
    fun youDark() {
        show(Samples.today, AppTheme.Dark, on(Tab.You))
        capture("you-dark")
    }

    @Test
    fun settings() {
        show(Samples.backedUp, navigator = on(Tab.You, Route.Settings))
        capture("settings")
        compose.onNodeWithText("lb").performClick()
        assertTrue(actions.calls.single().startsWith("updateSettings AppSettings(unit=Pounds"))
    }
}
