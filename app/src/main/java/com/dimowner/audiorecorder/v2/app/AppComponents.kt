/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.dimowner.audiorecorder.AppConstantsV2.RECORD_DESCRIPTION_MAX_LENGTH
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
fun TitleBar(
    title: String,
    onBackPressed: () -> Unit,
    actionButtonText: String = "",
    onActionClick: (() -> Unit)? = null
) {
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
        IconButton(
            onClick = onBackPressed,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.navigate_back),
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
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
        if (onActionClick != null) {
            Button(
                modifier = Modifier
                    .padding(8.dp)
                    .wrapContentSize(),
                onClick = { onActionClick() }
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TitleBarPreview() {
    TitleBar("Title bar", {}, "BtnText", {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableTitleBar(
    title: String,
    onBackPressed: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actionButtonText: String = "",
    onActionClick: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
                fontWeight = FontWeight.Light,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(58.dp, 54.dp)
                    .padding(8.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            if (onActionClick != null) {
                Button(
                    modifier = Modifier
                        .padding(8.dp)
                        .wrapContentSize(),
                    onClick = { onActionClick() }
                ) {
                    Text(
                        text = actionButtonText,
                        fontSize = 16.sp,
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ScrollableTitleBarPreview() {
    ScrollableTitleBar("Title bar", {}, TopAppBarDefaults.enterAlwaysScrollBehavior(),"BtnText", {})
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
            fontWeight = FontWeight.Light,
            fontSize = 18.sp,
        )
        Text(
            modifier = Modifier
                .padding(16.dp, 2.dp, 16.dp, 8.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
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
            Text(text = dialogText, fontSize = 18.sp)
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
    dialogText: AnnotatedString,
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
            Text(
                text = dialogText,
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
    recordName: String,
    onAcceptClick: (String) -> Unit,
    onDismissClick: () -> Unit,
    onDontAskAgain: (Boolean) -> Unit = {},
    showDontAskAgain: Boolean = false
) {
    val currentValue = remember { mutableStateOf(recordName) }
    val checkedState = remember { mutableStateOf(false) }
    val isNameEmpty = currentValue.value.isBlank()
    // If recordName arrives after first composition (e.g. async state update), populate
    // the field as long as the user hasn't started typing yet.
    LaunchedEffect(recordName) {
        if (currentValue.value != recordName) {
            currentValue.value = recordName
        }
    }
    AlertDialog(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    painter = painterResource(id = R.drawable.ic_pencil),
                    contentDescription = stringResource(id = R.string.update_record_name)
                )
                Text(text = stringResource(id = R.string.update_record_name))
            }
        },
        text = {
            val localFocusManager = LocalFocusManager.current
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = currentValue.value,
                    onValueChange = {
                        currentValue.value = it
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.rename), fontSize = 18.sp)
                    },
                    textStyle = TextStyle.Default.copy(fontSize = 20.sp),
                    isError = isNameEmpty,
                    supportingText = if (isNameEmpty) {
                        { Text(text = stringResource(id = R.string.msg_name_cannot_be_empty)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions {
                        localFocusManager.clearFocus()
                    }
                )
                if (showDontAskAgain) {
                    Row {
                        Checkbox(
                            checked = checkedState.value,
                            onCheckedChange = { checkedState.value = it },
                        )
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = stringResource(id = R.string.dont_ask_again),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissClick()
            if (showDontAskAgain) {
                onDontAskAgain(checkedState.value)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isNameEmpty,
                onClick = {
                    onAcceptClick(currentValue.value)
                    if (showDontAskAgain) {
                        onDontAskAgain(checkedState.value)
                    }
                }
            ) {
                Text(stringResource(id = R.string.btn_save))
            }

        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissClick()
                    if (showDontAskAgain) {
                        onDontAskAgain(checkedState.value)
                    }
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
    RenameAlertDialog("Record-14", {}, {}, {}, true)
}

@Composable
fun UpdateNameAndDescriptionDialog(
    recordName: String,
    recordDescription: String,
    isWriteToFileSupported: Boolean,
    onAcceptClick: (name: String, description: String, writeToFile: Boolean) -> Unit,
    onDismissClick: () -> Unit,
    onDontAskAgain: (Boolean) -> Unit = {},
    showDontAskAgain: Boolean = false,
) {
    val nameValue = remember { mutableStateOf(recordName) }
    val descriptionValue = remember { mutableStateOf(recordDescription) }
    val writeToFile = remember { mutableStateOf(isWriteToFileSupported) }
    val dontAskAgainChecked = remember { mutableStateOf(false) }
    val effectiveWriteToFile = isWriteToFileSupported && writeToFile.value
    val isNameEmpty = nameValue.value.isBlank()

    LaunchedEffect(recordName) {
        if (nameValue.value != recordName) {
            nameValue.value = recordName
        }
    }
    LaunchedEffect(recordDescription) {
        if (descriptionValue.value != recordDescription) {
            descriptionValue.value = recordDescription
        }
    }

    AlertDialog(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    painter = painterResource(id = R.drawable.ic_pencil),
                    contentDescription = stringResource(id = R.string.update_name_and_description)
                )
                Text(
                    text = stringResource(id = R.string.update_name_and_description),
                    fontSize = 22.sp,
                )
            }
        },
        text = {
            val localFocusManager = LocalFocusManager.current
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = nameValue.value,
                    onValueChange = { nameValue.value = it },
                    label = { Text(text = stringResource(id = R.string.rename)) },
                    textStyle = TextStyle.Default.copy(fontSize = 20.sp),
                    isError = isNameEmpty,
                    supportingText = if (isNameEmpty) {
                        { Text(text = stringResource(id = R.string.msg_name_cannot_be_empty)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = descriptionValue.value,
                    onValueChange = {
                        if (it.length <= RECORD_DESCRIPTION_MAX_LENGTH) descriptionValue.value = it
                    },
                    label = { Text(text = stringResource(id = R.string.rec_description)) },
                    placeholder = { Text(text = stringResource(id = R.string.rec_description_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    supportingText = {
                        Text(
                            text = "${descriptionValue.value.length}/$RECORD_DESCRIPTION_MAX_LENGTH",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    },
                    keyboardActions = KeyboardActions {
                        localFocusManager.clearFocus()
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isWriteToFileSupported) {
                                Modifier.clickable { writeToFile.value = !writeToFile.value }
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = effectiveWriteToFile,
                        onCheckedChange = { writeToFile.value = it },
                        enabled = isWriteToFileSupported,
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = stringResource(id = R.string.write_description_into_file),
                        fontSize = 16.sp,
                        color = if (isWriteToFileSupported) {
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
                if (showDontAskAgain) {
                    Row {
                        Checkbox(
                            checked = dontAskAgainChecked.value,
                            onCheckedChange = { dontAskAgainChecked.value = it },
                        )
                        Text(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            text = stringResource(id = R.string.dont_ask_again),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissClick()
            if (showDontAskAgain) {
                onDontAskAgain(dontAskAgainChecked.value)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isNameEmpty,
                onClick = {
                    onAcceptClick(nameValue.value, descriptionValue.value, effectiveWriteToFile)
                    if (showDontAskAgain) {
                        onDontAskAgain(dontAskAgainChecked.value)
                    }
                }
            ) {
                Text(stringResource(id = R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissClick()
                    if (showDontAskAgain) {
                        onDontAskAgain(dontAskAgainChecked.value)
                    }
                }
            ) {
                Text(stringResource(id = R.string.btn_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun UpdateNameAndDescriptionDialogPreview() {
    UpdateNameAndDescriptionDialog(
        recordName = "Record-14",
        recordDescription = "Meeting notes",
        isWriteToFileSupported = true,
        onAcceptClick = { _, _, _ -> },
        onDismissClick = {},
        showDontAskAgain = true,
    )
}

/**
 * Lightweight dialog for adding or editing a record's description (note) directly
 * from the records list, without navigating to the info screen. An empty value is
 * allowed and clears the note.
 *
 * The "save to audio file" checkbox controls whether the description is also embedded
 * as a COMMENT tag in the audio file. When unchecked, it is saved to the database only.
 * [onAcceptClick] receives the entered text and the checkbox state.
 *
 * When [isWriteToFileSupported] is false (e.g. 3GP files, which can't store comment
 * metadata) the checkbox is forced off and disabled, and the saved flag is always false.
 */
@Composable
fun EditDescriptionDialog(
    initialDescription: String,
    onAcceptClick: (description: String, writeToFile: Boolean) -> Unit,
    onDismissClick: () -> Unit,
    initialWriteToFile: Boolean,
    isWriteToFileSupported: Boolean,
) {
    val currentValue = remember { mutableStateOf(initialDescription) }
    val writeToFile = remember { mutableStateOf(initialWriteToFile) }
    // 3GP and similar containers can't store a comment tag, so file-write is forced off.
    val effectiveWriteToFile = isWriteToFileSupported && writeToFile.value
    // If the description arrives after first composition (async state update),
    // populate the field as long as the user hasn't started typing yet.
    LaunchedEffect(initialDescription) {
        if (currentValue.value != initialDescription) {
            currentValue.value = initialDescription
        }
    }
    AlertDialog(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(0.dp, 0.dp, 16.dp, 0.dp),
                    painter = painterResource(id = R.drawable.ic_description),
                    contentDescription = stringResource(id = R.string.rec_description)
                )
                Text(text = stringResource(id = R.string.rec_description))
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = currentValue.value,
                    onValueChange = {
                        if (it.length <= RECORD_DESCRIPTION_MAX_LENGTH) currentValue.value = it
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.rec_description_hint))
                    },
                    minLines = 3,
                    maxLines = 9,
                    supportingText = {
                        Text(
                            text = "${currentValue.value.length}/$RECORD_DESCRIPTION_MAX_LENGTH",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isWriteToFileSupported) {
                                Modifier.clickable { writeToFile.value = !writeToFile.value }
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = effectiveWriteToFile,
                        onCheckedChange = { writeToFile.value = it },
                        enabled = isWriteToFileSupported,
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = stringResource(id = R.string.write_description_into_file),
                        fontSize = 16.sp,
                        color = if (isWriteToFileSupported) {
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        },
        onDismissRequest = { onDismissClick() },
        confirmButton = {
            TextButton(
                onClick = { onAcceptClick(currentValue.value, effectiveWriteToFile) }
            ) {
                Text(stringResource(id = R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissClick() }
            ) {
                Text(stringResource(id = R.string.btn_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EditDescriptionDialogPreview() {
    EditDescriptionDialog(
        "Meeting notes for Q3 planning.",
        { _, _ -> }, {}, true, true
    )
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
    dialogText: String,
    onAcceptClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    ConfirmationAlertDialog(
        onDismissRequest = { onDismissClick() },
        onConfirmation = { onAcceptClick() },
        dialogTitle = stringResource(id = R.string.warning),
        dialogText = dialogText,
        painter = painterResource(id = R.drawable.ic_delete_forever),
        positiveButton = stringResource(id = R.string.btn_yes),
        negativeButton = stringResource(id = R.string.btn_no)
    )
}

@Preview(showBackground = true)
@Composable
fun DeleteDialogPreview() {
    DeleteDialog(stringResource(id = R.string.move_record_to_trash,"Record-14"), {}, {})
}

@Composable
fun SaveAsDialog(
    dialogText: String,
    onAcceptClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    ConfirmationAlertDialog(
        onDismissRequest = { onDismissClick() },
        onConfirmation = { onAcceptClick() },
        dialogTitle = stringResource(id = R.string.save_as),
        dialogText = dialogText,
        painter = painterResource(id = R.drawable.ic_save_alt),
        positiveButton = stringResource(id = R.string.btn_yes),
        negativeButton = stringResource(id = R.string.btn_no)
    )
}

@Preview(showBackground = true)
@Composable
fun SaveAsDialogPreview() {
    SaveAsDialog(
        dialogText = stringResource(id = R.string.record_name_will_be_copied_into_downloads, "Record-14"),
        {}, {}
    )
}

data class DropDownMenuItem<T>(
    val id: T,
    val textResId: Int,
    val imageResId: Int
)

