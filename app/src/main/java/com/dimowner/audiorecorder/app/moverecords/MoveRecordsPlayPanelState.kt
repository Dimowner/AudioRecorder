package com.dimowner.audiorecorder.app.moverecords

/**
 * Created on 07.08.2021.
 * @author Dimowner
 */
data class MoveRecordsPlayPanelState(
	val playProgress: Int = 0,
	val playProgressMills: Long = 0,
	val recordPath: String = "",
	val playRecordName: String = "",
	val playRecordDuration: Long = 0,
	val activeRecordData: IntArray = intArrayOf()
)