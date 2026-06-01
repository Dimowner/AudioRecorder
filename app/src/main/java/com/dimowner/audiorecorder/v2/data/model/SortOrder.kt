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
package com.dimowner.audiorecorder.v2.data.model

/**
 * Represents the available sorting options for records list,
 * defining both the criteria (e.g., Date, Name, Duration) and the
 * direction (e.g., Ascending, Descending).
 */
enum class SortOrder {
    /** Sorts by date in ascending order (oldest to newest). */
    DateAsc,

    /** Sorts by date in descending order (newest to oldest). */
    DateDesc,

    /** Sorts by name in ascending order (alphabetical, A-Z). */
    NameAsc,

    /** Sorts by name in descending order (reverse alphabetical, Z-A). */
    NameDesc,

    /** Sorts by duration in ascending order (shortest to longest). */
    DurationShortest,

    /** Sorts by duration in descending order (longest to shortest). */
    DurationLongest
}

fun String.convertToSortOrder(): SortOrder? {
    return if (this == SortOrder.DateAsc.toString()) SortOrder.DateAsc
    else if (this == SortOrder.DateDesc.toString()) SortOrder.DateDesc
    else if (this == SortOrder.NameAsc.toString()) SortOrder.NameAsc
    else if (this == SortOrder.NameDesc.toString()) SortOrder.NameDesc
    else if (this == SortOrder.DurationShortest.toString()) SortOrder.DurationShortest
    else if (this == SortOrder.DurationLongest.toString()) SortOrder.DurationLongest
    else null
}
