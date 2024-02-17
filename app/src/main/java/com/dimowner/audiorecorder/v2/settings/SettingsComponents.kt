package com.dimowner.audiorecorder.v2.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        Text(
            modifier = Modifier
                .padding(0.dp, 12.dp, 0.dp, 12.dp)
                .wrapContentWidth()
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

    }
}

@Preview(showBackground = true)
@Composable
fun SettingsItemPreview() {
    SettingsItem("Label", R.drawable.ic_color_lens, {})
}

@Composable
fun SettingsItemCheckBox(
    label: String,
    iconRes: Int,
    onCheckedChange: ((Boolean) -> Unit),
) {
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
            checked = true,
            onCheckedChange = { onCheckedChange(it) },
            enabled = true,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsItemCheckBoxPreview() {
    SettingsItemCheckBox("Label", R.drawable.ic_color_lens, {})
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
    text: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(8.dp),
                textAlign = TextAlign.Start,
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light
            )
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
    ResetRecordingSettingsPanel("ResetRecordingSettingsPanel", {})
}

@Composable
fun SettingSelector(
    name: String,
    chips: List<ChipItem>,
    onSelect: (ChipItem) -> Unit,
) {
//    val screenWidth = LocalConfiguration.current.screenWidthDp.dp.value
//    var grid = calculateChipsPositions(values, screenWidth)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(4.dp),
            textAlign = TextAlign.Start,
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light
        )
        ChipsPanel(
            modifier = Modifier.wrapContentSize(),
            chips = chips,
            onSelect = onSelect
        )
//        var k = 0
//        grid.forEach { item ->
//            Timber.v("MY_TEST createRows")
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .wrapContentHeight(),
//                verticalAlignment = Alignment.CenterVertically,
//            ) {
//                for (j in 0..< item.value) {
//                    Timber.v("MY_TEST create Chips j = " + j)
//                    ChipComponent(
//                        modifier = Modifier,
//                        values[k],
//                        onSelect
//                    )
//                    k++
//                }
//            }
//        }
    }
}

@Composable
fun ChipComponent(
    modifier: Modifier = Modifier,
    item: ChipItem,
    onSelect: (ChipItem) -> Unit,
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
                        .size(29.dp)
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
    SettingSelector("SettingsSelector", emptyList(), {})
}

@Composable
fun ChipsPanel(
    modifier: Modifier = Modifier,
    chips: List<ChipItem>,
    onSelect: (ChipItem) -> Unit,
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

fun calculatePositionsDefault(
    temp: List<Placeable>,
    viewWidth: Int,
    onPlace: ((Placeable, x: Int, y: Int) -> Unit)? = null
): Int {
    val rowHeight = temp.first().measuredHeight
    var rowCount = 0
    var posY = 0
    var posX = 0
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
    return posY
}
