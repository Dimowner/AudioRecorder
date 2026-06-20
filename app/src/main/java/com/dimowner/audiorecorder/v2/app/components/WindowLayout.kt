/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/** Max width for single-column content (forms, settings, detail screens) on large screens. */
val MAX_CONTENT_WIDTH_NARROW = 600.dp

/** Max width for list content on large screens. */
val MAX_CONTENT_WIDTH_WIDE = 840.dp

/**
 * Describes the available window space so screens can pick a fitting arrangement.
 * Based on Material window size classes, see
 * https://developer.android.com/develop/ui/compose/layouts/adaptive/window-size-classes
 */
data class WindowLayout(
    /** Window height < 480dp: typically a phone in landscape, vertical stacks don't fit. */
    val isCompactHeight: Boolean,
    /** Window width >= 600dp: tablet portrait, unfolded foldable, split screen on a tablet. */
    val isAtLeastMediumWidth: Boolean,
    /** Window width >= 840dp: tablet landscape, desktop window. */
    val isExpandedWidth: Boolean,
    /** Window is wider than tall. */
    val isLandscape: Boolean,
) {
    /**
     * Wide-and-short or very wide windows where main screen content should be
     * arranged in two horizontal panes instead of one vertical stack.
     */
    val useHorizontalLayout: Boolean
        get() = isCompactHeight || (isExpandedWidth && isLandscape)
}

@Composable
fun rememberWindowLayout(): WindowLayout {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val containerSize = LocalWindowInfo.current.containerSize
    return WindowLayout(
        isCompactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
        ),
        isAtLeastMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ),
        isExpandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        ),
        isLandscape = containerSize.width > containerSize.height,
    )
}
