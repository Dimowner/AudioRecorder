package com.dimowner.audiorecorder.util

/**
 * Created on 18.08.2021.
 * @author Dimowner
 */
interface OnCopyListListener : OnCopyListener {
	fun onStartCopy(name: String)
}
