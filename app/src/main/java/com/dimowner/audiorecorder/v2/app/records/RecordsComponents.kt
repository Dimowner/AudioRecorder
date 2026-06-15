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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.app.RecordsDropDownMenu
import com.dimowner.audiorecorder.v2.app.records.models.RecordDropDownMenuItemId
import com.dimowner.audiorecorder.v2.app.records.models.SortDropDownMenuItemId

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
    filterActiveCount: Int,
    onBackPressed: () -> Unit,
    onFilterClick: () -> Unit,
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
            BadgedBox(
                badge = {
                    if (filterActiveCount > 0) {
                        Badge { Text(text = filterActiveCount.toString()) }
                    }
                }
            ) {
                IconButton(
                    onClick = onFilterClick,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_filter),
                        contentDescription = stringResource(id = R.string.filter),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(6.dp)
                    )
                }
            }
            Box {
                RecordsDropDownMenu(
                    items = remember { getSortDroDownMenuItems() },
                    onItemClick = { itemId ->
                        onSortItemClick(itemId)
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
        filterActiveCount = 2,
        onBackPressed = {},
        onFilterClick = {},
        onSortItemClick = {},
        onBookmarksClick = {},
        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
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
                text = stringResource(R.string.items_selected, selectedItemsCount),
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
                    tint = MaterialTheme.colorScheme.error,
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
    description: String,
    isBookmarked: Boolean,
    isSelected: Boolean,
    isShowMenuButton: Boolean,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    onClickBookmark: (Boolean) -> Unit,
    onClickMenu: (RecordDropDownMenuItemId) -> Unit,
    onClickDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = { onClickItem() },
                    onLongClick = { onLongClickItem() }
                )
                .fillMaxWidth()
                .padding(start = 16.dp, end = 0.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Name + duration/details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = duration,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = details,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontFamily = FontFamily(
                            Font(
                                DeviceFontFamilyName("sans-serif"),
                                weight = FontWeight.Light,
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                    )
                }
                // Optional description/note line. Tapping it opens the edit dialog.
                // When empty, an "Add description" placeholder is shown instead.
                Spacer(modifier = Modifier.height(2.dp))
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClickDescription)
                            .padding(vertical = 2.dp),
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.add_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(onClick = onClickDescription)
                            .padding(vertical = 2.dp),
                    )
                }
            }

            // Bookmark button
            IconButton(
                onClick = { onClickBookmark(!isBookmarked) },
                modifier = Modifier.size(width = 36.dp, height = 42.dp),
            ) {
                Icon(
                    painter = if (isBookmarked) {
                        painterResource(id = R.drawable.ic_bookmark_small)
                    } else {
                        painterResource(id = R.drawable.ic_bookmark_bordered_small)
                    },
                    contentDescription = stringResource(id = R.string.bookmarks),
                    tint = if (isBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            // More menu button
            if (isShowMenuButton) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    RecordsDropDownMenu(
                        items = remember { getRecordsDroDownMenuItems() },
                        onItemClick = { itemId -> onClickMenu(itemId) },
                        expanded = expanded,
                    )
                    IconButton(
                        onClick = { expanded.value = !expanded.value },
                        modifier = Modifier.size(width = 36.dp, height = 42.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(id = androidx.compose.ui.R.string.dropdown_menu),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecordListItemPreview() {
    RecordListItemView(
        name = "Recording_2024-01-15",
        details = "1.5 MB · mp4 · 192 kbps · 48 kHz",
        duration = "3:15",
        description = "Meeting notes: discuss Q3 roadmap and budget planning for the next sprint.",
        isBookmarked = true,
        isSelected = false,
        isShowMenuButton = true,
        onClickItem = {},
        onLongClickItem = {},
        onClickBookmark = {},
        onClickMenu = {},
        onClickDescription = {},
    )
}

@Preview(showBackground = true)
@Composable
fun RecordListItemSelectedPreview() {
    RecordListItemView(
        name = "Recording_2024-01-15",
        details = "4.5 MB · mp3 · 128 kbps · 32 kHz",
        duration = "8:15",
        description = "",
        isBookmarked = false,
        isSelected = true,
        isShowMenuButton = false,
        onClickItem = {},
        onLongClickItem = {},
        onClickBookmark = {},
        onClickMenu = {},
        onClickDescription = {},
    )
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
            text = stringResource(R.string.items_selected, selectedItemsCount),
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
                tint = MaterialTheme.colorScheme.error,
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
