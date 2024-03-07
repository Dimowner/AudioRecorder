package com.dimowner.audiorecorder.v2.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.TitleBar
import timber.log.Timber

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    viewModel.initSettings()
    val context = LocalContext.current
    val state = viewModel.state.observeAsState()

    val openInfoDialog = remember { mutableStateOf(false) }
    val openWarningDialog = remember { mutableStateOf(false) }
    val infoText = remember { mutableStateOf("") }
    val warningText = remember { mutableStateOf("") }

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
                SettingsItem(stringResource(R.string.trash), R.drawable.ic_delete) {

                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsItemCheckBox(
                        state.value?.isDynamicColors ?: false,
                        stringResource(R.string.dynamic_theme_colors),
                        R.drawable.ic_palette_outline,
                        {
                            viewModel.setDynamicTheme(it)
                        })
                }
                SettingsItemCheckBox(
                    state.value?.isDarkTheme ?: false,
                    stringResource(R.string.dark_theme),
                    R.drawable.ic_dark_mode,
                    {
                        viewModel.setDarkTheme(it)
                    })
                SettingsItemCheckBox(
                    state.value?.isKeepScreenOn ?: false,
                    stringResource(R.string.keep_screen_on),
                    R.drawable.ic_lightbulb_on,
                    {
                        viewModel.setKeepScreenOn(it)
                    })
                SettingsItemCheckBox(
                    state.value?.isShowRenameDialog ?: false,
                    stringResource(R.string.ask_to_rename),
                    R.drawable.ic_pencil,
                    {
                        viewModel.setShowRenamingDialog(it)
                    })
                DropDownSetting(
                    items = state.value?.nameFormats ?: emptyList(),
                    selectedItem = state.value?.selectedNameFormat,
                    onSelect = {
                        viewModel.setNameFormat(it)
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                ResetRecordingSettingsPanel(
                    stringResource(id = R.string.size_per_min, state.value?.sizePerMin ?: ""),
                    state.value?.recordingSettingsText ?: ""
                ) {
                    viewModel.resetRecordingSettings()
                }
                SettingSelector(
                    name = stringResource(id = R.string.recording_format),
                    chips = state.value?.recordingSetting?.recordingFormats ?: emptyList(),
                    onSelect = {
                        viewModel.selectRecordingFormat(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_format)
                        openInfoDialog.value = true
                    }
                )
                SettingSelector(
                    name = stringResource(id = R.string.sample_rate),
                    chips = state.value?.recordingSetting?.sampleRates ?: emptyList(),
                    onSelect = {
                        viewModel.selectSampleRate(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_frequency)
                        openInfoDialog.value = true
                    }
                )
                SettingSelector(
                    name = stringResource(id = R.string.bitrate),
                    chips = state.value?.recordingSetting?.bitRates ?: emptyList(),
                    onSelect = {
                        viewModel.selectBitrate(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_bitrate)
                        openInfoDialog.value = true
                    }
                )
                SettingSelector(
                    name = stringResource(id = R.string.channels),
                    chips = state.value?.recordingSetting?.channelCounts ?: emptyList(),
                    onSelect = {
                        viewModel.selectChannelCount(it.value)
                    },
                    onClickInfo = {
                        infoText.value = context.getString(R.string.info_channels)
                        openInfoDialog.value = true
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                SettingsItem(stringResource(R.string.rate_app), R.drawable.ic_thumbs) {
                    rateApp(context)
                }
                SettingsItem(stringResource(R.string.request), R.drawable.ic_chat_bubble) {
                    requestFeature(context) {
                        warningText.value = it
                        openWarningDialog.value = true
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                InfoTextView(
                    stringResource(
                        id = R.string.total_record_count,
                        (state.value?.totalRecordCount ?: "")
                    )
                )
                InfoTextView(
                    stringResource(
                        id = R.string.total_duration,
                        (state.value?.totalRecordDuration ?: "")
                    )
                )
                InfoTextView(
                    stringResource(
                        id = R.string.available_space,
                        (state.value?.availableSpace ?: "")
                    )
                )
                AppInfoView(state.value?.appName ?: "", state.value?.appVersion ?: "")
                Spacer(modifier = Modifier.size(8.dp))
            }
            if (openInfoDialog.value) {
                SettingsInfoDialog(openInfoDialog, infoText.value)
            }
            if (openWarningDialog.value) {
                SettingsWarningDialog(openWarningDialog, warningText.value)
            }
        }
    }
}

@Preview
@Composable
fun RecordInfoScreenPreview() {
    SettingsScreen(rememberNavController())
}