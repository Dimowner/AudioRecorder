package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.getTestWaveformData
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import com.dimowner.audiorecorder.v2.app.home.LegacySlider
import com.dimowner.audiorecorder.v2.app.home.PlayPanel
import com.dimowner.audiorecorder.v2.data.model.PlaybackSpeed

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
    onPlaybackSpeedClick: (PlaybackSpeed) -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Grab handle indicates the panel can be dragged to dismiss.
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
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
            modifier = Modifier.fillMaxWidth().height(42.dp),
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
                    .padding(8.dp, 4.dp, 8.dp, 0.dp),
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
        // Row mirrors button positions in RTL automatically, but the skip icons keep
        // their fixed visual direction, so swap which callback each button fires to
        // match what its position/icon now indicates.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = if (isRtl) onNextClick else onPrevClick,
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
                selectedSpeed = uiState.playbackSpeed,
                onPlayClick = { onPlayClick() },
                onStopClick = { onStopClick() },
                onPauseClick = { onPauseClick() },
                onPlaybackSpeedClick = onPlaybackSpeedClick,
                // Only the extreme rates: this row also carries the prev/next buttons.
                speeds = PlaybackSpeed.compact
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = if (isRtl) onPrevClick else onNextClick,
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
