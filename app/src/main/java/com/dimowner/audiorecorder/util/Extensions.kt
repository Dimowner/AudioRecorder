package com.dimowner.audiorecorder.util

import android.content.Context
import android.content.res.Configuration
import android.view.View
import kotlin.math.abs

inline var View.isVisible: Boolean
	get() = visibility == View.VISIBLE
	set(value) {
		visibility = if (value) View.VISIBLE else View.GONE
	}

fun isUsingNightModeResources(context: Context): Boolean {
	return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
		Configuration.UI_MODE_NIGHT_YES -> true
		Configuration.UI_MODE_NIGHT_NO -> false
		Configuration.UI_MODE_NIGHT_UNDEFINED -> false
		else -> false
	}
}

fun Double.equalsDelta(other: Double, delta: Double = 0.000001) = abs(this - other) < delta

fun Float.equalsDelta(other: Float, delta: Float = 0.000001f) = abs(this - other) < delta
