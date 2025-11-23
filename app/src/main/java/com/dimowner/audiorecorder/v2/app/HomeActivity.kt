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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.v2.app.home.HomeViewModel
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.navigation.RecorderNavigationGraph
import com.dimowner.audiorecorder.v2.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity: ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var prefs: PrefsV2

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AppTheme(
                    dynamicColors = prefs.isDynamicTheme,
                    darkTheme = prefs.isDarkTheme
                ) { RecorderApp(lifecycleScope) }
            } else {
                AppTheme(darkTheme = prefs.isDarkTheme) { RecorderApp(lifecycleScope) }
            }
        }
    }

    @Composable
    fun RecorderApp(
        coroutineScope: CoroutineScope
    ) {
        RecorderNavigationGraph(coroutineScope, viewModel, onSwitchToLegacyApp = {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        })
    }
}
