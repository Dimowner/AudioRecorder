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

/**
 * Playback rates offered next to the play controls. [value] is the multiplier passed to the player,
 * [label] is what the button shows. The normal rate has no button: it is what playback falls back to
 * when no rate is selected.
 */
enum class PlaybackSpeed(val value: Float, val label: String) {
    X0_5(value = 0.5f, label = "0.5x"),
    X0_75(value = 0.75f, label = "0.75x"),
    X1_5(value = 1.5f, label = "1.5x"),
    X2(value = 2.0f, label = "2.0x");

    companion object {
        /**
         * The rates to offer where the play controls share a row with the prev/next buttons, as in
         * the records playback panel: only the extremes, so everything fits on a narrow screen.
         */
        val compact: List<PlaybackSpeed> = listOf(X0_5, X2)
    }
}

/** Rates shown to the left of the play controls. */
fun List<PlaybackSpeed>.slower(): List<PlaybackSpeed> = filter { it.value < NORMAL_PLAYBACK_SPEED }

/** Rates shown to the right of the play controls. */
fun List<PlaybackSpeed>.faster(): List<PlaybackSpeed> = filter { it.value > NORMAL_PLAYBACK_SPEED }

private const val SPEED_COMPARISON_TOLERANCE = 0.01f

fun Float.convertToPlaybackSpeed(): PlaybackSpeed? {
    return PlaybackSpeed.entries.firstOrNull {
        kotlin.math.abs(it.value - this) < SPEED_COMPARISON_TOLERANCE
    }
}
