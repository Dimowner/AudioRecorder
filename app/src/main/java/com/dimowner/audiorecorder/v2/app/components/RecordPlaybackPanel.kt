package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.getTestWaveformData
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import com.dimowner.audiorecorder.v2.app.home.LegacySlider
import com.dimowner.audiorecorder.v2.app.home.PlayPanel

@Composable
internal fun RecordPlaybackPanel(
    modifier: Modifier,
    uiState: HomeScreenState,
    isBookmarked: Boolean = false,
    onProgressChange: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekProgress: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onBookmarkClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time + bookmark row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.size(48.dp))
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                textAlign = TextAlign.Center,
                text = uiState.time,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onBookmarkClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = if (isBookmarked) {
                        painterResource(id = R.drawable.ic_bookmark)
                    } else {
                        painterResource(id = R.drawable.ic_bookmark_bordered)
                    },
                    contentDescription = stringResource(id = R.string.bookmarks),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        WaveformComposeView(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            state = uiState.waveformState,
            showTimeline = false,
            onSeekStart = {
                onSeekStart()
            },
            onSeekProgress = { mills ->
                onSeekProgress(mills)
            },
            onSeekEnd = { mills ->
                onSeekEnd(mills)
            }
        )
        Row(
            modifier = Modifier.padding(0.dp, 8.dp, 0.dp, 0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp, 0.dp),
                textAlign = TextAlign.Start,
                text = uiState.startTime,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                modifier = Modifier
                    .wrapContentHeight().weight(1f)
                    .padding(8.dp, 6.dp, 8.dp, 0.dp),
                textAlign = TextAlign.Center,
                text = uiState.recordName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp, 0.dp),
                textAlign = TextAlign.Start,
                text = uiState.endTime,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LegacySlider(
            progress = uiState.progress,
            onProgressChange = onProgressChange
        )
        // Prev / Play controls / Next row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevClick,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = stringResource(id = R.string.btn_previous),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            PlayPanel(
                modifier = Modifier.wrapContentHeight().wrapContentSize(),
                showPause = uiState.showPause,
                showStop = uiState.showStop,
                onPlayClick = { onPlayClick() },
                onStopClick = { onStopClick() },
                onPauseClick = { onPauseClick() }
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = stringResource(id = R.string.btn_next),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview
@Composable
fun PlaybackPanelPreview() {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        RecordPlaybackPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            uiState = HomeScreenState(
                waveformState = getTestWaveformData(),
                startTime = "00:00",
                endTime = "3:42",
                time = "1:51",
                recordName = "Test Record Name",
                recordInfo = "1.5 MB, mp4, 192 kbps, 48 kHz",
                isContextMenuAvailable = true,
                isStopRecordingButtonAvailable = true,
            ),
            isBookmarked = false,
            onProgressChange = {},
            onSeekStart = {},
            onSeekProgress = {},
            onSeekEnd = {},
            onPlayClick = {},
            onStopClick = {},
            onPauseClick = {},
            onBookmarkClick = {},
            onPrevClick = {},
            onNextClick = {},
        )
    }
}
