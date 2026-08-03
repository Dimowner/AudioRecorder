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
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
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
import com.dimowner.audiorecorder.v2.app.nameformat.NameFormatConstructorAction
import com.dimowner.audiorecorder.v2.app.nameformat.NameFormatConstructorScreen
import com.dimowner.audiorecorder.v2.app.nameformat.NameFormatConstructorViewModel
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

/**
 * A screen is only allowed to drive navigation while its own entry is RESUMED.
 *
 * An entry drops below RESUMED as soon as it starts animating away — including the moment a
 * predictive back gesture grabs it. Gating on that state stops a screen from popping or
 * navigating twice, and stops a tap on the toolbar back arrow from tearing down an entry that a
 * back gesture is already transitioning, which leaves NavHost holding an entry that is no longer
 * in the back stack ("Cannot transition entry that is not in the back stack").
 */
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED

private fun NavController.navigateFrom(
    from: NavBackStackEntry,
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (from.isResumed()) navigate(route, builder)
}

private fun NavController.popBackStackFrom(from: NavBackStackEntry) {
    if (from.isResumed()) popBackStack()
}

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
        composable(Routes.HOME_SCREEN) { entry ->
            LaunchedEffect(Unit) {
                onCheckNotificationPermission()
            }
            HomeScreen(
                showRecordsScreen = { navController.navigateFrom(entry, Routes.RECORDS_SCREEN) },
                showSettingsScreen = { navController.navigateFrom(entry, Routes.SETTINGS_SCREEN) },
                showRecordInfoScreen = { json ->
                    navController.navigateFrom(entry, Routes.RECORD_INFO_SCREEN + "/${json}")
                },
                showLostRecordsScreen = { lostRecord ->
                    val idsString = lostRecord.id.toString()
                    navController.navigateFrom(entry, "${Routes.LOST_RECORDS_SCREEN}/$idsString")
                },
                uiState = homeViewModel.state.value,
                event = homeViewModel.event,
                onAction = { homeViewModel.onAction(it) }
            )
        }
        composable(Routes.RECORDS_SCREEN) { entry ->
            val recordsViewModel: RecordsViewModel = hiltViewModel()
            RecordsScreen(
                onPopBackStack = {
                    navController.popBackStackFrom(entry)
                },
                showRecordInfoScreen = { json ->
                    navController.navigateFrom(entry, Routes.RECORD_INFO_SCREEN + "/${json}")
                }, showDeletedRecordsScreen = {
                    navController.navigateFrom(entry, Routes.DELETED_RECORDS_SCREEN)
                }, showLostRecordsScreen = { lostRecords ->
                    val idsString = lostRecords.joinToString(",") { it.id.toString() }
                    navController.navigateFrom(entry, "${Routes.LOST_RECORDS_SCREEN}/$idsString")
                }, uiState = recordsViewModel.state.value,
                event = recordsViewModel.event.collectAsState(null).value,
                onAction = {
                    recordsViewModel.onAction(it)
                },
                uiHomeState = homeViewModel.state.value,
                onHomeAction = { homeViewModel.onAction(it) }
            )
        }
        composable(Routes.DELETED_RECORDS_SCREEN) { entry ->
            val deletedViewModel: DeletedRecordsViewModel = hiltViewModel()
            DeletedRecordsScreen(onPopBackStack = {
                    navController.popBackStackFrom(entry)
                },
                showRecordInfoScreen = { json ->
                    navController.navigateFrom(entry, Routes.RECORD_INFO_SCREEN + "/${json}")
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
                    navController.popBackStackFrom(backStackEntry)
                },
                showRecordInfoScreen = { json ->
                    navController.navigateFrom(backStackEntry, Routes.RECORD_INFO_SCREEN + "/${json}")
                },
                uiState = lostRecordsViewModel.state.value,
                event = lostRecordsViewModel.event.collectAsState(null).value,
                onAction = { lostRecordsViewModel.onAction(it) }
            )
        }
        composable(Routes.SETTINGS_SCREEN) { entry ->
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(onPopBackStack = {
                    navController.popBackStackFrom(entry)
                }, showDeletedRecordsScreen = {
                    navController.navigateFrom(entry, Routes.DELETED_RECORDS_SCREEN)
                }, showNameFormatConstructorScreen = {
                    navController.navigateFrom(entry, Routes.NAME_FORMAT_CONSTRUCTOR_SCREEN)
                }, uiState = settingsViewModel.state.value,
                onAction = {
                    settingsViewModel.onAction(it)
                    if (it is SettingsScreenAction.SetAppV2) {
                        onSwitchToLegacyApp()
                    }
                }
            )
        }
        composable(Routes.NAME_FORMAT_CONSTRUCTOR_SCREEN) { entry ->
            val nameFormatViewModel: NameFormatConstructorViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                nameFormatViewModel.onAction(NameFormatConstructorAction.InitScreen)
            }
            NameFormatConstructorScreen(
                onPopBackStack = { navController.popBackStackFrom(entry) },
                uiState = nameFormatViewModel.state.value,
                onAction = { nameFormatViewModel.onAction(it) },
            )
        }
        composable(Routes.WELCOME_SCREEN) { entry ->
            WelcomeScreen(onGetStarted = {
                navController.navigateFrom(entry, Routes.WELCOME_SETUP_SETTINGS_SCREEN)
            })
        }
        composable(Routes.WELCOME_SETUP_SETTINGS_SCREEN) { entry ->
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            WelcomeSetupSettingsScreen(onPopBackStack = {
                    navController.popBackStackFrom(entry)
                }, onApplySettings = {
                    navController.navigateFrom(entry, Routes.HOME_SCREEN) {
                        popUpTo(0)
                    }
                }, showNameFormatConstructorScreen = {
                    navController.navigateFrom(entry, Routes.NAME_FORMAT_CONSTRUCTOR_SCREEN)
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
        ) { entry ->
            val recordInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                entry.arguments?.getParcelable(Routes.RECORD_INFO,  RecordInfoState::class.java)
            } else {
                entry.arguments?.getParcelable(Routes.RECORD_INFO)
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
                onPopBackStack = { navController.popBackStackFrom(entry) },
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
