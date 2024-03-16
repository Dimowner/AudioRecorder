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