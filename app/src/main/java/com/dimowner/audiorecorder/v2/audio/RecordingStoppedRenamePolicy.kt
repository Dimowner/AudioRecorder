package com.dimowner.audiorecorder.v2.audio

internal data class RecordingStoppedRenamePolicy(
    val showInAppRenameDialog: Boolean,
    val showFloatingOverlayRenameDialog: Boolean,
)

internal fun recordingStoppedRenamePolicy(
    askToRenameAfterRecordingStopped: Boolean,
    recordId: Long,
    stoppedFromFloatingOverlay: Boolean,
    suppressRenameDialog: Boolean = false,
): RecordingStoppedRenamePolicy {
    val canRename = askToRenameAfterRecordingStopped && recordId >= 0 && !suppressRenameDialog
    return RecordingStoppedRenamePolicy(
        showInAppRenameDialog = canRename && !stoppedFromFloatingOverlay,
        showFloatingOverlayRenameDialog = canRename && stoppedFromFloatingOverlay,
    )
}
