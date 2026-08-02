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
package com.dimowner.audiorecorder.v2.data.model

import com.dimowner.audiorecorder.audio.player.NORMAL_PLAYBACK_SPEED
import java.text.NumberFormat
import java.util.Locale

/**
 * Playback rates offered in the speed menu next to the play controls. [value] is the multiplier
 * passed to the player and the number the menu shows, see [formatValue]. The entries are declared
 * in ascending order, which is the order they are listed in.
 */
enum class PlaybackSpeed(val value: Float) {
    X0_5(value = 0.5f),
    X0_75(value = 0.75f),
    X1(value = NORMAL_PLAYBACK_SPEED),
    X1_25(value = 1.25f),
    X1_5(value = 1.5f),
    X1_75(value = 1.75f),
    X2(value = 2.0f);

    companion object {
        /** The rate playback runs at unless another one is picked. */
        val NORMAL = X1
    }
}

/**
 * The multiplier as it appears in the menu, in the numbering system and with the decimal
 * separator of [locale] — Arabic renders it as `٠٫٥`, not `0.5`. Durations elsewhere in the
 * playback panel are formatted against the default locale too, so both use the same digits.
 * Trailing zeros are dropped, so the whole rates read as `1` and `2` rather than `1.0` and `2.0`.
 */
fun PlaybackSpeed.formatValue(locale: Locale = Locale.getDefault()): String {
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)
}

private const val SPEED_COMPARISON_TOLERANCE = 0.01f

fun Float.convertToPlaybackSpeed(): PlaybackSpeed? {
    return PlaybackSpeed.entries.firstOrNull {
        kotlin.math.abs(it.value - this) < SPEED_COMPARISON_TOLERANCE
    }
}
