package com.dimowner.audiorecorder.v2.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R

@Composable
fun TextComponent(
    textValue: String,
    textSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Light
) {
    Text(
        text = textValue,
        fontSize = textSize,
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
            .size(56.dp)
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

@Composable
fun TitleBar(value: String, onBackPressed: () -> Unit) {
//    val localFocusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .height(60.dp)
            .fillMaxWidth()
            .padding(0.dp, 4.dp, 0.dp, 0.dp)
            .background(color = MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        FilledIconButton(
            onClick = onBackPressed,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterVertically),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Navigate back",
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Light
                )
            ),
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
fun TitleBarPreview() {
    TitleBar("Title bar", {})
}

@Composable
fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            modifier = Modifier
                .padding(16.dp, 8.dp, 16.dp, 2.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Light
                )
            ),
        )
        Text(
            modifier = Modifier
                .padding(16.dp, 2.dp, 16.dp, 8.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InfoItemPreview() {
    InfoItem("Label", "Value")
}

@Composable
fun ConfirmationAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    painter: Painter,
    positiveButton: String,
    negativeButton: String,
) {
    AlertDialog(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    painter = painter,
                    contentDescription = dialogTitle
                )
                Text(text = dialogTitle)
            }
        },
        text = {
            Text(text = dialogText, fontSize = 18.sp,)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text(positiveButton)
            }

        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(negativeButton)
            }
        }
    )
}

@Composable
fun InfoAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
    dismissButton: String,
) {
    AlertDialog(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    imageVector = icon,
                    contentDescription = dialogTitle
                )
                Text(text = dialogTitle)
            }
        },
        text = {
            Text(text = dialogText,
                style = TextStyle(
                    fontSize = 18.sp,
                    lineBreak = LineBreak.Heading,
                    fontWeight = FontWeight.Normal,
                )
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text(dismissButton)
            }
        },
    )
}

@Composable
fun RenameAlertDialog(
    openDialog: MutableState<Boolean>,
    recordName: String,
    onAcceptClick: (String) -> Unit
) {
    AlertDialog(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    painter = painterResource(id = R.drawable.ic_pencil),
                    contentDescription = stringResource(id = R.string.record_name)
                )
                Text(text = stringResource(id = R.string.record_name))
            }
        },
        text = {
            Text(text = recordName, fontSize = 18.sp,)
        },
        onDismissRequest = {
            openDialog.value = false
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAcceptClick("NewRecordName")
                }
            ) {
                Text(stringResource(id = R.string.btn_save))
            }

        },
        dismissButton = {
            TextButton(
                onClick = {
                    openDialog.value = false
                }
            ) {
                Text(stringResource(id = R.string.btn_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun RenameAlertDialogPreview() {
    RenameAlertDialog(remember { mutableStateOf(true) }, "Record-14", {})
}

@Composable
fun DropDownMenuItem(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() },
    ) {
        Icon(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            painter = painterResource(id = iconRes),
            contentDescription = text,
        )
        Text(
            modifier = Modifier
                .padding(0.dp, 16.dp, 16.dp, 16.dp)
                .wrapContentSize(),
            text = text,
            fontSize = 18.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Light
                )
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DropDownMenuItemPreview() {
    DropDownMenuItem("Label", R.drawable.ic_palette_outline, {})
}

@Composable
fun <T> RecordsDropDownMenu(
    items: List<DropDownMenuItem<T>>,
    onItemClick: (T) -> Unit,
    expanded: MutableState<Boolean>
) {
    DropdownMenu(
        modifier = Modifier.wrapContentSize(),
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false }
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                onClick = {},
                text = {
                    DropDownMenuItem(
                        text = stringResource(id = item.textResId),
                        iconRes = item.imageResId,
                        onClick = {
                            onItemClick(item.id)
                            expanded.value = false
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun DeleteDialog(
    openDialog: MutableState<Boolean>,
    recordName: String,
    onAcceptClick: () -> Unit
) {
    if (openDialog.value) {
        ConfirmationAlertDialog(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = {
                openDialog.value = false
                onAcceptClick()
            },
            dialogTitle = stringResource(id = R.string.warning),
            dialogText = stringResource(id = R.string.delete_record, recordName),
            painter = painterResource(id = R.drawable.ic_delete_forever),
            positiveButton = stringResource(id = R.string.btn_yes),
            negativeButton = stringResource(id = R.string.btn_no)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteDialogPreview() {
    DeleteDialog(remember { mutableStateOf(true) }, "Record-14", {})
}

@Composable
fun SaveAsDialog(
    openDialog: MutableState<Boolean>,
    recordName: String,
    onAcceptClick: () -> Unit
) {
    if (openDialog.value) {
        ConfirmationAlertDialog(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = {
                openDialog.value = false
                onAcceptClick()
            },
            dialogTitle = stringResource(id = R.string.save_as),
            dialogText = stringResource(id = R.string.record_name_will_be_copied_into_downloads, recordName),
            painter = painterResource(id = R.drawable.ic_save_alt),
            positiveButton = stringResource(id = R.string.btn_yes),
            negativeButton = stringResource(id = R.string.btn_no)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SaveAsDialogPreview() {
    SaveAsDialog(remember { mutableStateOf(true) }, "Record-14", {})
}

data class DropDownMenuItem<T>(
    val id: T,
    val textResId: Int,
    val imageResId: Int
)

