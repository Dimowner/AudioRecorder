package com.dimowner.audiorecorder.v2.app.overlay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.speech.RecognizerIntent

internal const val EXTRA_RENAME_SPEECH_RESULT_RECEIVER =
    "com.dimowner.audiorecorder.v2.app.overlay.EXTRA_RENAME_SPEECH_RESULT_RECEIVER"
internal const val EXTRA_RENAME_SPEECH_TEXT =
    "com.dimowner.audiorecorder.v2.app.overlay.EXTRA_RENAME_SPEECH_TEXT"
internal const val RENAME_SPEECH_RESULT_OK = 1
internal const val RENAME_SPEECH_RESULT_NO_MATCH = 2
internal const val RENAME_SPEECH_RESULT_ERROR = 3

internal data class RenameSpeechRecognitionConfig(
    val action: String = RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
    val languageModel: String = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
    val partialResults: Boolean = false,
    val maxResults: Int = 1,
    val preferOffline: Boolean = true,
)

internal fun renameSpeechRecognitionConfig(): RenameSpeechRecognitionConfig = RenameSpeechRecognitionConfig()

internal fun buildRenameSpeechRecognitionIntent(): Intent {
    val config = renameSpeechRecognitionConfig()
    return Intent(config.action).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, config.languageModel)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.preferOffline)
    }
}

class FloatingRenameSpeechRecognitionActivity : Activity() {

    private var resultReceiver: ResultReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultReceiver = readResultReceiver()
        if (savedInstanceState == null) {
            launchRecognizer()
        }
    }

    private fun launchRecognizer() {
        try {
            // Use the activity-based RecognizerIntent contract instead of SpeechRecognizer.
            // This delegates UI, permissions, and recognition lifecycle to the user's chosen
            // recognizer app, matching Chromium-style FUTO Voice Input integration and avoiding
            // low-level RecognitionListener callback management in Audio Recorder.
            startActivityForResult(buildRenameSpeechRecognitionIntent(), REQUEST_RENAME_SPEECH)
        } catch (e: ActivityNotFoundException) {
            sendResult(RENAME_SPEECH_RESULT_ERROR)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RENAME_SPEECH) return

        if (resultCode == RESULT_OK) {
            val transcript = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }
            if (transcript == null) {
                sendResult(RENAME_SPEECH_RESULT_NO_MATCH)
            } else {
                sendResult(RENAME_SPEECH_RESULT_OK, transcript)
            }
        } else {
            sendResult(RENAME_SPEECH_RESULT_NO_MATCH)
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun readResultReceiver(): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RENAME_SPEECH_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RENAME_SPEECH_RESULT_RECEIVER)
        }
    }

    private fun sendResult(resultCode: Int, transcript: String? = null) {
        val data = Bundle().apply {
            if (transcript != null) putString(EXTRA_RENAME_SPEECH_TEXT, transcript)
        }
        resultReceiver?.send(resultCode, data)
    }

    companion object {
        private const val REQUEST_RENAME_SPEECH = 1001
    }
}
