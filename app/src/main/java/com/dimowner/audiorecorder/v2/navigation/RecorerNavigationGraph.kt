package com.dimowner.audiorecorder.v2.navigation

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dimowner.audiorecorder.v2.app.deleted.DeletedRecordsScreen
import com.dimowner.audiorecorder.v2.app.deleted.DeletedRecordsViewModel
import com.dimowner.audiorecorder.v2.app.home.HomeScreen
import com.dimowner.audiorecorder.v2.app.home.HomeViewModel
import com.dimowner.audiorecorder.v2.app.isDescriptionFileWriteSupported
import com.dimowner.audiorecorder.v2.app.info.AssetParamType
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.info.RecordInfoScreen
import com.dimowner.audiorecorder.v2.app.info.RecordInfoViewModel
import com.dimowner.audiorecorder.v2.app.lostrecords.LostRecordsScreen
import com.dimowner.audiorecorder.v2.app.lostrecords.LostRecordsViewModel
import com.dimowner.audiorecorder.v2.app.records.RecordsScreen
import com.dimowner.audiorecorder.v2.app.records.RecordsViewModel
import com.dimowner.audiorecorder.v2.app.settings.SettingsScreen
import com.dimowner.audiorecorder.v2.app.settings.SettingsScreenAction
import com.dimowner.audiorecorder.v2.app.settings.SettingsViewModel
import com.dimowner.audiorecorder.v2.app.settings.WelcomeSetupSettingsScreen
import com.dimowner.audiorecorder.v2.app.welcome.WelcomeScreen
import com.dimowner.audiorecorder.R
import kotlinx.coroutines.CoroutineScope

private const val ANIMATION_DURATION = 120

@Composable
fun RecorderNavigationGraph(
    coroutineScope: CoroutineScope,
    homeViewModel: HomeViewModel,
    isFirstRun: Boolean,
    onSwitchToLegacyApp: () -> Unit,
    onCheckNotificationPermission: () -> Unit = {},
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (isFirstRun) Routes.WELCOME_SCREEN else Routes.HOME_SCREEN,
        enterTransition = { enterTransition(this) },
        exitTransition = { exitTransition(this) },
        popEnterTransition = { popEnterTransition(this) },
        popExitTransition = { popExitTransition(this) }
    ) {
        composable(Routes.HOME_SCREEN) {
            LaunchedEffect(Unit) {
                onCheckNotificationPermission()
            }
            HomeScreen(
                showRecordsScreen = { navController.navigate(Routes.RECORDS_SCREEN) },
                showSettingsScreen = { navController.navigate(Routes.SETTINGS_SCREEN) },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
                showLostRecordsScreen = { lostRecord ->
                    val idsString = lostRecord.id.toString()
                    navController.navigate("${Routes.LOST_RECORDS_SCREEN}/$idsString")
                },
                uiState = homeViewModel.state.value,
                event = homeViewModel.event,
                onAction = { homeViewModel.onAction(it) }
            )
        }
        composable(Routes.RECORDS_SCREEN) {
            val recordsViewModel: RecordsViewModel = hiltViewModel()
            RecordsScreen(
                onPopBackStack = {
                    navController.popBackStack()
                },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                }, showDeletedRecordsScreen = {
                    navController.navigate(Routes.DELETED_RECORDS_SCREEN)
                }, showLostRecordsScreen = { lostRecords ->
                    val idsString = lostRecords.joinToString(",") { it.id.toString() }
                    navController.navigate("${Routes.LOST_RECORDS_SCREEN}/$idsString")
                }, uiState = recordsViewModel.state.value,
                event = recordsViewModel.event.collectAsState(null).value,
                onAction = {
                    recordsViewModel.onAction(it)
                },
                uiHomeState = homeViewModel.state.value,
                onHomeAction = { homeViewModel.onAction(it) }
            )
        }
        composable(Routes.DELETED_RECORDS_SCREEN) {
            val deletedViewModel: DeletedRecordsViewModel = hiltViewModel()
            DeletedRecordsScreen(onPopBackStack = {
                    navController.popBackStack()
                },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                }, uiState = deletedViewModel.state.value,
                event = deletedViewModel.event,
                onAction = { deletedViewModel.onAction(it) }
            )
        }
        composable(
            "${Routes.LOST_RECORDS_SCREEN}/{${Routes.LOST_RECORD_IDS}}",
            arguments = listOf(
                navArgument(Routes.LOST_RECORD_IDS) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val lostRecordsViewModel: LostRecordsViewModel = hiltViewModel()
            val idsString = backStackEntry.arguments?.getString(Routes.LOST_RECORD_IDS) ?: ""
            lostRecordsViewModel.loadRecordsByIds(idsString)
            LostRecordsScreen(
                onPopBackStack = {
                    navController.popBackStack()
                },
                showRecordInfoScreen = { json ->
                    navController.navigate(Routes.RECORD_INFO_SCREEN +"/${json}")
                },
                uiState = lostRecordsViewModel.state.value,
                event = lostRecordsViewModel.event.collectAsState(null).value,
                onAction = { lostRecordsViewModel.onAction(it) }
            )
        }
        composable(Routes.SETTINGS_SCREEN) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(onPopBackStack = {
                    navController.popBackStack()
                }, showDeletedRecordsScreen = {
                    navController.navigate(Routes.DELETED_RECORDS_SCREEN)
                }, uiState = settingsViewModel.state.value,
                onAction = {
                    settingsViewModel.onAction(it)
                    if (it is SettingsScreenAction.SetAppV2) {
                        onSwitchToLegacyApp()
                    }
                }
            )
        }
        composable(Routes.WELCOME_SCREEN) {
            WelcomeScreen(onGetStarted = {
                navController.navigate(Routes.WELCOME_SETUP_SETTINGS_SCREEN)
            })
        }
        composable(Routes.WELCOME_SETUP_SETTINGS_SCREEN) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            WelcomeSetupSettingsScreen(onPopBackStack = {
                    navController.popBackStack()
                }, onApplySettings = {
                    navController.navigate(Routes.HOME_SCREEN) {
                        popUpTo(0)
                    }
                }, uiState = settingsViewModel.state.value,
                    onAction = { settingsViewModel.onAction(it) }
            )
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
            val recordInfoViewModel: RecordInfoViewModel = hiltViewModel()
            LaunchedEffect(recordInfo?.location) {
                recordInfo?.location?.let { path -> recordInfoViewModel.loadAuthorName(path) }
            }
//            RecordInfoScreen(onPopBackStack = {
//                navController.popBackStack()
//            }, recordInfo?.copy(
//                authorName = recordInfoViewModel.authorName.value ?: ""
//            ))
            LaunchedEffect(recordInfo?.id) {
                if (recordInfo != null) {
                    recordInfoViewModel.loadDescription(
                        recordId = recordInfo.id,
                        filePath = recordInfo.location,
                        fallback = recordInfo.description
                    )
                }
            }
            val resolvedAuthorName = recordInfoViewModel.authorName.value ?: ""
            val resolvedDescription = recordInfoViewModel.description.value ?: recordInfo?.description ?: ""
            val context = LocalContext.current

            RecordInfoScreen(
                onPopBackStack = { navController.popBackStack() },
                recordInfo = recordInfo?.copy(
                    authorName = resolvedAuthorName,
                    description = resolvedDescription
                ),
                saveDescriptionToFile = recordInfoViewModel.saveDescriptionToFile,
                onSaveDescription = { description, writeToFile ->
                    if (recordInfo != null) {
                        recordInfoViewModel.saveDescription(
                            recordId = recordInfo.id,
                            description = description,
                            writeToFile = writeToFile,
                            writeToFileSupported = isDescriptionFileWriteSupported(recordInfo.format),
                            onDone = { success ->
                                if (success) {
                                    Toast.makeText(context, R.string.msg_saved_successfully, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
            )
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
