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

package com.dimowner.audiorecorder.v2.app.info

import com.dimowner.audiorecorder.v2.data.model.Record

fun Record.toRecordInfoState(): RecordInfoState {
    return RecordInfoState(
        name = this.name,
        format = this.format,
        duration = this.durationMills,
        size = this.size,
        location = this.path,
        created = this.created,
        sampleRate = this.sampleRate,
        channelCount = this.channelCount,
        bitrate = this.bitrate,
    )
}
