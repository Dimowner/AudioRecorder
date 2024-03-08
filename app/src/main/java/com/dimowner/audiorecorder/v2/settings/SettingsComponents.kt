package com.dimowner.audiorecorder.v2.settings

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.dimowner.audiorecorder.v2.InfoAlertDialog
import com.dimowner.audiorecorder.v2.data.model.SampleRate

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
fun AppInfoView(appName: String, version: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(4.dp),
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
    onClickInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .wrapContentHeight()
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
            FilledIconButton(
                onClick = onClickInfo,
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
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
            onSelect = onSelect
        )
    }
}

@Composable
fun <T: Parcelable> ChipComponent(
    modifier: Modifier = Modifier,
    item: ChipItem<T>,
    onSelect: (ChipItem<T>) -> Unit,
) {
    Card(
        modifier = modifier
            .wrapContentSize()
            .padding(2.dp, 0.dp),
        shape = RoundedCornerShape(18.dp),
        border = if (item.isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else { null },
        onClick = { onSelect(item) }
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
        ChipItem(id = 0, value = SampleRate.SR8000, name = "8000", false)
    ) {}
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
) {
    Layout(
        modifier = modifier,
        content = { chips.map {
            ChipComponent(modifier = Modifier.wrapContentSize(), item = it, onSelect = onSelect) }
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
    ChipsPanel(Modifier.wrapContentSize(), getTestChips()) {}
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
            dialogText = message,
            icon = Icons.Default.Info,
            dismissButton = stringResource(id = R.string.btn_ok)
        )
    }
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
            dialogText = message,
            icon = Icons.Default.Warning,
            dismissButton = stringResource(id = R.string.btn_ok)
        )
    }
}

private fun getTestChips(): List<ChipItem<SampleRate>> {
    return listOf(
        ChipItem(id = 0, value = SampleRate.SR8000, name = "8000", false),
        ChipItem(id = 1, value = SampleRate.SR16000, name = "16000", false),
        ChipItem(id = 2, value = SampleRate.SR22500, name = "22500", true),
        ChipItem(id = 3, value = SampleRate.SR32000, name = "32000", false),
    )
}