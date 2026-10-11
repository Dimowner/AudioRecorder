package com.dimowner.audiorecorder.v2.data.model

/**
 * Requested input effects, not a guarantee of device support. Null leaves that effect untouched;
 * false explicitly disables it. Names are persisted in preferences and must remain stable.
 */
enum class InputPreprocessingPolicy(
    val requestedNs: Boolean?,
    val requestedAec: Boolean?,
    val requestedAgc: Boolean?,
) {
    SYSTEM_DEFAULT(null, null, null),
    DISABLE_NS(false, null, null),
    DISABLE_NS_AEC(false, false, null),
    DISABLE_NS_AEC_AGC(false, false, false),
    AGC_ONLY(false, false, true),
}
