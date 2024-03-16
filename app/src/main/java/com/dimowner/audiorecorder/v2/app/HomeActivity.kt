package com.dimowner.audiorecorder.v2.app

import android.os.Build
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dimowner.audiorecorder.v2.navigation.RecorderNavigationGraph
import com.dimowner.audiorecorder.v2.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AppTheme(dynamicColors = true, darkTheme = true) { RecorderApp() }
            } else {
                AppTheme(darkTheme = true) { RecorderApp() }
            }
        }
    }

    @Composable
    fun RecorderApp() {
        RecorderNavigationGraph()
    }
}
