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
    return if (this.equals(NameFormat.Record.toString(), true)) NameFormat.Record
    else if (this.equals(NameFormat.Timestamp.toString(), true)) NameFormat.Timestamp
    else if (this.equals(NameFormat.Date.toString(), true)) NameFormat.Date
    else if (this.equals(NameFormat.DateUs.toString(), true)) NameFormat.DateUs
    else if (this.equals(NameFormat.DateIso8601.toString(), true)) NameFormat.DateIso8601
    else null
}
