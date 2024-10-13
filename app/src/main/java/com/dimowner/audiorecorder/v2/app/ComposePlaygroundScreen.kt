package com.dimowner.audiorecorder.v2.app

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.app.main.MainActivity
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import com.dimowner.audiorecorder.v2.app.info.RecordInfoState
import com.dimowner.audiorecorder.v2.app.settings.ChipItem
import com.dimowner.audiorecorder.v2.app.settings.SettingSelector
import com.google.gson.Gson
import timber.log.Timber

@Composable
fun ComposePlaygroundScreen(
    userInputViewModel: UserInputViewModel = viewModel(),
    showDetailsScreen: (Pair<String, String>) -> Unit,
    showRecordInfoScreen: (String) -> Unit,
    showSettingsScreen: () -> Unit,
    showHomeScreen: () -> Unit,
    showRecordsScreen: () -> Unit,
    showWelcomeScreen: () -> Unit,
    showDeletedRecordsScreen: () -> Unit,
) {
    val context = LocalContext.current

    val recordInfo = RecordInfoState(
        name = "name666",
        format = "format777",
        duration = 150000000,
        size = 1500000,
        location = "location888",
        created = System.currentTimeMillis(),
        sampleRate = 44000,
        channelCount = 1,
        bitrate = 240000,
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
                    Button(onClick = {
                        showDetailsScreen(
                            Pair(
                                userInputViewModel.uiState.value.nameEntered,
                                userInputViewModel.uiState.value.animalSelected
                            )
                        )
                    }) {
                        Text(text = "Go to details screen",)
                    }
                }
                Row {
                    Button(onClick = {
                        val json = Uri.encode(Gson().toJson(recordInfo))
                        showRecordInfoScreen(json)
                    }) {
                        Text(text = "Record Info",)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { showSettingsScreen() }) {
                        Text(text = "Settings Screen",)
                    }
                }
                Row {
                    Button(onClick = { showHomeScreen() }) {
                        Text(text = "Home Screen",)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { showRecordsScreen() }) {
                        Text(text = "Records Screen",)
                    }
                }
                Row {
                    Button(onClick = { showWelcomeScreen() }) {
                        Text(text = "Welcome Screen",)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { showDeletedRecordsScreen() }) {
                        Text(text = "Deleted Records",)
                    }
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
                        ChipItem(id = 0, value = SampleRate.SR8000, name = "8000", false),
                        ChipItem(id = 1, value = SampleRate.SR16000, name = "16000", false),
                        ChipItem(id = 2, value = SampleRate.SR22500, name = "22500", true),
                        ChipItem(id = 3, value = SampleRate.SR32000, name = "32000", false),
                        ChipItem(id = 4, value = SampleRate.SR44100, name = "44100", false),
                        ChipItem(id = 5, value = SampleRate.SR48000, name = "48000", false),
                    ),
                    onSelect = {
                        Timber.v("MY_TEST: onSelect = " + it.name)
                    },
                    onClickInfo = { Timber.v("MY_TEST: onClickInfo") }
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
//                CircularProgressIndicator(
//                    modifier = Modifier.padding(16.dp)
//                )
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
fun ComposePlaygroundScreenPreview() {
    ComposePlaygroundScreen(viewModel(), {}, {}, {}, {}, {}, {}, {})
}
