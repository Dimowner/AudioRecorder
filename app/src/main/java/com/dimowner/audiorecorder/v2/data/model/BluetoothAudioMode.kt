package com.dimowner.audiorecorder.v2.data.model

import android.media.AudioManager

/** Names are persisted in preferences; keep them stable across releases. */
enum class BluetoothAudioMode(val value: Int) {
    IN_COMMUNICATION(AudioManager.MODE_IN_COMMUNICATION),
    NORMAL(AudioManager.MODE_NORMAL),
}
