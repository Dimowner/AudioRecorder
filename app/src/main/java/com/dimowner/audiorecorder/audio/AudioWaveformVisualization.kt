package com.dimowner.audiorecorder.audio

import com.dimowner.audiorecorder.BackgroundQueue
import java.lang.Exception

/**
 * Created on 03.02.2021.
 * @author Dimowner
 */
class AudioWaveformVisualization(
		private val processingTasks: BackgroundQueue
) {

	fun decodeRecordWaveform(path: String, listener: AudioDecodingListener? = null) {
		processingTasks.postRunnable {
			AudioDecoder.decode(path, object : AudioDecodingListener {
				override fun isCanceled(): Boolean {
					return listener?.isCanceled() ?: false
				}

				override fun onStartProcessing(duration: Long, channelsCount: Int, sampleRate: Int) {
					listener?.onStartProcessing(duration, channelsCount, sampleRate)
				}

				override fun onProcessingProgress(percent: Int) {
					listener?.onProcessingProgress(percent)
				}

				override fun onProcessingCancel() {
					listener?.onProcessingCancel()
				}

				override fun onFinishProcessing(data: IntArray, duration: Long) {
					listener?.onFinishProcessing(data, duration)
				}

				override fun onError(exception: Exception) {
					listener?.onError(exception)
				}
			})
		}
	}
}
