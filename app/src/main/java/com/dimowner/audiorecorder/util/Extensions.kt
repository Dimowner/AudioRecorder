package com.dimowner.audiorecorder.util

import android.view.View

inline var View.isVisible: Boolean
	get() = visibility == View.VISIBLE
	set(value) {
		visibility = if (value) View.VISIBLE else View.GONE
	}