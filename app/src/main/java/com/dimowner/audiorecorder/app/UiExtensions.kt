package com.dimowner.audiorecorder.app

import com.dimowner.audiorecorder.AppConstants
import com.dimowner.audiorecorder.app.settings.SettingsMapper

fun formatRecordInformation(settingsMapper: SettingsMapper, format: String, sampleRate: Int, size: Long): String {
	return when (format) {
		AppConstants.FORMAT_3GPP,
		AppConstants.FORMAT_3GP ->
			(settingsMapper.formatSize(size).toString() + AppConstants.SEPARATOR
					+ settingsMapper.convertFormatsToString(format)
					+ AppConstants.SEPARATOR + settingsMapper.convertSampleRateToString(sampleRate))
		AppConstants.FORMAT_M4A,
		AppConstants.FORMAT_WAV ->
			(settingsMapper.formatSize(size).toString() + AppConstants.SEPARATOR
					+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
					+ settingsMapper.convertSampleRateToString(sampleRate))

		else ->
			(settingsMapper.formatSize(size).toString() + AppConstants.SEPARATOR
					+ format + AppConstants.SEPARATOR
					+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR)
	}
}
