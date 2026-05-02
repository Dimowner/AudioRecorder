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

package com.dimowner.audiorecorder.v2.app.settings

import android.os.Parcelable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.InfoAlertDialog
import com.dimowner.audiorecorder.v2.app.components.DISABLED_ALPHA
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsItem(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(24.dp, 16.dp, 16.dp, 16.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            painter = painterResource(id = iconRes),
            contentDescription = label,
        )
        SettingsItemText(text = label)
    }
}

@Composable
fun SettingsItemText(text: String) {
    Text(
        modifier = Modifier
            .padding(0.dp, 12.dp, 0.dp, 12.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        text = text,
        fontSize = 20.sp,
        fontFamily = FontFamily(
            Font(
                DeviceFontFamilyName("sans-serif"),
                weight = FontWeight.Light
            )
        ),
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsItemPreview() {
    SettingsItem("Label", R.drawable.ic_color_lens, {})
}

@Composable
fun SettingsItemCheckBox(
    checked: Boolean,
    label: String,
    iconRes: Int,
    onCheckedChange: ((Boolean) -> Unit),
    enabled: Boolean = true,
) {
    val checkState = remember { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(24.dp, 16.dp, 16.dp, 16.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            painter = painterResource(id = iconRes),
            contentDescription = label
        )
        Text(
            modifier = Modifier
                .padding(0.dp, 12.dp, 0.dp, 12.dp)
                .weight(1f)
                .wrapContentHeight(),
            text = label,
            fontSize = 20.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Light
                )
            ),
        )
        Switch(
            checked = checkState.value,
            onCheckedChange = {
                checkState.value = it
                onCheckedChange(it)
            },
            enabled = enabled,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsItemCheckBoxPreview() {
    SettingsItemCheckBox(true,"Label", R.drawable.ic_color_lens, {})
}

@Composable
fun AppInfoView(appName: String, version: String, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            textAlign = TextAlign.Center,
            text = appName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Medium
                )
            ),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            textAlign = TextAlign.Center,
            text = version,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppInfoViewPreview() {
    AppInfoView("App Name", "App Version")
}

@Composable
fun InfoTextView(value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 0.dp, 16.dp, 0.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InfoTextViewPreview() {
    InfoTextView("InfoTextView")
}

@Composable
fun ResetRecordingSettingsPanel(
    sizePerMin: String,
    recordingSettingsText: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(4.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(8.dp),
            ) {
                Text(
                    textAlign = TextAlign.Start,
                    text = sizePerMin,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    textAlign = TextAlign.Start,
                    text = recordingSettingsText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                )
            }
            Button(
                modifier = Modifier
                    .padding(8.dp)
                    .wrapContentSize(),
                enabled = enabled,
                onClick = { onClick() }
            ) {
                Text(
                    text = stringResource(id = R.string.btn_reset),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun ResetRecordingSettingsPanelPreview() {
    ResetRecordingSettingsPanel("ResetRecordingSettingsPanel", "m4a, mono", {})
}

@Composable
fun <T: Parcelable> SettingSelector(
    name: String,
    chips: List<ChipItem<T>>,
    onSelect: (ChipItem<T>) -> Unit,
    onClickInfo: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .wrapContentHeight()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
    ) {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp),
                textAlign = TextAlign.Start,
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light
            )
            IconButton(
                onClick = onClickInfo,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.CenterVertically),
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = name,
                )
            }
        }

        ChipsPanel(
            modifier = Modifier.wrapContentSize(),
            chips = chips,
            onSelect = onSelect,
            enabled = enabled
        )
    }
}

@Composable
fun <T: Parcelable> ChipComponent(
    modifier: Modifier = Modifier,
    item: ChipItem<T>,
    onSelect: (ChipItem<T>) -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier
            .wrapContentSize()
            .padding(2.dp, 0.dp),
        shape = RoundedCornerShape(18.dp),
        border = if (item.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else { null },
        onClick = { if (enabled) onSelect(item) },
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .wrapContentSize()
                .padding(if (item.isSelected) 12.dp else 25.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = item.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(26.dp)
                        .padding(0.dp, 3.dp, 3.dp, 3.dp)
                        .align(Alignment.CenterVertically)
                )
            }
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(2.dp),
                textAlign = TextAlign.Start,
                text = item.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }

}

@Preview(showBackground = true)
@Composable
fun ChipComponentPreview() {
    ChipComponent(
        Modifier.wrapContentSize(),
        item = ChipItem(id = 0, value = SampleRate.SR8000, name = "8000", false),
        onSelect = {},
    )
}

//@Composable
//fun calculateChipsPositions(chips: List<ChipItem>, screenWidth: Float): Map<Int, Int> {
//    val map = mutableMapOf<Int, Int>()
//    var row = 0
//    var col = 0
//    var cumulativeWidth = 0f
//    val textStyle = TextStyle(
//        fontSize = 17.sp,
//        fontWeight = FontWeight.Normal,
//        textAlign = TextAlign.Start
//    )
//    chips.forEach { chip ->
//        val chipWidthExtra = 58 //Dp
//        val chipWidth = measureTextWidth(chip.name, textStyle).value + chipWidthExtra.dp.value
//        cumulativeWidth += chipWidth
//        if (cumulativeWidth > screenWidth) {
//            map[row] = col
//            row++
//            col = 1
//            cumulativeWidth = chipWidth
//        } else {
//            col++
//            map[row] = col
//        }
//    }
//    return map
//}

@Composable
fun measureTextWidth(text: String, style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    val widthInPixels = textMeasurer.measure(text, style).size.width
    return with(LocalDensity.current) { widthInPixels.toDp() }
}

@Preview(showBackground = true)
@Composable
fun SettingSelectorPreview() {
    SettingSelector("SettingsSelector", chips = getTestChips(), {}, {})
}

@Composable
fun <T: Parcelable> ChipsPanel(
    modifier: Modifier = Modifier,
    chips: List<ChipItem<T>>,
    onSelect: (ChipItem<T>) -> Unit,
    enabled: Boolean = true,
) {
    Layout(
        modifier = modifier,
        content = { chips.map {
            ChipComponent(modifier = Modifier.wrapContentSize(), item = it, onSelect = onSelect, enabled = enabled) }
        }
    ) { measurables, constraints ->
        // List of measured children
        val placeables = measurables.map { measurable ->
            // Measure each children
            measurable.measure(constraints)
        }
        val totalHeight = calculatePositionsDefault(placeables, constraints.maxWidth)

        // Set the size of the layout as big as it can
        layout(constraints.maxWidth, totalHeight) {
            calculatePositionsDefault(
                temp = placeables,
                constraints.maxWidth,
            ) { placeable, x, y -> placeable.place(x, y) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChipsPanelPreview() {
    ChipsPanel(Modifier.wrapContentSize(), getTestChips(), {})
}

fun calculatePositionsDefault(
    temp: List<Placeable>,
    viewWidth: Int,
    onPlace: ((Placeable, x: Int, y: Int) -> Unit)? = null
): Int {
    var posY = 0
    if (temp.isNotEmpty()) {
        var posX = 0
        val rowHeight = temp.first().measuredHeight
        var rowCount = 0
        rowCount++
        if (temp.isNotEmpty()) {
            var availableWidth = viewWidth
            for (i in temp.indices) {
                if (availableWidth < temp[i].measuredWidth) {
                    rowCount++
                    posY += rowHeight
                    availableWidth = viewWidth
                    posX = 0
                }
                onPlace?.invoke(temp[i], posX, posY)
                val width: Int = (temp[i].measuredWidth)
                posX += width
                availableWidth -= width
            }
            posY += rowHeight
        }
    }
    return posY
}

@Composable
fun DropDownSetting(
    items: List<NameFormatItem>,
    selectedItem: NameFormatItem?,
    onSelect: (NameFormatItem) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.TopStart)
    ) {
        // The DropdownMenu composable
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onSelect(item)
                        expanded.value = false
                    }, text = {
                        SettingsItemText(text = item.nameText)
                    }
                )
            }
        }
        val text = selectedItem?.nameText ?: stringResource(id = R.string.empty)
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(24.dp, 16.dp, 16.dp, 16.dp)
                    .wrapContentSize(),
                painter = painterResource(id = R.drawable.ic_title),
                contentDescription = text,
            )
            Column(
                modifier = Modifier
                    .padding(0.dp, 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(id = R.string.record_name_format),
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Bold
                        )
                    ),
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = text,
                    fontSize = 20.sp,
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Light
                        )
                    ),
                )
            }
            Icon(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(0.dp, 0.dp, 12.dp, 0.dp),
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = text,
            )
        }
    }
}

@Composable
fun SettingsInfoDialog(openDialog: MutableState<Boolean>, message: String) {
    if (openDialog.value) {
        InfoAlertDialog(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = {
                openDialog.value = false
            },
            dialogTitle = stringResource(id = R.string.info),
            dialogText = buildAnnotatedString {
                append(message)
            },
            icon = Icons.Default.Info,
            dismissButton = stringResource(id = R.string.btn_ok)
        )
    }
}

@Composable
fun SettingsInfoDialog(
    openDialog: MutableState<Boolean>,
    message: AnnotatedString,
    title: String = stringResource(id = R.string.info)
) {
    if (openDialog.value) {
        InfoAlertDialog(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = {
                openDialog.value = false
            },
            dialogTitle = title,
            dialogText = message,
            icon = Icons.Default.Info,
            dismissButton = stringResource(id = R.string.btn_got_it)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsInfoDialogPreview() {
    SettingsInfoDialog(
        remember {mutableStateOf(true) },
        buildAnnotatedString {
            append("Information massage")
        }
    )
}

@Composable
fun SettingsWarningDialog(openDialog: MutableState<Boolean>, message: String) {
    if (openDialog.value) {
        InfoAlertDialog(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = {
                openDialog.value = false
            },
            dialogTitle = stringResource(id = R.string.warning),
            dialogText = buildAnnotatedString {
                append(message)
            },
            icon = Icons.Default.Warning,
            dismissButton = stringResource(id = R.string.btn_ok)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsWarningDialogPreview() {
    SettingsWarningDialog(remember {mutableStateOf(true) }, "Warning message")
}

@Composable
fun AuthorNameSettingItem(
    currentAuthorName: String,
    onClick: () -> Unit,
    onClickInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(24.dp, 16.dp, 16.dp, 16.dp)
                .wrapContentWidth()
                .wrapContentHeight(),
            painter = painterResource(id = R.drawable.ic_artist),
            contentDescription = stringResource(id = R.string.records_author_name),
        )
        Column(
            modifier = Modifier
                .padding(0.dp, 12.dp)
                .weight(1f)
        ) {
            Text(
                text = stringResource(id = R.string.records_author_name),
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Bold
                    )
                ),
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = currentAuthorName,
                fontSize = 20.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        IconButton(
            onClick = onClickInfo,
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_info),
                contentDescription = stringResource(id = R.string.records_author_name),
            )
        }
    }
}

@Composable
fun AuthorNameEditDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.records_author_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(id = R.string.records_author_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            onConfirm(text)
                        },
                    ) {
                        Text(stringResource(id = R.string.btn_save))
                    }
                }
            }
        }
    }
}

private fun getTestChips(): List<ChipItem<SampleRate>> {
    return listOf(
        ChipItem(id = 0, value = SampleRate.SR8000, name = "8000", false),
        ChipItem(id = 1, value = SampleRate.SR16000, name = "16000", false),
        ChipItem(id = 2, value = SampleRate.SR22050, name = "22050", true),
        ChipItem(id = 3, value = SampleRate.SR32000, name = "32000", false),
    )
}