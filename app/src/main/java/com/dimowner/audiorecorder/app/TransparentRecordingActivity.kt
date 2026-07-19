/*
 * Copyright 2021 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.ErrorParser
import com.dimowner.audiorecorder.util.AndroidUtils
import com.dimowner.audiorecorder.v2.audio.AudioRecordingService
import timber.log.Timber

const val REQ_CODE_RECORD_AUDIO = 303
const val REQ_CODE_WRITE_EXTERNAL_STORAGE = 404

class TransparentRecordingActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var fileRepository: FileRepository

    private var recordingRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ARApplication.injector.providePrefs(applicationContext)
        fileRepository = ARApplication.injector.provideFileRepository(applicationContext)
    }

    /**
     * The service is started here and not in [onCreate] on purpose. While the activity is being
     * created the process may still be in a background state, and starting a service then fails
     * with BackgroundServiceStartNotAllowedException. By the time the activity is resumed the
     * process is in the foreground and the start is allowed.
     */
    override fun onResume() {
        super.onResume()
        if (recordingRequested) return
        if (!checkRecordPermission2()) return
        if (!prefs.isAppV2 && !checkStoragePermission2()) return
        recordingRequested = true
        startRecordingService()
        finish()
    }

    private fun startRecordingService() {
        try {
            if (prefs.isAppV2) {
                startRecordingServiceV2()
            } else {
                startLegacyRecordingService()
            }
        } catch (e: IllegalStateException) {
            //BackgroundServiceStartNotAllowedException and ForegroundServiceStartNotAllowedException.
            Timber.e(e)
            Toast.makeText(
                applicationContext, R.string.error_failed_to_start_recording, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startRecordingServiceV2() {
        AudioRecordingService.startServiceForeground(applicationContext)
    }

    private fun startLegacyRecordingService() {
        try {
            val startIntent = Intent(applicationContext, RecordingService::class.java)
            val path = fileRepository.provideRecordFile().absolutePath
            startIntent.action = RecordingService.ACTION_START_RECORDING_SERVICE
            startIntent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path)
            ContextCompat.startForegroundService(applicationContext, startIntent)
        } catch (e: CantCreateFileException) {
            Toast.makeText(applicationContext, ErrorParser.parseException(e), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        //Recording itself is started from onResume(), which runs right after this callback.
        if (grantResults.isEmpty()) {
            //The request was cancelled, otherwise onResume() would ask for the permission again.
            finish()
            return
        }
        when (requestCode) {
            //Without the record permission there is nothing to continue with.
            REQ_CODE_RECORD_AUDIO -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    finish()
                }
            }
            //Denied storage permission is not fatal, the record goes to the private dir instead.
            REQ_CODE_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                    setStoragePrivate()
                }
            }
        }
    }

    private fun setStoragePrivate() {
        prefs.isStoreDirPublic = false
        fileRepository.updateRecordingDir(applicationContext, prefs)
    }

    private fun checkRecordPermission2(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQ_CODE_RECORD_AUDIO
                )
                return false
            }
        }
        return true
    }

    private fun checkStoragePermission2(): Boolean {
        if (prefs.isStoreDirPublic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    AndroidUtils.showDialog(
                        this, R.string.warning, R.string.need_write_permission,
                        { _ ->
                            requestPermissions(
                                arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ),
                                REQ_CODE_WRITE_EXTERNAL_STORAGE
                            )
                        }, null
                    )
                    return false
                }
            }
        }
        return true
    }
}