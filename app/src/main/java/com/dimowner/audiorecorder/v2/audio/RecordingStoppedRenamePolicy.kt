package com.dimowner.audiorecorder.v2.audio

internal data class RecordingStoppedRenamePolicy(
    val showInAppRenameDialog: Boolean,
    val showFloatingOverlayRenameDialog: Boolean,
)

internal fun recordingStoppedRenamePolicy(
    askToRenameAfterRecordingStopped: Boolean,
    recordId: Long,
    stoppedFromFloatingOverlay: Boolean,
): RecordingStoppedRenamePolicy {
    val canRename = askToRenameAfterRecordingStopped && recordId >= 0
    return RecordingStoppedRenamePolicy(
        showInAppRenameDialog = canRename && !stoppedFromFloatingOverlay,
        showFloatingOverlayRenameDialog = canRename && stoppedFromFloatingOverlay,
    )
}
