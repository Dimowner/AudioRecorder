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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.dimowner.audiorecorder.v2.app.components.TouchPanel
import com.dimowner.audiorecorder.v2.app.getTestWaveformData
import com.dimowner.audiorecorder.v2.app.home.HomeScreenAction
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import com.dimowner.audiorecorder.v2.app.lostrecords.LostRecordsDialog
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.data.model.Record
import com.google.gson.Gson
import kotlinx.coroutines.launch
import timber.log.Timber

private const val ANIMATION_DURATION = 500
private const val MAX_MOVE = 250


//TODO: Add simple waveform to each record item

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordsScreen(
    onPopBackStack: () -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showDeletedRecordsScreen: () -> Unit,
    showLostRecordsScreen: (List<Record>) -> Unit,
    uiState: RecordsScreenState,
    event: RecordsScreenEvent?,
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val recordMovedToTrashMessage = if (event is RecordsScreenEvent.RecordMovedToRecycleSnack) {
        stringResource(R.string.msg_recording_moved_to_trash, event.recordName)
    } else ""
    val undoLabel = stringResource(R.string.action_undo)
    val fewRecordsMovedMessage = if (event is RecordsScreenEvent.FewRecordsMovedToRecycleSnack) {
        stringResource(R.string.msg_few_recordings_moved_to_trash, event.movedCount, event.expectedCount)
    } else ""

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("RecordsScreen: On Start")
                onAction(RecordsScreenAction.OnStartRecordsScreen(
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
    LaunchedEffect(key1 = event) {
        when (event) {
            is RecordsScreenEvent.RecordInformationEvent -> {
                val json = Uri.encode(Gson().toJson(event.recordInfo))
                Timber.v("ON EVENT: ShareRecord json = $json")
                showRecordInfoScreen(json)
            }
            is RecordsScreenEvent.RecordMovedToRecycleSnack -> {
                scope.launch {
                    val result = snackbarHostState
                        .showSnackbar(
                            message = recordMovedToTrashMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                    when (result) {
                        SnackbarResult.ActionPerformed -> {
                            onAction(RecordsScreenAction.RestoreRecordFromRecycle(event.recordId))
                        }
                        SnackbarResult.Dismissed -> {
                            /* Handle snackbar dismissed */
                        }
                    }
                }
            }
            is RecordsScreenEvent.FewRecordsMovedToRecycleSnack -> {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            message = fewRecordsMovedMessage,
                            duration = SnackbarDuration.Short
                        )
                }
            }
            is RecordsScreenEvent.ShowInfoSnack -> {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                }
            }
            is RecordsScreenEvent.ShowErrorSnack -> {
                scope.launch {
                    snackbarHostState
                        .showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                }
            }

            else -> {
                Timber.v("ON EVENT: Unknown")
                //Do nothing
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null &&
                    lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && uiState.hasMoreData) {
            onAction(RecordsScreenAction.LoadNextPage)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.selectedRecords.isEmpty()) {
                ScrollableRecordsTopBar(
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
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                MultiSelectTopBar(
                    selectedItemsCount = uiState.selectedRecords.size,
                    onCancelClick = { onAction(RecordsScreenAction.MultiSelectCancel) },
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

        }
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
                if (uiState.isShowLoadingProgress && uiState.recordsMap.isEmpty()) {
                    // Full-screen loading indicator on initial load
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (!uiState.isShowLoadingProgress && uiState.recordsMap.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_audiotrack_64),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.bookmarksSelected) {
                                stringResource(R.string.no_bookmarks)
                            } else {
                                stringResource(R.string.no_records)
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (uiState.showDeletedRecordsButton) {
                            item(key = "trash_button") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showDeletedRecordsScreen() },
                                ) {
                                    Column {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_delete),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = stringResource(R.string.trash),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                fontSize = 18.sp,
                                            )
                                        }
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }
                        }
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
                                            textAlign = TextAlign.Center,
                                            fontSize = 16.sp,
                                        )
                                    }
                                }
                            }
                            //The list of items for that specific date
                            items(recordsOnDate, key = { it.recordId }) { record ->
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
                                                onHomeAction(HomeScreenAction.OnStartHomeScreen)
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
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        // Add bottom spacing when TouchPanel is visible so the last items
                        // are not covered by the playback panel overlay
                        if (uiState.isShowLoadingProgress && uiState.recordsMap.isNotEmpty()) {
                            item(key = "loading_indicator") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        if (uiState.showRecordPlaybackPanel) {
                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(225.dp))
                            }
                        }
                    }
                    if (uiState.showMoveToRecycleDialog) {
                        uiState.operationSelectedRecord?.let { record ->
                            DeleteDialog(
                                dialogText = stringResource(id = R.string.move_record_to_trash, record.name),
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
                    if (uiState.showLostRecordsDialog) {
                        LostRecordsDialog(
                            onDismiss = {
                                onAction(RecordsScreenAction.DismissLostRecordsDialog)
                            },
                            onDetailsClick = {
                                onAction(RecordsScreenAction.DismissLostRecordsDialog)
                                showLostRecordsScreen(uiState.lostRecords)
                            }
                        )
                    }
                }
                if (!uiState.isRecording) {
                    TouchPanel(
                        showRecordPlaybackPanel = uiState.showRecordPlaybackPanel,
                        uiHomeState = uiHomeState,
                        isBookmarked = uiState.activeRecord?.isBookmarked ?: false,
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
                        onBookmarkClick = {
                            onAction(RecordsScreenAction.BookmarkActiveRecord)
                        },
                        onPrevClick = {
                            onAction(RecordsScreenAction.PlayPreviousRecord)
                            onHomeAction(HomeScreenAction.LoadActiveRecordAndPlay)
                        },
                        onNextClick = {
                            onAction(RecordsScreenAction.PlayNextRecord)
                            onHomeAction(HomeScreenAction.LoadActiveRecordAndPlay)
                        },
                    )
                }
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
    RecordsScreen({}, {}, {}, {},
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
            waveformState = getTestWaveformData(),
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
    RecordsScreen({}, {}, {}, {},
        RecordsScreenState(),
        null, {},
        uiHomeState = HomeScreenState(),
        onHomeAction = {}
    )
}


@Preview(showBackground = true)
@Composable
fun RecordsScreenLoadingPreview() {
    RecordsScreen({}, {}, {}, {},
        RecordsScreenState(isShowLoadingProgress = true),
        null, {},
        uiHomeState = HomeScreenState(),
        onHomeAction = {}
    )
}