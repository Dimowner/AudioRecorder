package com.dimowner.audiorecorder.app.v2.ui

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dimowner.audiorecorder.app.v2.ui.theme.Compose1Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            Compose1Theme {
                // A surface container using the 'background' color from the theme
                FunFactsApp()
            }
        }
    }

    @Composable
    fun FunFactsApp() {
        FunFactsNavigationGraph()
    }
}
