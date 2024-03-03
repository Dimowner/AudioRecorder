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
    if (this.equals(NameFormat.Record.toString(), true)) return NameFormat.Record
    if (this.equals(NameFormat.Timestamp.toString(), true)) return NameFormat.Timestamp
    if (this.equals(NameFormat.Date.toString(), true)) return NameFormat.Date
    if (this.equals(NameFormat.DateUs.toString(), true)) return NameFormat.DateUs
    if (this.equals(NameFormat.DateIso8601.toString(), true)) return NameFormat.DateIso8601

    return null
}
