// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.gureet.kitae.app.KamaeRoot
import ca.gureet.kitae.app.Navigator
import ca.gureet.kitae.app.Route
import ca.gureet.kitae.app.Tab
import ca.gureet.kitae.model.AppSettings
import ca.gureet.kitae.ui.theme.KamaeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pages open on their tab's stack, and back goes through them, then to Today. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val navigator = Navigator(Tab.Exercises)

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun backGoesThroughThePagesThenToToday() {
        compose.setContent {
            KamaeTheme { KamaeRoot(Samples.today, AppSettings(), RecordedActions(), SamplePages, frozenNow = Samples.NOW, navigator = navigator) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Incline Bench").performClick()
        compose.waitForIdle()
        assertEquals(listOf(Route.Home(Tab.Exercises), Route.ExercisePage(101)), navigator.stack(Tab.Exercises).toList())
        compose.onNodeWithText("How to do it").assertExists()

        back()
        assertEquals(listOf(Route.Home(Tab.Exercises)), navigator.stack(Tab.Exercises).toList())
        assertEquals(Tab.Exercises, navigator.tab)

        back()
        assertEquals(Tab.Today, navigator.tab)
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun tappingTheOpenTabGoesBackToItsFirstPage() {
        navigator.stack(Tab.Exercises).add(Route.ExercisePage(101))
        compose.setContent {
            KamaeTheme { KamaeRoot(Samples.today, AppSettings(), RecordedActions(), SamplePages, frozenNow = Samples.NOW, navigator = navigator) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Exercises").performClick()
        compose.waitForIdle()
        assertEquals(listOf(Route.Home(Tab.Exercises)), navigator.stack(Tab.Exercises).toList())
    }
}
