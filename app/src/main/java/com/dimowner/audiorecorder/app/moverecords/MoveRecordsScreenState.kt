package com.dimowner.audiorecorder.app.moverecords

/**
 * Created on 19.06.2021.
 * @author Dimowner
 */
data class MoveRecordsScreenState(
	val list: List<MoveRecordsItem> = emptyList(),
	val showFooterItem: Boolean = false,
	val showProgress: Boolean = false,
	val count: Int = 0
)