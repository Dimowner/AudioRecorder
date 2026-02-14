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

package com.dimowner.audiorecorder.v2.app.records

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
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
    bookmarksSelected: Boolean,
    onBackPressed: () -> Unit,
    onSortItemClick: (SortDropDownMenuItemId) -> Unit,
    onBookmarksClick: (Boolean) -> Unit,
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
        IconButton(
            onClick = onBackPressed,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.navigate_back),
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
            IconButton(
                onClick = {
                    expanded.value = true
                },
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
        IconButton(
            onClick = {
                onBookmarksClick(!bookmarksSelected)
            },
        ) {
            Icon(
                painter = if (bookmarksSelected) {
                    painterResource(id = R.drawable.ic_bookmark)
                } else {
                    painterResource(id = R.drawable.ic_bookmark_bordered)
                },
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
fun RecordsTopBarPreview() {
    RecordsTopBar("Title bar", "By date", false, {}, {}, {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableRecordsTopBar(
    title: String,
    subTitle: String,
    bookmarksSelected: Boolean,
    onBackPressed: () -> Unit,
    onSortItemClick: (SortDropDownMenuItemId) -> Unit,
    onBookmarksClick: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val expanded = remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight(),
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontFamily = FontFamily(
                        Font(
                            DeviceFontFamilyName("sans-serif"),
                            weight = FontWeight.Light
                        )
                    ),
                    fontWeight = FontWeight.Light,
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
                    fontWeight = FontWeight.Light,
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBackPressed,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            Box {
                RecordsDropDownMenu(
                    items = remember { getSortDroDownMenuItems() },
                    onItemClick = { itemId ->
                        onSortItemClick(itemId)
                        Timber.v("On Drop Down Menu item click id = $itemId")
                    },
                    expanded = expanded
                )
                IconButton(
                    onClick = {
                        expanded.value = true
                    },
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
            IconButton(
                onClick = {
                    onBookmarksClick(!bookmarksSelected)
                },
            ) {
                Icon(
                    painter = if (bookmarksSelected) {
                        painterResource(id = R.drawable.ic_bookmark)
                    } else {
                        painterResource(id = R.drawable.ic_bookmark_bordered)
                    },
                    contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                )
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
fun ScrollableRecordsTopBarPreview() {
    ScrollableRecordsTopBar(
        "Title bar",
        "By date",
        false,
        {},
        {},
        {},
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedItemsCount: Int,
    onCancelClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selected, selectedItemsCount),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
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
                onClick = onCancelClick,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.btn_cancel),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onShareClick,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = stringResource(id = R.string.share),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                )
            }
            IconButton(
                onClick = onDownloadClick,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_save_alt),
                    contentDescription = stringResource(id = R.string.save_as),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = stringResource(id = R.string.delete),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MultiSelectTopBarPreview() {
    MultiSelectTopBar(3, {}, {}, {}, {})
}


@Composable
fun RecordListItemView(
    name: String,
    details: String,
    duration: String,
    isBookmarked: Boolean,
    isSelected: Boolean,
    isShowMenuButton: Boolean,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    onClickBookmark: (Boolean) -> Unit,
    onClickMenu: (RecordDropDownMenuItemId) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .background(color = if (isSelected) colorResource(R.color.selected_item_color) else Color.Transparent)
            .combinedClickable(
                onClick = {
                    onClickItem()
                },
                onLongClick = {
                    onLongClickItem()
                }
            )
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                modifier = Modifier
                    .padding(16.dp, 10.dp, 12.dp, 2.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                modifier = Modifier
                    .padding(16.dp, 2.dp, 12.dp, 10.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(0.dp, 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            IconButton(
                onClick = { onClickBookmark(!isBookmarked) },
                modifier = Modifier
                    .width(36.dp)
                    .height(32.dp)
            ) {
                Icon(
                    painter = if (isBookmarked) {
                        painterResource(id = R.drawable.ic_bookmark_small)
                    } else {
                        painterResource(id = R.drawable.ic_bookmark_bordered_small)
                    },
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
                fontSize = 14.sp,
                fontFamily = FontFamily(
                    Font(
                        DeviceFontFamilyName("sans-serif"),
                        weight = FontWeight.Light
                    )
                ),
            )
        }
        if (isShowMenuButton) {
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
                IconButton(
                    onClick = { expanded.value = !expanded.value },
                    modifier = Modifier
                        .width(36.dp)
                        .height(60.dp)
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
        } else {
            Spacer(modifier = Modifier.width(36.dp).height(60.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordListItemPreview() {
    RecordListItemView("Label", "Value", "Duration", true, true, true,  {}, {}, {}, {})
}

@Composable
fun MultiSelectMenu(
    selectedItemsCount: Int,
    onCancelClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(
            onClick = onCancelClick,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.btn_cancel),
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = stringResource(R.string.selected, selectedItemsCount),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontFamily = FontFamily(
                Font(
                    DeviceFontFamilyName("sans-serif"),
                    weight = FontWeight.Light
                )
            ),
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onShareClick,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = stringResource(id = R.string.share),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
            )
        }
        IconButton(
            onClick = onDownloadClick,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_save_alt),
                contentDescription = stringResource(id = R.string.save_as),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
            )
        }
        IconButton(
            onClick = onDeleteClick,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(id = R.string.delete),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MultiSelectMenuPreview() {
    MultiSelectMenu(3, {},{}, {}, {})
}
