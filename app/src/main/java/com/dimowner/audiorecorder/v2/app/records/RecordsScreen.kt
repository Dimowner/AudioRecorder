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

package com.dimowner.audiorecorder.v2.app.records

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.ComposableLifecycle
import com.dimowner.audiorecorder.v2.app.DeleteDialog
import com.dimowner.audiorecorder.v2.app.RenameAlertDialog
import com.dimowner.audiorecorder.v2.app.SaveAsDialog
import com.dimowner.audiorecorder.v2.app.calculateGridStep
import com.dimowner.audiorecorder.v2.app.calculateScale
import com.dimowner.audiorecorder.v2.app.components.TouchPanel
import com.dimowner.audiorecorder.v2.app.components.WaveformState
import com.dimowner.audiorecorder.v2.app.home.HomeScreenAction
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.settings.SettingsItem
import com.google.gson.Gson
import kotlinx.coroutines.launch
import timber.log.Timber

private const val ANIMATION_DURATION = 500
private const val MAX_MOVE = 250

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showDeletedRecordsScreen: () -> Unit,
    uiState: RecordsScreenState,
    recordsEvent: RecordsScreenEvent?,
    onAction: (RecordsScreenAction) -> Unit,
    uiHomeState: HomeScreenState,
    onHomeAction: (HomeScreenAction) -> Unit,
) {
//    val density = LocalDensity.current
//    // State to keep track of the Card position
//    val offsetY = remember { mutableFloatStateOf(0f) }
//    val maxMove = with(density) { MAX_MOVE.dp.toPx() }
//    val k = (maxMove / (Math.PI / 2f)).toFloat()
//    val startY = with(density) { 12.dp.toPx() }
//
//    val animatableY = remember { Animatable(startY) }
//
    // Get a CoroutineScope tied to the Composable
    val coroutineScope = rememberCoroutineScope()
//
//    // Define a threshold for Y coordinate movement
//    val playPanelHeight = remember { mutableFloatStateOf(with(density) { 300.dp.toPx() }) }
//
//    // Modifier to make the text draggable
//    val modifier = Modifier
//        .offset { IntOffset(0, animatableY.value.roundToInt()) }
//        .pointerInput(Unit) {
//            detectDragGestures(
//                onDragStart = {
//                    offsetY.floatValue = startY
//                },
//                onDragEnd = {
//                    // Animate back to start position
//                    if (offsetY.floatValue.absoluteValue > playPanelHeight.floatValue * 0.5) {
//                        coroutineScope.launch {
//                            animatableY.animateTo(
////                                TODO:Fix constants!!
//                                playPanelHeight.floatValue * 1.5f,
//                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
//                            )
//                            offsetY.floatValue = startY
//                            onHomeAction(HomeScreenAction.OnStopClick)
//                        }
//                    } else {
//                        coroutineScope.launch {
//                            animatableY.animateTo(
//                                startY,
//                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
//                            )
//                        }
//                    }
//                },
//                onDragCancel = {
//                    if (offsetY.floatValue.absoluteValue > playPanelHeight.floatValue * 0.5) {
//                        coroutineScope.launch {
//                            animatableY.animateTo(
//                                playPanelHeight.floatValue * 1.5f,
//                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
//                            )
//                            offsetY.floatValue = startY
//                            onHomeAction(HomeScreenAction.OnStopClick)
//                        }
//                    } else {
//                        // Animate back to start position
//                        coroutineScope.launch {
//                            animatableY.animateTo(
//                                startY,
//                                animationSpec = tween(durationMillis = ANIMATION_DURATION)
//                            )
//                        }
//                    }
//                },
//                onDrag = { change, dragAmount ->
//                    change.consume()
//                    offsetY.floatValue += change.position.y
//                    offsetY.floatValue = k * atan(offsetY.floatValue / k)
//                    coroutineScope.launch {
//                        animatableY.snapTo(offsetY.floatValue)
//                    }
//                }
//            )
//        }

    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("RecordsScreen: On Start")
                onAction(RecordsScreenAction.InitRecordsScreen(
                    showPlayPanel = uiHomeState.waveformState.progressMills > 0)
                )
            }
            Lifecycle.Event.ON_STOP -> {
                Timber.d("RecordsScreen: On Stop")
                onAction(RecordsScreenAction.OnStopRecordsScreen)
            }
            else -> {}
        }
    }
    LaunchedEffect(key1 = recordsEvent) {
        when (recordsEvent) {
            is RecordsScreenEvent.RecordInformationEvent -> {
                val json = Uri.encode(Gson().toJson(recordsEvent.recordInfo))
                Timber.v("ON EVENT: ShareRecord json = $json")
                showRecordInfoScreen(json)
            }

            else -> {
                Timber.v("ON EVENT: Unknown")
                //Do nothing
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart,
            ) {
                if (uiState.isShowProgress) {
                    //Show nothing because of progress takes very short period of time
                } else if (uiState.recordsMap.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            modifier = Modifier.wrapContentSize(),
                            painter = painterResource(id = R.drawable.ic_audiotrack_64),
                            contentDescription = "Image Description",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            modifier = Modifier.wrapContentSize(),
                            text = if (uiState.bookmarksSelected) {
                                stringResource(R.string.no_bookmarks)
                            } else {
                                stringResource(R.string.no_records)
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.selectedRecords.isEmpty()) {
                        RecordsTopBar(
                            stringResource(id = R.string.records),
                            uiState.sortOrder.toText(context),
                            bookmarksSelected = uiState.bookmarksSelected,
                            onBackPressed = { onPopBackStack() },
                            onSortItemClick = { order ->
                                onAction(RecordsScreenAction.UpdateListWithSortOrder(order))
                            },
                            onBookmarksClick = { bookmarksSelected ->
                                onAction(
                                    RecordsScreenAction.UpdateListWithBookmarks(
                                        bookmarksSelected
                                    )
                                )
                            }
                        )
                    } else {
                        MultiSelectMenu(
                            selectedItemsCount = uiState.selectedRecords.size,
                            onCancelClick = { onAction(RecordsScreenAction.MultiSelectCancel)},
                            onShareClick = {
                                onAction(RecordsScreenAction.MultiSelectShare(uiState.selectedRecords))
                            },
                            onDownloadClick = {
                                onAction(RecordsScreenAction.MultiSelectSaveAsRequest)
                            },
                            onDeleteClick = {
                                onAction(RecordsScreenAction.MultiSelectMoveToRecycleRequest)
                            },
                        )
                    }
                    if (uiState.showDeletedRecordsButton) {
                        SettingsItem(stringResource(R.string.trash), R.drawable.ic_delete) {
                            showDeletedRecordsScreen()
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        uiState.recordsMap.forEach { (date, recordsOnDate) ->
                            //Sticky date header
                            stickyHeader {
                                if (date.isEmpty()) {
                                    Box(modifier = Modifier) {}
                                } else {
                                    Surface(
                                        modifier = Modifier.fillParentMaxWidth(),
                                    ) {
                                        Text(
                                            text = date,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                16.dp, 10.dp, 16.dp, 2.dp
                                            ),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            //The list of items for that specific date
                            items(recordsOnDate) { record ->
                                RecordListItemView(
                                    name = record.name,
                                    details = record.details,
                                    duration = record.duration,
                                    isBookmarked = record.isBookmarked,
                                    isSelected = record.recordId == uiState.activeRecord?.recordId
                                            || uiState.selectedRecords.contains(record),
                                    isShowMenuButton = uiState.selectedRecords.isEmpty()
                                            && record.recordId != uiState.recordedRecordId,
                                    onClickItem = {
                                        if (!uiState.isRecording) {
                                            if (uiState.selectedRecords.isEmpty()) {
                                                onAction(RecordsScreenAction.OnItemSelect(record))
                                                onHomeAction(HomeScreenAction.InitHomeScreen)
                                                onHomeAction(HomeScreenAction.OnPlayClick)
                                            } else {
                                                onAction(RecordsScreenAction.MultiSelectAddItem(record))
                                            }
                                        }
                                    },
                                    onLongClickItem = {
                                        if (!uiState.isRecording) {
                                            onAction(RecordsScreenAction.MultiSelectAddItem(record))
                                        }
                                    },
                                    onClickBookmark = { isBookmarked ->
                                        onAction(
                                            RecordsScreenAction.BookmarkRecord(
                                                record.recordId,
                                                isBookmarked
                                            )
                                        )
                                    },
                                    onClickMenu = {
                                        when (it) {
                                            RecordDropDownMenuItemId.SHARE -> {
                                                onAction(RecordsScreenAction.ShareRecord(record.recordId))
                                            }

                                            RecordDropDownMenuItemId.INFORMATION -> {
                                                onAction(RecordsScreenAction.ShowRecordInfo(record.recordId))
                                            }

                                            RecordDropDownMenuItemId.RENAME -> {
                                                onAction(
                                                    RecordsScreenAction.OnRenameRecordRequest(
                                                        record
                                                    )
                                                )
                                            }

                                            RecordDropDownMenuItemId.OPEN_WITH -> {
                                                onAction(
                                                    RecordsScreenAction.OpenRecordWithAnotherApp(
                                                        record.recordId
                                                    )
                                                )
                                            }

                                            RecordDropDownMenuItemId.SAVE_AS -> {
                                                onAction(RecordsScreenAction.OnSaveAsRequest(record))
                                            }

                                            RecordDropDownMenuItemId.DELETE -> {
                                                onAction(
                                                    RecordsScreenAction.OnMoveToRecycleRecordRequest(
                                                        record
                                                    )
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (uiState.showMoveToRecycleDialog) {
                        uiState.operationSelectedRecord?.let { record ->
                            DeleteDialog(
                                dialogText = stringResource(id = R.string.delete_record, record.name),
                                onAcceptClick = {
                                    onAction(RecordsScreenAction.MoveRecordToRecycle(record.recordId))
                                }, onDismissClick = {
                                    onAction(RecordsScreenAction.OnMoveToRecycleRecordDismiss)
                                }
                            )
                        }
                    } else if (uiState.showSaveAsDialog) {
                        uiState.operationSelectedRecord?.let { record ->
                            SaveAsDialog(
                                dialogText = stringResource(
                                    id = R.string.record_name_will_be_copied_into_downloads,
                                    record.name),
                                onAcceptClick = {
                                    onAction(RecordsScreenAction.SaveRecordAs(record.recordId))
                                }, onDismissClick = {
                                    onAction(RecordsScreenAction.OnSaveAsDismiss)
                                }
                            )
                        }
                    } else if (uiState.showRenameDialog) {
                        uiState.operationSelectedRecord?.let { record ->
                            RenameAlertDialog(record.name, onAcceptClick = {
                                onAction(RecordsScreenAction.RenameRecord(record.recordId, it))
                            }, onDismissClick = {
                                onAction(RecordsScreenAction.OnRenameRecordDismiss)
                            })
                        }
                    } else if (uiState.showMoveToRecycleMultipleDialog) {
                        val count = uiState.selectedRecords.size
                        val titleText = pluralStringResource(
                            id = R.plurals.delete_selected_records,
                            count = count, count)
                        DeleteDialog(titleText, onAcceptClick = {
                            onAction(RecordsScreenAction.MultiSelectMoveToRecycle)
                        }, onDismissClick = {
                            onAction(RecordsScreenAction.MultiSelectMoveToRecycleDismiss)
                        })
                    } else if (uiState.showSaveAsMultipleDialog) {
                        val count = uiState.selectedRecords.size
                        val titleText = pluralStringResource(
                            id = R.plurals.download_selected_records,
                            count = count, count)

                        SaveAsDialog(titleText,
                            onAcceptClick = {
                                onAction(RecordsScreenAction.MultiSelectSaveAs)
                            }, onDismissClick = {
                                onAction(RecordsScreenAction.MultiSelectSaveAsDismiss)
                            }
                        )
                    }
                }
                TouchPanel(
                    showRecordPlaybackPanel = uiState.showRecordPlaybackPanel,
                    uiHomeState = uiHomeState,
                    onProgressChange = {
                        onHomeAction(
                            HomeScreenAction.OnProgressBarStateChange(
                                it
                            )
                        )
                    },
                    onSeekStart = { onHomeAction(HomeScreenAction.OnSeekStart) },
                    onSeekProgress = { onHomeAction(HomeScreenAction.OnSeekProgress(it)) },
                    onSeekEnd = { onHomeAction(HomeScreenAction.OnSeekEnd(it)) },
                    onPlayClick = { onHomeAction(HomeScreenAction.OnPlayClick) },
                    onStopClick = {
                        coroutineScope.launch {
                            onHomeAction(HomeScreenAction.OnStopClick)
                        }
                    },
                    onPauseClick = { onHomeAction(HomeScreenAction.OnPauseClick) },
                )
//                AnimatedVisibility(
//                    visible = uiState.showRecordPlaybackPanel,
//                    enter = slideInVertically(initialOffsetY = { it }),
//                    exit = slideOutVertically(targetOffsetY = { it })
//                ) {
//                    Card(
//                        modifier = modifier
//                            .wrapContentSize()
//                            .onSizeChanged {
//                                playPanelHeight.floatValue = it.height.toFloat()
//                            },
//                    ) {
//                        RecordPlaybackPanel(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .wrapContentHeight(),
//                            uiState = uiHomeState,
//                            onProgressChange = {
//                                onHomeAction(
//                                    HomeScreenAction.OnProgressBarStateChange(
//                                        it
//                                    )
//                                )
//                            },
//                            onSeekStart = { onHomeAction(HomeScreenAction.OnSeekStart) },
//                            onSeekProgress = { onHomeAction(HomeScreenAction.OnSeekProgress(it)) },
//                            onSeekEnd = { onHomeAction(HomeScreenAction.OnSeekEnd(it)) },
//                            onPlayClick = { onHomeAction(HomeScreenAction.OnPlayClick) },
//                            onStopClick = {
//                                coroutineScope.launch {
//                                    onHomeAction(HomeScreenAction.OnStopClick)
//                                }
//                            },
//                            onPauseClick = { onHomeAction(HomeScreenAction.OnPauseClick) },
//                        )
//                    }
//                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenPreview() {
    val waveformData = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 234, 526, 0, 0, 0, 0, 8424, 4394, 7514, 23400, 13754, 10400, 21118, 12018, 24986, 6656, 7514, 10926, 32767, 24186, 21118, 16250, 10400, 7962, 5850, 4738, 4394, 26624, 10926, 5850, 4738, 5096, 4062, 11466, 10926, 14976, 22626, 21118, 30056, 21118, 32767, 23400, 21866, 21866, 23400, 27462, 25798, 24186, 14976, 18258, 11466, 15606, 23400, 29178, 29178, 26624, 16906, 12018, 16906, 12018, 14358, 7962, 22626, 22626, 14358, 6656, 4738, 3744, 2866, 4394, 3744, 4394, 3146, 2600, 416, 27462, 28314, 14976, 14976, 14976, 29178, 23400, 24186, 19662, 28314, 26624, 13162, 18258, 18258, 22626, 30946, 19662, 9886, 6246, 5096, 3744, 3744, 4394, 24186, 21118, 7078, 3146, 1878, 3438, 1878, 9386, 21118, 12584, 14976, 12584, 17576, 5850, 29178, 23400, 4738, 4062, 26624, 26624, 18954, 13754, 14358, 13162, 13754, 7962, 17576, 31850, 13754, 13162, 13754, 10400, 8898, 5850, 5466, 2866, 23400, 9386, 7514, 5850, 7962, 7514, 5466, 8424, 6656, 5850, 4394, 19662, 3438, 26624, 22626, 12018, 7514, 24986, 24186, 16250, 30946, 21118, 11466, 9886, 12584, 25798, 30056, 27462, 17576, 16906, 20384, 19662, 18258, 19662, 9386, 19662, 10400, 3744, 3146, 3744, 8424, 4062, 7078, 15606, 22626, 20384, 24186, 18258, 27462, 25798, 12584, 14358, 18954, 24986, 24986, 19662, 20384, 23400, 15606, 16906, 17576, 16906, 13162, 29178, 8898, 7514, 4738, 2600, 2106, 2866, 3438, 28314, 22626, 4394, 1462, 1098, 2866, 1878, 4394, 2600, 2346, 2346, 5466, 4738, 526, 29178, 20384, 19662, 6656, 28314, 21866, 20384, 11466, 21866, 15606, 18954, 18954, 16250, 32767, 22626, 9886, 18954, 16906, 13162, 8898, 6246, 5466, 30056, 7962, 5466, 5850, 23400, 17576, 29178, 10400, 14976, 22626, 19662, 20384, 14976, 32767, 13754, 13162, 13162, 32767, 20384, 8898, 7078, 16250, 18258, 15606, 13162, 14358, 29178, 15606, 5466, 4394, 2106, 162, 0, 0, 0, 786, 58, 58, 526, 104, 1462, 526, 786, 26, 0, 0, 0, 0, 29178, 17576, 24986, 29178, 28314, 23400, 20384, 30056, 27462, 31850, 32767, 27462, 32767, 25798, 32767, 30056, 29178, 23400, 22626, 31850, 14976, 16906, 30946, 25798, 27462, 22626, 15606, 29178, 13162, 23400, 21866, 24186, 21118, 24186, 28314, 29178, 30056, 16250, 18954, 16906, 29178, 30056, 27462, 20384, 29178, 25798, 12584, 21118, 20384, 31850, 21866, 21866, 26624, 18954, 14358, 21866, 24186, 25798, 27462, 21118, 22626, 21118, 24986, 13754, 13754, 30056, 22626, 10400, 27462, 30056, 24986, 29178, 20384, 23400, 28314, 29178, 29178, 17576, 20384, 23400, 27462, 13162, 24186, 20384, 31850, 25798, 25798, 14976, 24986, 22626, 24186, 23400, 30056, 30946, 17576, 16250, 13754, 16250, 24986, 24986, 17576, 29178, 20384, 30056, 19662, 18258, 24986, 30056, 18954, 24186, 30946, 32767, 32767, 27462, 30946, 13162, 24186, 21866, 31850, 30056, 24986, 30946, 26624, 22626, 21866, 25798, 28314, 30946, 32767, 30056, 30946, 29178, 23400, 28314, 30056, 30946, 26624, 30946, 29178, 21118, 29178, 21866, 29178, 28314, 21118, 31850, 32767, 29178, 31850, 30946, 31850, 25798, 26624, 30946, 32767, 32767, 30946, 28314, 28314, 32767, 30946, 28314, 28314, 31850, 30946, 30946, 30056, 21866, 18954, 30056, 19662, 31850, 29178, 31850, 22626, 25798, 28314, 26624, 29178, 25798, 28314, 28314, 29178, 28314, 27462, 27462, 25798, 22626, 18258, 29178, 23400, 32767, 21866, 18954, 24186, 19662, 14976, 17576, 23400, 23400, 24986, 27462, 26624, 19662, 25798, 21866, 30056, 30056, 30946, 32767, 18954, 28314, 10926, 30056, 24986, 31850, 25798, 29178, 27462, 22626, 24186, 24986, 25798, 17576, 28314, 27462, 29178, 30056, 20384, 18258, 21118, 24986, 24186, 25798, 29178, 29178, 27462, 22626, 16906, 23400, 21118, 27462, 26624, 26624, 31850, 27462, 23400, 26624, 21866, 20384, 25798, 29178, 20384, 13754, 9886, 9886, 10926, 29178, 25798, 24986, 22626, 14358, 1878, 0, 0, 0, 0, 0, 8898, 2600, 25798, 9886, 6656, 7962, 6246, 6246, 5096, 5850, 6656, 5096, 8424, 8424, 4062, 4394, 4394, 5850, 4394, 9886, 28314, 8424, 6246, 58, 6656, 3438, 0, 12018, 8898, 7514, 1878, 1098, 58, 0, 318, 318, 8424, 12018, 13162, 7962, 7514, 7514, 7514, 6246, 3146, 18954, 84)
    val durationMills = 58728L
    RecordsScreen({}, {}, {},
        RecordsScreenState(
            recordsMap = mapOf(
                Pair("Today", listOf(
                    RecordListItem(
                        recordId = 1,
                        name = "Test record 1",
                        details = "1.5 MB, mp4, 192 kbps, 48 kHz",
                        duration = "3:15",
                        added = 100000000,
                        isBookmarked = true
                    ),
                    RecordListItem(
                        recordId = 2,
                        name = "Test record 2",
                        details = "4.5 MB, mp3, 128 kbps, 32 kHz",
                        duration = "8:15",
                        added = 0,
                        isBookmarked = false
                    )
                ))
            ),
            showDeletedRecordsButton = true,
            showRenameDialog = false,
            showMoveToRecycleDialog = false,
            showSaveAsDialog = false,
            operationSelectedRecord = RecordListItem(
                recordId = 2,
                name = "Test record 2",
                details = "4.5 MB, mp3, 128 kbps, 32 kHz",
                duration = "8:15",
                added = 0,
                isBookmarked = false
            )
        ),
        null, {},
        uiHomeState = HomeScreenState(
            waveformState = WaveformState(
                widthScale = calculateScale(durationMills, defaultWidthScale = 1.5f),
                durationMills = durationMills,
                progressMills = 60000L,
                waveformData = waveformData,
                durationSample = waveformData.size,
                gridStepMills = calculateGridStep(durationMills)
            ),
            startTime = "00:00",
            endTime = "3:42",
            time = "1:51",
            recordName = "Test Record Name",
            recordInfo = "1.5 MB, mp4, 192 kbps, 48 kHz",
            isContextMenuAvailable = true,
            isStopRecordingButtonAvailable = true,
        ),
        onHomeAction = {}
    )
}

@Preview(showBackground = true)
@Composable
fun RecordsScreenEmptyPreview() {
    RecordsScreen({}, {}, {},
        RecordsScreenState(),
        null, {},
        uiHomeState = HomeScreenState(),
        onHomeAction = {}
    )
}


@Preview(showBackground = true)
@Composable
fun RecordsScreenLoadingPreview() {
    RecordsScreen({}, {}, {},
        RecordsScreenState(isShowProgress = true),
        null, {},
        uiHomeState = HomeScreenState(),
        onHomeAction = {}
    )
}