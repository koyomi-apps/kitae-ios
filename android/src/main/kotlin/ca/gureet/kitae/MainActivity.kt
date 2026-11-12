// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gureet.kitae.app.AppModel
import ca.gureet.kitae.app.KamaeApplication
import ca.gureet.kitae.app.KamaeRoot
import ca.gureet.kitae.model.AppTheme
import ca.gureet.kitae.ui.theme.KamaeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as KamaeApplication).container
        setContent {
            val model: AppModel = viewModel { AppModel(container) }
            val settings by model.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                AppTheme.System -> isSystemInDarkTheme()
                AppTheme.Light -> false
                AppTheme.Dark -> true
            }
            // The status and navigation bars follow Settings › Theme, not only the phone's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(style, style)
                onDispose {}
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
            KamaeTheme(settings.theme) {
                KamaeRoot(model.state, settings, model)
            }
        }
    }
}
