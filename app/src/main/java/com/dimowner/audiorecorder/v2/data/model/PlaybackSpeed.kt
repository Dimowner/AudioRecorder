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

/**
 * Playback rates offered on the home screen. [value] is the multiplier passed to the player,
 * [label] is what the button shows.
 */
enum class PlaybackSpeed(val value: Float, val label: String) {
    X0_5(value = 0.5f, label = "0.5x"),
    X0_75(value = 0.75f, label = "0.75x"),
    X1(value = 1.0f, label = "1x"),
    X1_5(value = 1.5f, label = "1.5x"),
    X2(value = 2.0f, label = "2x"),
}

private const val SPEED_COMPARISON_TOLERANCE = 0.01f

fun Float.convertToPlaybackSpeed(): PlaybackSpeed? {
    return PlaybackSpeed.entries.firstOrNull {
        kotlin.math.abs(it.value - this) < SPEED_COMPARISON_TOLERANCE
    }
}
