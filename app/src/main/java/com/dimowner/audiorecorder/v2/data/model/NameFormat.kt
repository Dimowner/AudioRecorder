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

enum class NameFormat {
    Record, Timestamp, Date, DateUs, DateIso8601
}

fun String.convertToNameFormat(): NameFormat? {
    if (this == NameFormat.Record.toString()) return NameFormat.Record
    if (this == NameFormat.Timestamp.toString()) return NameFormat.Timestamp
    if (this == NameFormat.Date.toString()) return NameFormat.Date
    if (this == NameFormat.DateUs.toString()) return NameFormat.DateUs
    if (this == NameFormat.DateIso8601.toString()) return NameFormat.DateIso8601

    return null
}
