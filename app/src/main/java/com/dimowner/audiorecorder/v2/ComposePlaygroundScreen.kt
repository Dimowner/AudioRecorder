package com.dimowner.audiorecorder.v2

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.util.TimeUtils
import com.dimowner.audiorecorder.v2.info.RecordInfoState
import com.dimowner.audiorecorder.v2.settings.ChipItem
import com.dimowner.audiorecorder.v2.settings.SettingSelector
import com.google.gson.Gson
import timber.log.Timber

@Composable
fun ComposePlaygroundScreen(
    navController: NavHostController,
    userInputViewModel: UserInputViewModel,
    showWelcomeScreen: (Pair<String, String>) -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showSettingsScreen: () -> Unit
) {
    val context = LocalContext.current

    val recordInfo = RecordInfoState(
        name = "name666",
        format = "format777",
        duration = TimeUtils.formatTimeIntervalHourMinSec2(150000000/1000),
        size = ARApplication.injector.provideSettingsMapper(context).formatSize(1500000),
        location = "location888",
        created = TimeUtils.formatDateTimeLocale(System.currentTimeMillis()),
        sampleRate = stringResource(R.string.value_hz, 44000),
        channelCount = stringResource(R.string.mono),
        bitrate = stringResource(R.string.value_kbps, 240000/1000),
    )

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(weight = 1f, fill = false)
                    .padding(16.dp)
            ) {
                TextComponent(textValue = "Name", textSize = 18.sp)
                Spacer(modifier = Modifier.size(10.dp))
                TextFieldComponent(onTextChanged = {
                    userInputViewModel.onEvent(UserDataUiEvents.UserNameEntered(it))
                })
                Spacer(modifier = Modifier.size(10.dp))
                TextComponent(textValue = "What do you like?", textSize = 18.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimalCard(image = R.drawable.ic_audiotrack_64, onImageClicked = {
                        userInputViewModel.onEvent(UserDataUiEvents.AnimalSelected(it))
                    }, selected = userInputViewModel.uiState.value.animalSelected == "Cat")
                    AnimalCard(image = R.drawable.ic_color_lens, onImageClicked = {
                        userInputViewModel.onEvent(UserDataUiEvents.AnimalSelected(it))
                    }, selected = userInputViewModel.uiState.value.animalSelected == "Dog")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (userInputViewModel.isValidState()) {
                    ButtonComponent(onClicked = {
                        showWelcomeScreen(
                            Pair(
                                userInputViewModel.uiState.value.nameEntered,
                                userInputViewModel.uiState.value.animalSelected
                            )
                        )
                    }, text = "Go to details screen")
                }
                Row {
                    ButtonComponent(onClicked = {
                        val json = Uri.encode(Gson().toJson(recordInfo))
                        showRecordInfoScreen.invoke(json)
                    }, text = "Record Info")
                    ButtonComponent(onClicked = {
                        showSettingsScreen.invoke()
                    }, text = "Settings")
                }

                // Text variations
                Text(
                    text = "Primary Text",
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = "Secondary Text",
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = "Error Text",
                    modifier = Modifier.padding(16.dp),
                )

                Spacer(modifier = Modifier.size(10.dp))

                SettingSelector(
                    name = "Test Name",
                    chips = listOf(
                        ChipItem(id = 0, value = 1000, "1000", false),
                        ChipItem(id = 1, value = 2000, "2000", true),
                        ChipItem(id = 2, value = 3000, "3000", false),
                        ChipItem(id = 4, value = 4, "4", false),
                        ChipItem(id = 5, value = 5, "5", false),
                        ChipItem(id = 6, value = 600, "600", false),
                        ChipItem(id = 7, value = 70000, "70000", false),
                    ),
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    }
                )
                // Buttons with different states
                Button(
                    onClick = { context.startActivity(Intent(context, MainActivity::class.java)) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(text = "Audio Recorder",)
                }
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text(text = "Disabled Button",)
                }
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "Elevated Surface",
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
                LinearProgressIndicator(
                    progress = { 0.5f },
                    modifier = Modifier.padding(16.dp),
                )
                Slider(
                    value = 0.5f,
                    onValueChange = {},
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        enabled = true,
                        modifier = Modifier.padding(16.dp)
                    )
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = true,
                        modifier = Modifier.padding(16.dp)
                    )
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(16.dp)
                )
                BottomAppBar(
                    content = {
                        Text(
                            text = "Bottom App Bar",
                        )
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun UserInputScreenPreview() {
    ComposePlaygroundScreen(rememberNavController(), viewModel(), {}, {}, {})
}
