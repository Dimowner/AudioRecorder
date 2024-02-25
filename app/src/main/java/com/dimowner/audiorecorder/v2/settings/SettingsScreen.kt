package com.dimowner.audiorecorder.v2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.TitleBar
import timber.log.Timber

@Composable
fun SettingsScreen(
    navController: NavHostController,
) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBar(
                stringResource(R.string.settings),
                onBackPressed = {
                    navController.popBackStack()
                })
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
            ) {
                Spacer(modifier = Modifier.size(8.dp))
                SettingsItem(stringResource(R.string.trash), R.drawable.ic_delete, {

                })
                SettingsItem(stringResource(R.string.app_name), R.drawable.ic_color_lens, {

                })
                SettingsItemCheckBox(stringResource(R.string.keep_screen_on), R.drawable.ic_lightbulb_on, {

                })
                SettingsItemCheckBox(stringResource(R.string.ask_to_rename), R.drawable.ic_pencil, {

                })
                SettingsItem(stringResource(R.string.rec_format), R.drawable.ic_title, {

                })
                Spacer(modifier = Modifier.size(8.dp))
                ResetRecordingSettingsPanel("1 Mb/min expected size\nM4a, 44.1kHz, 256kbps, Stereo", {

                })
                val formats = stringArrayResource(id = R.array.formats2).toList()
                SettingSelector(
                    name = stringResource(id = R.string.recording_format),
                    chips = formats.mapIndexed { index, format ->
                        ChipItem(id = index, value = index, format, false)
                    },
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    },
                    onClickInfo = { Timber.v("MY_TEST: onClickInfo") }
                )
                val sampleRates = stringArrayResource(id = R.array.sample_rates2).toList()
                SettingSelector(
                    name = stringResource(id = R.string.sample_rate),
                    chips = sampleRates.mapIndexed { index, rate ->
                        ChipItem(id = index, value = index, rate, false)
                    },
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    },
                    onClickInfo = { Timber.v("MY_TEST: onClickInfo") }
                )
                val bitRates = stringArrayResource(id = R.array.bit_rates2).toList()
                SettingSelector(
                    name = stringResource(id = R.string.bitrate),
                    chips = bitRates.mapIndexed { index, rate ->
                        ChipItem(id = index, value = index, rate, false)
                    },
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    },
                    onClickInfo = { Timber.v("MY_TEST: onClickInfo") }
                )
                val channels = stringArrayResource(id = R.array.channels).toList()
                SettingSelector(
                    name = stringResource(id = R.string.channels),
                    chips = channels.mapIndexed { index, channel ->
                        ChipItem(id = index, value = index, channel, false)
                    },
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    },
                    onClickInfo = { Timber.v("MY_TEST: onClickInfo") }
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingsItem(stringResource(R.string.rate_app), R.drawable.ic_thumbs, {

                })
                SettingsItem(stringResource(R.string.request), R.drawable.ic_chat_bubble, {

                })
                Spacer(modifier = Modifier.size(8.dp))
                InfoTextView("InfoTextView")
                InfoTextView("InfoTextView")
                InfoTextView("InfoTextView")
                AppInfoView(stringResource(id = R.string.app_name), stringResource(id = R.string.app_name))
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}


@Preview
@Composable
fun RecordInfoScreenPreview() {
    SettingsScreen(rememberNavController())
}