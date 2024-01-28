package com.dimowner.audiorecorder.app.v2.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class UserInputViewModel: ViewModel() {

    val uiState = mutableStateOf(UserInputScreenState())

    fun onEvent(event: UserDataUiEvents) {
        when(event) {
            is UserDataUiEvents.UserNameEntered -> {
                uiState.value = uiState.value.copy(
                    nameEntered = event.name
                )
            }
            is UserDataUiEvents.AnimalSelected -> {
                uiState.value = uiState.value.copy(
                    animalSelected = event.animalValue
                )
            }
        }

    }

    fun isValidState(): Boolean {
        return uiState.value.animalSelected.isNotEmpty() && uiState.value.nameEntered.isNotEmpty()
    }
}

data class UserInputScreenState(
    val nameEntered: String = "",
    val animalSelected: String = ""
)

sealed class UserDataUiEvents {
    data class UserNameEntered(val name: String): UserDataUiEvents()
    data class AnimalSelected(val animalValue: String): UserDataUiEvents()
}
