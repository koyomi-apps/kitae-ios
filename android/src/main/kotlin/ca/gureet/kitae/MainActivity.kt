// SPDX-License-Identifier: GPL-3.0-or-later

package ca.gureet.kitae

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
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
            // While Kamae is on screen, the workout taps the phone when a rest ends, so the
            // notification doesn't say "Rest over" too.
            LifecycleEventEffect(Lifecycle.Event.ON_START) { container.restNotifications.isVisible = true }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { container.restNotifications.isVisible = false }
            // Asks to show notifications the first time a rest starts, for the rest timer.
            val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                model.refresh()
            }
            val resting = model.state.workout?.rest?.isActive == true
            LaunchedEffect(resting) {
                if (resting && Build.VERSION.SDK_INT >= 33 && !container.rest.askedForNotifications &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    container.rest.askedForNotifications = true
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // A restored backup or a reset starts Kamae again, so nothing keeps the old database.
            LaunchedEffect(model.state.restarting) {
                if (model.state.restarting) {
                    startActivity(Intent.makeRestartActivityTask(componentName))
                    Runtime.getRuntime().exit(0)
                }
            }
            KamaeTheme(settings.theme) {
                KamaeRoot(model.state, settings, model, pages = model)
            }
        }
    }
}
