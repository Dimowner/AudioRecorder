package com.dimowner.audiorecorder.v2.data.model

/** Names are persisted in preferences; gain processing applies to signed PCM16 only. */
enum class PcmGainMode {
    OFF,
    DB_PLUS_6,
    DB_PLUS_12,
    DB_PLUS_18,
    AUTO_LEVEL,
}
