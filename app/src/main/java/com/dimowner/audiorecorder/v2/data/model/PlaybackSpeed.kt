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
 * Playback rates offered next to the play controls. [value] is the multiplier passed to the player
 * and the number the button shows, see [formatValue]. The normal rate has no button: it is what
 * playback falls back to when no rate is selected.
 */
enum class PlaybackSpeed(val value: Float) {
    X0_5(value = 0.5f),
    X0_75(value = 0.75f),
    X1_5(value = 1.5f),
    X2(value = 2.0f);

    companion object {
        /**
         * The rates to offer where the play controls share a row with the prev/next buttons, as in
         * the records playback panel: only the extremes, so everything fits on a narrow screen.
         */
        val compact: List<PlaybackSpeed> = listOf(X0_5, X2)
    }
}

/** Rates shown before the play controls, i.e. to their left in an LTR layout. */
fun List<PlaybackSpeed>.slower(): List<PlaybackSpeed> = filter { it.value < NORMAL_PLAYBACK_SPEED }

/** Rates shown after the play controls, i.e. to their right in an LTR layout. */
fun List<PlaybackSpeed>.faster(): List<PlaybackSpeed> = filter { it.value > NORMAL_PLAYBACK_SPEED }

/**
 * The multiplier as it appears on the button, in the numbering system and with the decimal
 * separator of [locale] — Arabic renders it as `٠٫٥`, not `0.5`. Durations elsewhere in the
 * playback panel are formatted against the default locale too, so both use the same digits.
 * One fraction digit is always kept, which is what makes `2` read as `2.0`.
 */
fun PlaybackSpeed.formatValue(locale: Locale = Locale.getDefault()): String {
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }.format(value)
}

private const val SPEED_COMPARISON_TOLERANCE = 0.01f

fun Float.convertToPlaybackSpeed(): PlaybackSpeed? {
    return PlaybackSpeed.entries.firstOrNull {
        kotlin.math.abs(it.value - this) < SPEED_COMPARISON_TOLERANCE
    }
}
