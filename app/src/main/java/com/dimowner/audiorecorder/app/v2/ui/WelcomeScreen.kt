package com.dimowner.audiorecorder.app.v2.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(userName: String?, animalSelected: String?) {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            TopBar("Welcome $userName \uD83D\uDE0A")
            TextComponent(textValue = "Thank you for sharing your data!", textSize = 24.sp)
            Spacer(modifier = Modifier.size(60.dp))
            TextComponent(
                textValue = "You are $animalSelected lover!",
                textSize = 24.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.size(16.dp))
            InfoCard(animalSelected = animalSelected)

        }
    }
}


@Preview
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen("name", "animal")
}
