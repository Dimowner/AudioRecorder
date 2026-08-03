/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.v2.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.v2.app.home.HomeViewModel
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.navigation.RecorderNavigationGraph
import com.dimowner.audiorecorder.v2.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

// Smallest screen width (in dp) that qualifies as a tablet/large screen per Material guidelines.
// Devices below this threshold are treated as phones and locked to portrait orientation.
private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

@AndroidEntryPoint
class HomeActivity: ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var prefs: PrefsV2

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled — no action needed */ }

    // The permission dialog is requested only once per activity instance. Re-entering the home
    // destination (back navigation) re-triggers the check, and launching a second request while
    // the first dialog is up makes the system fail to start the dialog activity.
    private var notificationPermissionRequested = false

    // Set when the check happens while the activity is not resumed, so it can be retried later.
    private var notificationPermissionCheckPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (resources.configuration.smallestScreenWidthDp < TABLET_MIN_SMALLEST_WIDTH_DP) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            val isDark by prefs.isDarkThemeFlow.collectAsState()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isDynamic by prefs.isDynamicThemeFlow.collectAsState()
                AppTheme(
                    dynamicColors = isDynamic,
                    darkTheme = isDark
                ) { RecorderApp(lifecycleScope) }
            } else {
                AppTheme(darkTheme = isDark) { RecorderApp(lifecycleScope) }
            }
        }
    }

    @Composable
    fun RecorderApp(
        coroutineScope: CoroutineScope
    ) {
        RecorderNavigationGraph(
            coroutineScope,
            viewModel,
            isFirstRun = prefs.isFirstRun,
            onSwitchToLegacyApp = {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            },
            onCheckNotificationPermission = { checkNotificationPermission() }
        )
    }

    override fun onResume() {
        super.onResume()
        if (notificationPermissionCheckPending) {
            notificationPermissionCheckPending = false
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionRequested) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (isFinishing || isDestroyed) return
        // The check is triggered from a composition, which may run before the activity is resumed
        // or after it went to the background. Starting the permission dialog from a non-resumed
        // activity is rejected by the system, so postpone it until the next onResume().
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            notificationPermissionCheckPending = true
            return
        }
        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

    }
}
