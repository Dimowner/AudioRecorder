package com.dimowner.audiorecorder.v2.app.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.RecordsDropDownMenu
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId
import timber.log.Timber

@Composable
fun RecordsTopBar(
    title: String,
    subTitle: String,
    onBackPressed: () -> Unit,
    onSortItemClick: (SortDropDownMenuItemId) -> Unit,
    onBookmarksClick: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
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

        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = subTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            RecordsDropDownMenu(
                items = remember { getSortDroDownMenuItems() },
                onItemClick = { itemId ->
                    onSortItemClick(itemId)
                    Timber.v("On Drop Down Menu item click id = $itemId")
                },
                expanded = expanded
            )
            FilledIconButton(
                onClick = {
                    expanded.value = true
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sort),
                    contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                )
            }
        }
        FilledIconButton(
            onClick = onBookmarksClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bookmark_bordered),
                contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TitleBarPreview() {
    RecordsTopBar("Title bar", "By date", {}, {}, {})
}

@Composable
fun RecordListItem(
    name: String,
    details: String,
    duration: String,
    onClickItem: () -> Unit,
    onClickBookmark: () -> Unit,
    onClickMenu: (RecordDropDownMenuItemId) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clickable { onClickItem() }
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                modifier = Modifier
                    .padding(16.dp, 12.dp, 12.dp, 2.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                modifier = Modifier
                    .padding(16.dp, 2.dp, 12.dp, 12.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        Spacer(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
        )
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(0.dp, 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            FilledIconButton(
                onClick = onClickBookmark,
                modifier = Modifier
                    .width(36.dp)
                    .height(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bookmark_bordered_small),
                    contentDescription = stringResource(id = R.string.bookmarks),
                    modifier = Modifier
                        .padding(6.dp)
                        .size(36.dp)
                        .fillMaxHeight()
                )
            }
            Text(
                modifier = Modifier
                    .padding(0.dp, 2.dp, 8.dp, 12.dp)
                    .wrapContentWidth()
                    .wrapContentHeight(),
                text = duration,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        Box(
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            // The DropdownMenu composable
            RecordsDropDownMenu(
                items = remember { getRecordsDroDownMenuItems() },
                onItemClick = { itemId ->
                    Timber.v("On Drop Down Menu item click id = $itemId")
                    onClickMenu(itemId)
                },
                expanded = expanded
            )
            FilledIconButton(
                onClick = {
                    expanded.value = !expanded.value
                },
                modifier = Modifier
                    .width(32.dp)
                    .height(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                    modifier = Modifier
                        .width(36.dp)
                        .padding(4.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordListItemPreview() {
    RecordListItem("Label", "Value", "Duration", {}, {}, {})
}
