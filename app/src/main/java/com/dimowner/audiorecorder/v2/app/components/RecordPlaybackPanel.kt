package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.v2.app.getTestWaveformData
import com.dimowner.audiorecorder.v2.app.home.HomeScreenState
import com.dimowner.audiorecorder.v2.app.home.LegacySlider
import com.dimowner.audiorecorder.v2.app.home.PlayPanel

@Composable
internal fun RecordPlaybackPanel(
    modifier: Modifier,
    uiState: HomeScreenState,
    onProgressChange: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekProgress: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize().padding(12.dp),
            textAlign = TextAlign.Center,
            text = uiState.time,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                modifier = Modifier
                    .wrapContentHeight().weight(1f)
                    .padding(8.dp, 6.dp, 8.dp, 0.dp),
                textAlign = TextAlign.Center,
                text = uiState.recordName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(8.dp, 0.dp),
                textAlign = TextAlign.Start,
                text = uiState.endTime,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
        LegacySlider(
            progress = uiState.progress,
            onProgressChange = onProgressChange
        )
        PlayPanel(
            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
            showPause = uiState.showPause,
            showStop = uiState.showStop,
            onPlayClick = { onPlayClick() },
            onStopClick = { onStopClick() },
            onPauseClick = { onPauseClick() }
        )
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
            onProgressChange = {},
            onSeekStart = {},
            onSeekProgress = {},
            onSeekEnd = {},
            onPlayClick = {},
            onStopClick = {},
            onPauseClick = {}
        )
    }
}
