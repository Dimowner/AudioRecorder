/*
 * Copyright 2018 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.dimowner.audiorecorder.util.AndroidUtils
import timber.log.Timber
import timber.log.Timber.DebugTree

//import com.google.firebase.FirebaseApp;
class ARApplication : Application() {
    private var audioOutputChangeReceiver: AudioOutputChangeReceiver? = null
    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            //Timber initialization
            Timber.plant(DebugTree())
        }
        super.onCreate()
        PACKAGE_NAME = applicationContext.packageName
        applicationHandler = Handler(applicationContext.mainLooper)
        screenWidthDp = AndroidUtils.pxToDp(
            AndroidUtils.getScreenWidth(
                applicationContext
            )
        )
        injector = Injector()
        val prefs = injector!!.providePrefs(applicationContext)
        if (!prefs.isMigratedSettings) {
            prefs.migrateSettings()
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(AUDIO_BECOMING_NOISY)
        audioOutputChangeReceiver = AudioOutputChangeReceiver()
        registerReceiver(audioOutputChangeReceiver, intentFilter)
        try {
            val mTelephonyMgr = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            mTelephonyMgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            Timber.e(e)
        }
        //		FirebaseApp.initializeApp(this);
    }

    override fun onTerminate() {
        super.onTerminate()
        //This method is never called on real Android devices
        injector!!.releaseMainPresenter()
        injector!!.closeTasks()
        unregisterReceiver(audioOutputChangeReceiver)
    }

    private class AudioOutputChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val actionOfIntent = intent.action
            if (actionOfIntent != null && actionOfIntent == AUDIO_BECOMING_NOISY) {
                val player = injector!!.provideAudioPlayer()
                player.pause()
            }
        }
    }

    private val mPhoneStateListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String) {
            if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                //Pause playback when incoming call or on hold
                val player = injector!!.provideAudioPlayer()
                player.pause()
            }
            super.onCallStateChanged(state, incomingNumber)
        }
    }

    companion object {
        const val AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY"
        private var PACKAGE_NAME: String? = null

        @JvmField
		@Volatile
        var applicationHandler: Handler? = null

        /** Screen width in dp  */
        private var screenWidthDp = 0f
        @JvmStatic
		var injector: Injector? = null
        @JvmStatic
		fun appPackage(): String? {
            return PACKAGE_NAME
        }

        /**
         * Calculate density pixels per second for record duration.
         * Used for visualisation waveform in view.
         * @param durationSec record duration in seconds
         */
		@JvmStatic
		fun getDpPerSecond(durationSec: Float): Float {
            return if (durationSec > AppConstants.LONG_RECORD_THRESHOLD_SECONDS) {
                AppConstants.WAVEFORM_WIDTH * screenWidthDp / durationSec
            } else {
                AppConstants.SHORT_RECORD_DP_PER_SECOND.toFloat()
            }
        }

        @JvmStatic
		val longWaveformSampleCount: Int
            get() = (AppConstants.WAVEFORM_WIDTH * screenWidthDp).toInt()
    }
}