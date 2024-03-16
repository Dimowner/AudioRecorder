package com.dimowner.audiorecorder.v2.app.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.InfoItem
import com.dimowner.audiorecorder.v2.app.TitleBar

@Composable
fun RecordInfoScreen(
    navController: NavHostController,
    recordInfo: RecordInfoState?
) {

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                stringResource(id = R.string.info),
                onBackPressed = { navController.popBackStack() }
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
            ) {
                Spacer(modifier = Modifier.size(8.dp))

                //TODO: Fix Record info display
//                val recordInfo = RecordInfoState(
//                    name = "name666",
//                    format = "format777",
//                    duration = TimeUtils.formatTimeIntervalHourMinSec2(150000000/1000),
//                    size = ARApplication.injector.provideSettingsMapper(context).formatSize(1500000),
//                    location = "location888",
//                    created = TimeUtils.formatDateTimeLocale(System.currentTimeMillis()),
//                    sampleRate = stringResource(R.string.value_hz, 44000),
//                    channelCount = stringResource(R.string.mono),
//                    bitrate = stringResource(R.string.value_kbps, 240000/1000),
//                )

                InfoItem(stringResource(R.string.rec_name), recordInfo?.name ?: "")
                InfoItem(stringResource(R.string.rec_format), recordInfo?.format ?: "")
                InfoItem(stringResource(R.string.bitrate), recordInfo?.bitrate.toString())
                InfoItem(stringResource(R.string.channels), recordInfo?.channelCount.toString())
                InfoItem(stringResource(R.string.sample_rate), recordInfo?.sampleRate.toString())
                InfoItem(stringResource(R.string.rec_duration), recordInfo?.duration.toString())
                InfoItem(stringResource(R.string.rec_size), recordInfo?.size.toString())
                InfoItem(stringResource(R.string.rec_location), recordInfo?.location ?: "")
                InfoItem(stringResource(R.string.rec_created), recordInfo?.created.toString())
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}


@Preview
@Composable
fun RecordInfoScreenPreview() {
    RecordInfoScreen(rememberNavController(), null)
}