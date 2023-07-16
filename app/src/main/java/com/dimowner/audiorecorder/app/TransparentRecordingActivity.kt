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
import com.dimowner.audiorecorder.ARApplication
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.data.FileRepository
import com.dimowner.audiorecorder.data.Prefs
import com.dimowner.audiorecorder.exception.CantCreateFileException
import com.dimowner.audiorecorder.exception.ErrorParser
import com.dimowner.audiorecorder.util.AndroidUtils

const val REQ_CODE_RECORD_AUDIO = 303
const val REQ_CODE_WRITE_EXTERNAL_STORAGE = 404

class TransparentRecordingActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var fileRepository: FileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ARApplication.injector.providePrefs()
        fileRepository = ARApplication.injector.provideFileRepository()

        if (checkRecordPermission2()) {
            if (checkStoragePermission2()) {
                startRecordingService()
                finish()
            }
        }
    }

    private fun startRecordingService() {
        try {
            val startIntent = Intent(applicationContext, RecordingService::class.java)
            val path = fileRepository.provideRecordFile().absolutePath
            startIntent.action = RecordingService.ACTION_START_RECORDING_SERVICE
            startIntent.putExtra(RecordingService.EXTRAS_KEY_RECORD_PATH, path)
            startService(startIntent)
        } catch (e: CantCreateFileException) {
            Toast.makeText(applicationContext, ErrorParser.parseException(e), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_CODE_RECORD_AUDIO && grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (checkStoragePermission2()) {
                startRecordingService()
            }
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
        ) {
            if (checkRecordPermission2()) {
                startRecordingService()
            }
        } else if (requestCode == REQ_CODE_WRITE_EXTERNAL_STORAGE && grantResults.isNotEmpty()
            && (grantResults[0] == PackageManager.PERMISSION_DENIED
            || grantResults[1] == PackageManager.PERMISSION_DENIED)
        ) {
            setStoragePrivate()
            startRecordingService()
        }
        finish()
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
                        { v ->
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