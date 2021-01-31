package com.dimowner.audiorecorder.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.audio.AudioDecoder
import timber.log.Timber
import java.io.*

private const val BUFFER_SIZE = 10240

fun downloadFiles(context: Context, list: List<File>, listener: OnCopyListener?) {
	var copied = 0
	var copiedPercent = 0
	var failed = 0
	var totalSize = 0f
	for (f in list) {
		totalSize += f.length()
	}
	totalSize /= 100

	for (f in list) {
		val size = f.length()/100f
		downloadFile(context, f, object : OnCopyListener {
			override fun isCancel(): Boolean = listener?.isCancel ?: false

			override fun onCopyProgress(percent: Int) {
				val globalProgress = copiedPercent + (percent*size)/totalSize
				listener?.onCopyProgress(globalProgress.toInt())
			}

			override fun onCanceled() {
				listener?.onCanceled()
			}

			override fun onCopyFinish(message: String) {
				copied++
				copiedPercent += ((size*100)/totalSize).toInt()
				if (copied + failed == list.size) {
					if (list.size == 1) {
						listener?.onCopyFinish(message)
					} else if (copied == list.size) {
						listener?.onCopyFinish(context.resources.getQuantityString(R.plurals.downloading_success_count, copied, copied))
					} else if (failed == list.size) {
						listener?.onCopyFinish(context.resources.getQuantityString(R.plurals.downloading_failed_count, failed, failed))
					} else if (copied > 0 && failed > 0) {
						listener?.onCopyFinish(context.getString(R.string.downloading_success_and_fail_count, copied, failed))
					} else {
						listener?.onCopyFinish(message)
					}
				}
			}

			override fun onError(message: String) {
				failed++
				if (copied + failed == list.size) {
					if (list.size == 1) {
						listener?.onError(message)
					} else if (copied > 0 && failed > 0) {
						listener?.onCopyFinish(context.getString(R.string.downloading_success_and_fail_count, copied, failed))
					} else if (failed == list.size) {
						listener?.onCopyFinish(context.resources.getQuantityString(R.plurals.downloading_failed_count, failed, failed))
					}
				}
			}
		})
	}
}

fun downloadFile(context: Context, sourceFile: File, listener: OnCopyListener?) {
	val sourceName = sourceFile.name
	var isCancel = false
	if (sourceFile.exists()) {
		val mime = AudioDecoder.readRecordMime(sourceFile)
		if (!isUriFileAlreadyExists(context, sourceName)) {
			val resolver: ContentResolver = context.contentResolver
			val contentValues = ContentValues()
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, sourceName)
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mime)
			contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
			val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
			if (uri != null) {
				try {
					val outputStream = resolver.openOutputStream(uri)
					if (outputStream != null) {
						val input = FileInputStream(sourceFile)
						val bis = BufferedInputStream(input)
						val sourceSize = bis.available()

						var size = BUFFER_SIZE
						var off = 0
						var buf = ByteArray(size)
						loop@ while (bis.available() > 0) {
							isCancel = listener?.isCancel ?: false
							if (isCancel) break@loop
							if (bis.available() < size) {
								size = bis.available()
								buf = ByteArray(size)
							}
							off += bis.read(buf, 0, size)
							outputStream.write(buf)
							listener?.onCopyProgress((100f * off.toFloat() / sourceSize.toFloat()).toInt())
						}
						outputStream.flush()
						outputStream.close()
						bis.close()
						input.close()
						if (isCancel) {
							resolver.delete(uri, null, null)
							listener?.onCanceled()
						} else {
							listener?.onCopyFinish(context.resources.getString(R.string.downloading_success, sourceName))
						}
					} else {
						resolver.delete(uri, null, null)
						listener?.onError(context.resources.getString(R.string.downloading_failed, sourceName))
					}
				} catch (e: IOException) {
					Timber.e(e)
					resolver.delete(uri, null, null)
					listener?.onError(context.resources.getString(R.string.downloading_failed, sourceName))
				}
			} else {
				listener?.onError(context.resources.getString(R.string.downloading_failed, sourceName))
			}
		} else {
			listener?.onError(context.resources.getString(R.string.downloading_failed_file_already_exists, sourceName))
		}
	} else {
		listener?.onError(context.resources.getString(R.string.downloading_failed, sourceName))
	}
}

private fun isUriFileAlreadyExists(context: Context, name: String): Boolean {
	val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
	val cursor = context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, null, null, null, null)
	cursor.use {
		if (it != null && it.moveToFirst()) {
			do {
				if (name.equals(it.getString(0), ignoreCase = true)) {
					return true
				}
			} while (it.moveToNext())
		}
	}
	return false
}
