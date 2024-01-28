package com.dimowner.audiorecorder.app.v2.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dimowner.audiorecorder.R

@Composable
fun UserInputScreen(
    navController: NavHostController,
    userInputViewModel: UserInputViewModel,
    showWelcomeScreen: (Pair<String, String>) -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxSize()
//            .clickable {
//                navController.navigate(Routes.WELCOME_SCREEN)
//            },
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            TopBar("Hi there \uD83D\uDE0A")
            TextComponent(textValue = "Lets learn about you", textSize = 24.sp)
            Spacer(modifier = Modifier.size(20.dp))
            TextComponent(
                textValue = "This app will prepare a details page based on input provided by you!",
                textSize = 18.sp
            )
            Spacer(modifier = Modifier.size(60.dp))
            TextComponent(textValue = "Name", textSize = 18.sp)
            Spacer(modifier = Modifier.size(10.dp))
            TextFieldComponent(onTextChanged = {
                userInputViewModel.onEvent(UserDataUiEvents.UserNameEntered(it))
            })
            Spacer(modifier = Modifier.size(20.dp))
            TextComponent(textValue = "What do you like?", textSize = 18.sp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
//                    navController.navigate(Routes.WELCOME_SCREEN)
                    showWelcomeScreen(
                        Pair(
                            userInputViewModel.uiState.value.nameEntered,
                            userInputViewModel.uiState.value.animalSelected
                        )
                    )
                })
            }
        }
    }
}

@Preview
@Composable
fun UserInputScreenPreview() {
    UserInputScreen(rememberNavController(), viewModel(), {})
}
