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

import android.os.Build
import android.os.Bundle
import androidx.navigation.NavType
import com.google.gson.Gson

class AssetParamType : NavType<RecordInfoState>(isNullableAllowed = false) {

    override fun get(bundle: Bundle, key: String): RecordInfoState? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, RecordInfoState::class.java)
        } else {
            bundle.getParcelable(key)
        }
    }

    override fun parseValue(value: String): RecordInfoState {
        return Gson().fromJson(value, RecordInfoState::class.java)
    }

    override fun put(bundle: Bundle, key: String, value: RecordInfoState) {
        bundle.putParcelable(key, value)
    }
}