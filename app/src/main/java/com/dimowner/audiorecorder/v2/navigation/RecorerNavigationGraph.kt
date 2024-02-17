package com.dimowner.audiorecorder.v2.navigation

import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dimowner.audiorecorder.v2.ComposePlaygroundScreen
import com.dimowner.audiorecorder.v2.UserInputViewModel
import com.dimowner.audiorecorder.v2.WelcomeScreen
import com.dimowner.audiorecorder.v2.info.AssetParamType
import com.dimowner.audiorecorder.v2.info.RecordInfoState
import com.dimowner.audiorecorder.v2.info.RecordInfoScreen
import com.dimowner.audiorecorder.v2.settings.SettingsScreen

private const val ANIMATION_DURATION = 120

@Composable
fun RecorderNavigationGraph(userInputViewModel: UserInputViewModel = viewModel()) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.COMPOSE_PLAYGROUND_SCREEN,
        enterTransition = { enterTransition(this) },
        exitTransition = { exitTransition(this) },
        popEnterTransition = { popEnterTransition(this) },
        popExitTransition = { popExitTransition(this) }
    ) {
        composable(Routes.COMPOSE_PLAYGROUND_SCREEN) {
            ComposePlaygroundScreen(navController, userInputViewModel,
                showWelcomeScreen = {
                    navController.navigate(Routes.WELCOME_SCREEN +"/${it.first}/${it.second}")
                },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
                showSettingsScreen = {
                    navController.navigate(Routes.SETTINGS_SCREEN)
                }
            )
        }
        composable(Routes.SETTINGS_SCREEN) {
            SettingsScreen(navController)
        }
        composable("${Routes.WELCOME_SCREEN}/{${Routes.USER_NAME}}/{${Routes.ANIMAL_SELECTED}}",
                arguments = listOf(
                    navArgument(name = Routes.USER_NAME) { type = NavType.StringType },
                    navArgument(name = Routes.ANIMAL_SELECTED) { type = NavType.StringType }
                ),
            ) {
            val userName = it.arguments?.getString(Routes.USER_NAME)
            val animalSelected = it.arguments?.getString(Routes.ANIMAL_SELECTED)
            WelcomeScreen(userName = userName, animalSelected = animalSelected)
        }

        composable(
            "${Routes.RECORD_INFO_SCREEN}/{${Routes.RECORD_INFO}}",
            arguments = listOf(
                navArgument(Routes.RECORD_INFO) {
                    type = AssetParamType()
                }
            ),
        ) {
            val recordInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.arguments?.getParcelable(Routes.RECORD_INFO,  RecordInfoState::class.java)
            } else {
                it.arguments?.getParcelable(Routes.RECORD_INFO)
            }
            RecordInfoScreen(navController, recordInfo)
        }
    }
}

private fun enterTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
    return scope.slideIntoContainer(
        animationSpec = tween(ANIMATION_DURATION, easing = EaseIn),
        towards = AnimatedContentTransitionScope.SlideDirection.Start
    )
}

private fun exitTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
    return scope.slideOutOfContainer(
        animationSpec = tween(ANIMATION_DURATION, easing = EaseIn),
        towards = AnimatedContentTransitionScope.SlideDirection.Start
    )
}

private fun popEnterTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
    return scope.slideIntoContainer(
        animationSpec = tween(ANIMATION_DURATION, easing = EaseOut),
        towards = AnimatedContentTransitionScope.SlideDirection.End
    )
}

private fun popExitTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
    return scope.slideOutOfContainer(
        animationSpec = tween(ANIMATION_DURATION, easing = EaseOut),
        towards = AnimatedContentTransitionScope.SlideDirection.End
    )
}
