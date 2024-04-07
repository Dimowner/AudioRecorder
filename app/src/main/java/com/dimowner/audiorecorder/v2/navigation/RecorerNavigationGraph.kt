package com.dimowner.audiorecorder.v2.navigation

import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dimowner.audiorecorder.v2.app.ComposePlaygroundScreen
import com.dimowner.audiorecorder.v2.app.UserInputViewModel
import com.dimowner.audiorecorder.v2.app.DetailsWelcomeScreen
import com.dimowner.audiorecorder.v2.app.deleted.DeletedRecordsScreen
import com.dimowner.audiorecorder.v2.app.home.HomeScreen
import com.dimowner.audiorecorder.v2.app.info.AssetParamType
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.RecordInfoScreen
import com.dimowner.audiorecorder.v2.app.records.RecordsScreen
import com.dimowner.audiorecorder.v2.app.settings.SettingsScreen
import com.dimowner.audiorecorder.v2.app.welcome.WelcomeScreen

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
            ComposePlaygroundScreen(userInputViewModel,
                showDetailsScreen = {
                    navController.navigate(Routes.DETAILS_SCREEN +"/${it.first}/${it.second}")
                },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
                showSettingsScreen = {
                    navController.navigate(Routes.SETTINGS_SCREEN)
                },
                showHomeScreen = {
                    navController.navigate(Routes.HOME_SCREEN)
                },
                showRecordsScreen = {
                    navController.navigate(Routes.RECORDS_SCREEN)
                },
                showWelcomeScreen = {
                    navController.navigate(Routes.WELCOME_SCREEN)
                },
                showDeletedRecordsScreen = {
                    navController.navigate(Routes.DELETED_RECORDS_SCREEN)
                }
            )
        }
        composable(Routes.HOME_SCREEN) {
            HomeScreen(
                showRecordsScreen = { navController.navigate(Routes.RECORDS_SCREEN) },
                showSettingsScreen = { navController.navigate(Routes.SETTINGS_SCREEN) },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
            )
        }
        composable(Routes.RECORDS_SCREEN) {
            RecordsScreen(navController,
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
            )
        }
        composable(Routes.DELETED_RECORDS_SCREEN) {
            DeletedRecordsScreen(navController,
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
            )
        }
        composable(Routes.SETTINGS_SCREEN) {
            SettingsScreen(navController, showDeletedRecordsScreen = {
                navController.navigate(Routes.DELETED_RECORDS_SCREEN)
            })
        }
        composable("${Routes.DETAILS_SCREEN}/{${Routes.USER_NAME}}/{${Routes.ANIMAL_SELECTED}}",
                arguments = listOf(
                    navArgument(name = Routes.USER_NAME) { type = NavType.StringType },
                    navArgument(name = Routes.ANIMAL_SELECTED) { type = NavType.StringType }
                ),
            ) {
            val userName = it.arguments?.getString(Routes.USER_NAME)
            val animalSelected = it.arguments?.getString(Routes.ANIMAL_SELECTED)
            DetailsWelcomeScreen(userName = userName, animalSelected = animalSelected)
        }
        composable(Routes.WELCOME_SCREEN) {
            WelcomeScreen(onGetStarted = {
                //TODO: navigate to app setup settings screen
                navController.navigate(Routes.HOME_SCREEN) {
                    popUpTo(0)
                }
            })
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
