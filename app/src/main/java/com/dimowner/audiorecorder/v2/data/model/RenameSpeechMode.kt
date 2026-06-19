package com.dimowner.audiorecorder.v2.data.model

enum class RenameSpeechMode(val persistedValue: Int) {
    Append(0),
    Replace(1),
    ;

    companion object {
        fun fromPersistedValue(value: Int): RenameSpeechMode {
            return entries.firstOrNull { it.persistedValue == value } ?: Append
        }
    }
}
