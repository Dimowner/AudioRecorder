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

package com.dimowner.audiorecorder.v2.app.records.models

/**
 * Holds the currently selected filter criteria for the records list.
 *
 * Each dimension (format, sample rate, channel count, bitrate) is an independent set of
 * selected values. An empty set means the dimension is not filtered. When multiple values
 * are selected within a dimension they are combined with OR, while different dimensions are
 * combined with AND (e.g. format in (m4a, wav) AND sampleRate in (44100)).
 */
data class RecordsFilter(
    val formats: Set<String> = emptySet(),
    val sampleRates: Set<Int> = emptySet(),
    val channelCounts: Set<Int> = emptySet(),
    val bitrates: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            sampleRates.isEmpty() &&
            channelCounts.isEmpty() &&
            bitrates.isEmpty()

    /** Total number of selected values across all dimensions. */
    val activeCount: Int
        get() = formats.size + sampleRates.size + channelCounts.size + bitrates.size
}

/**
 * The set of distinct values available for filtering, derived from the records currently
 * stored (excluding records in the recycle bin). Only values that actually exist among the
 * user's records are offered, so the filter never shows empty results for a chosen value.
 */
data class RecordsFilterOptions(
    val formats: List<String> = emptyList(),
    val sampleRates: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
    val bitrates: List<Int> = emptyList(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            sampleRates.isEmpty() &&
            channelCounts.isEmpty() &&
            bitrates.isEmpty()
}
