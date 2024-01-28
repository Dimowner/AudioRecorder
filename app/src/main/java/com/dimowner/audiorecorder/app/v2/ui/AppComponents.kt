package com.dimowner.audiorecorder.app.v2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R

@Composable
fun TopBar(value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        Image(
            modifier = Modifier.size(64.dp),
            painter = painterResource(id = R.drawable.ic_bookmark),
            contentDescription = "My test image"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreview() {
    TopBar("Text")
}

@Composable
fun TextComponent(
    textValue: String,
    textSize: TextUnit,
    colorValue: Color = Color.Black,
    fontWeight: FontWeight = FontWeight.Light
) {
    Text(
        text = textValue,
        fontSize = textSize,
        color = colorValue,
        fontWeight = fontWeight,
    )
}

@Preview(showBackground = true)
@Composable
fun TextComponentPreview() {
    TextComponent(textValue = "Text to preview", textSize = 24.sp)
}

@Composable
fun TextFieldComponent(onTextChanged: (name: String) -> Unit) {

    var currentValue by remember {
       mutableStateOf("")
    }

    val localFocusManager = LocalFocusManager.current
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = currentValue,
        onValueChange = {
            currentValue = it
            onTextChanged(it)
        },
        placeholder = {
            Text(text = "Enter your name", fontSize = 18.sp)
        },
        textStyle = TextStyle.Default.copy(fontSize = 24.sp),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions {
            localFocusManager.clearFocus()
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TextFieldComponentPreview() {
    TextFieldComponent {}
}

@Composable
fun AnimalCard(image: Int, selected: Boolean, onImageClicked: (animalName: String) -> Unit) {
    val localFocusManager = LocalFocusManager.current
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(16.dp)
            .size(130.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = if (selected) Color.Green else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Image(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .clickable {
                        val animalName = if (image == R.drawable.ic_audiotrack_64) "Cat" else "Dog"
                        onImageClicked(animalName)
                        localFocusManager.clearFocus()
                    },
                painter = painterResource(id = image),
                contentDescription = "Animal image",
            )
        }
    }
}

@Preview()
@Composable
fun AnimalCardPreview() {
    AnimalCard(image = R.drawable.ic_color_lens, false) {}
}


@Composable
fun ButtonComponent(onClicked: () -> Unit) {
    Button(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        onClick = { onClicked() }
    ) {
        TextComponent(textValue = "Go to details screen", textSize = 18.sp, colorValue = Color.White)
    }
}

@Preview()
@Composable
fun ButtonComponentPreview() {
    ButtonComponent {}
}

@Composable
fun InfoCard(animalSelected: String?) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(16.dp),
        elevation = CardDefaults.cardElevation()
    ) {
        Text(
            modifier = Modifier.padding(18.dp, 24.dp),
            text = if (animalSelected == "Dog") "Dog info" else if (animalSelected == "Cat") "Cat info" else "",
            fontSize = 18.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium,

        )
    }
}

@Preview()
@Composable
fun InfoCardPreview() {
    InfoCard("This is information card to provide some info")
}
