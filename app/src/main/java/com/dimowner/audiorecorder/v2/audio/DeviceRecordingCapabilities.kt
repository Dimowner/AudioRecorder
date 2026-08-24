/*
 * Copyright 2026 Dmytro Ponomarenko
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
package com.dimowner.audiorecorder.v2.audio

import android.media.MediaCodecList
import android.media.MediaFormat
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Returned when nothing limits the bitrate, so it can be safely combined with [minOf]. */
const val BIT_RATE_UNLIMITED: Int = Int.MAX_VALUE

/**
 * Highest bitrate the platform recorder will honour, and how to find it out.
 *
 * `MediaRecorder.setAudioEncodingBitRate()` is a request, not a contract: `StagefrightRecorder`
 * silently clips it to the `maxBitRate` its media profile declares for the AAC encoder, logging
 * only a warning ("Intended audio encoding bit rate (192000) is too large and will be set to
 * (96000)"). Many devices still ship the old AOSP value of 96000, so a 192 kbps recording quietly
 * ends up at 96 kbps. There is no public API for that limit, so it is pieced together from:
 *
 *  1. the device media profile - the very file `StagefrightRecorder` reads (see [MEDIA_PROFILES_PATHS]);
 *  2. the AAC encoder's own bitrate range, via [MediaCodecList];
 *  3. the AAC-LC frame ceiling implied by the selected sample rate and channel count;
 *  4. what previous recordings on this device actually produced - see [learnFromRecording].
 *
 * Sources 1 and 2 are configuration and can lie in either direction; source 4 is evidence and
 * overrides them for good once a real recording has been measured.
 */
@Singleton
class DeviceRecordingCapabilities @Inject constructor(
    private val prefs: PrefsV2,
) {

    /**
     * Highest bitrate worth offering for [format] at [sampleRate] / [channelCount], in bits per
     * second, or [BIT_RATE_UNLIMITED] when nothing is known to limit it.
     *
     * Cheap and safe to call from the main thread: it only reads already-loaded preferences and
     * does some arithmetic. Call [detect] once per settings screen (off the main thread) to keep
     * the persisted device limit up to date.
     */
    fun maxBitRate(format: RecordingFormat, sampleRate: SampleRate, channelCount: ChannelCount): Int {
        if (!format.hasBitrate) return BIT_RATE_UNLIMITED
        val deviceLimit = prefs.deviceM4aMaxBitRate.takeIf { it > 0 } ?: BIT_RATE_UNLIMITED
        val formatLimit = if (format == RecordingFormat.M4a) {
            aacFrameCeiling(sampleRate.value, channelCount.value)
        } else {
            BIT_RATE_UNLIMITED
        }
        return minOf(deviceLimit, formatLimit)
    }

    /**
     * Reads the device configuration and persists the limit it declares. Blocking (parses a file
     * and queries the codec list), so call it off the main thread.
     *
     * A limit that was measured from a real recording is never overwritten - evidence beats
     * configuration, which on devices shipping several media profile files can be the wrong file.
     */
    fun detect() {
        if (prefs.isDeviceMaxBitRateMeasured) return
        val limit = minOf(mediaProfilesAacMaxBitRate(), aacEncoderMaxBitRate())
        val stored = if (limit == BIT_RATE_UNLIMITED) 0 else limit
        if (stored != prefs.deviceM4aMaxBitRate) {
            Timber.d("Detected device recording bitrate limit: $stored bps")
            prefs.deviceM4aMaxBitRate = stored
        }
    }

    /**
     * Compares what a finished recording was asked for with what actually landed in the file and
     * remembers the difference, so the settings screen stops offering bitrates this device cannot
     * deliver (and starts offering them again if it turns out it can).
     *
     * Drops that the sample rate and channel count already explain are ignored: they are covered
     * by the AAC-LC frame ceiling in [maxBitRate] and say nothing about a device-wide limit.
     */
    @Suppress("ReturnCount")
    fun learnFromRecording(
        format: RecordingFormat,
        requestedBitRate: Int,
        measuredBitRate: Int,
        sampleRate: Int,
        channelCount: Int,
    ) {
        if (format != RecordingFormat.M4a) return
        if (requestedBitRate <= 0 || measuredBitRate <= 0) return
        if (requestedBitRate > aacFrameCeiling(sampleRate, channelCount)) return

        if (measuredBitRate >= requestedBitRate * HONOURED_RATIO) {
            // The encoder delivered what was asked for, so the device limit is at least this high.
            if (prefs.deviceM4aMaxBitRate in 1 until requestedBitRate) {
                Timber.d("Device recorded $requestedBitRate bps, raising the limit to it")
                storeMeasuredLimit(requestedBitRate)
            }
            return
        }

        val limit = highestBitRateAtOrBelow(measuredBitRate * MEASURED_TOLERANCE)
        Timber.d("Device clipped $requestedBitRate bps to $measuredBitRate bps, limit is $limit bps")
        storeMeasuredLimit(limit)
    }

    private fun storeMeasuredLimit(limit: Int) {
        if (prefs.deviceM4aMaxBitRate != limit) {
            prefs.deviceM4aMaxBitRate = limit
        }
        prefs.isDeviceMaxBitRateMeasured = true
    }

    /** The highest bitrate the app offers that is still at or below [limit]. */
    private fun highestBitRateAtOrBelow(limit: Double): Int {
        val values = BitRate.entries.map { it.value }
        return values.filter { it <= limit }.maxOrNull() ?: values.min()
    }

    /**
     * AAC-LC carries at most 6144 bits per channel in every 1024-sample frame, which puts a hard
     * ceiling of `6 * sampleRate * channelCount` on the encoded bitrate - 96 kbps for 16 kHz mono,
     * for instance, no matter what the encoder is asked for.
     */
    private fun aacFrameCeiling(sampleRate: Int, channelCount: Int): Int =
        AAC_LC_MAX_BITS_PER_SAMPLE * sampleRate * channelCount

    /** The AAC `maxBitRate` from the media profile file, or [BIT_RATE_UNLIMITED] if unreadable. */
    private fun mediaProfilesAacMaxBitRate(): Int {
        for (path in mediaProfilesPaths()) {
            val maxBitRate = readAacMaxBitRate(File(path)) ?: continue
            Timber.d("Media profile $path caps AAC recording at $maxBitRate bps")
            return maxBitRate
        }
        return BIT_RATE_UNLIMITED
    }

    /**
     * Candidate media profile files, in the order `MediaProfiles` itself resolves them: the file
     * named by the `media.settings.xml` system property first, then the well known locations.
     * Devices ship several of these with different limits, so picking the right one matters.
     */
    private fun mediaProfilesPaths(): List<String> {
        val fromProperty = systemProperty(MEDIA_SETTINGS_PROPERTY)
        return if (fromProperty != null) listOf(fromProperty) + MEDIA_PROFILES_PATHS else MEDIA_PROFILES_PATHS
    }

    private fun readAacMaxBitRate(file: File): Int? {
        if (!file.isFile || !file.canRead() || file.length() > MAX_PROFILE_FILE_SIZE) return null
        val xml = try {
            file.readText()
        } catch (e: IOException) {
            Timber.d("Can't read media profile ${file.path}: ${e.message}")
            return null
        }
        val aacCap = AAC_ENCODER_CAP.find(xml)?.value ?: return null
        if (DISABLED_CAP.containsMatchIn(aacCap)) return null
        return MAX_BIT_RATE.find(aacCap)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** The AAC encoders' own upper bound; the most permissive one wins, as any of them may be picked. */
    private fun aacEncoderMaxBitRate(): Int {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_AUDIO_AAC, true) }
                }
                .mapNotNull { info ->
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).audioCapabilities?.bitrateRange?.upper
                }
                .maxOrNull() ?: BIT_RATE_UNLIMITED
        } catch (e: IllegalArgumentException) {
            Timber.d("Can't read AAC encoder capabilities: ${e.message}")
            BIT_RATE_UNLIMITED
        }
    }

    @Suppress("PrivateApi")
    private fun systemProperty(name: String): String? {
        return try {
            val getter = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            (getter.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
        } catch (e: ReflectiveOperationException) {
            Timber.d("Can't read system property $name: ${e.message}")
            null
        }
    }

    private companion object {
        /** AAC-LC allows 6144 bits per channel per 1024-sample frame, i.e. 6 bits per sample. */
        const val AAC_LC_MAX_BITS_PER_SAMPLE = 6

        /** Below this share of the requested bitrate a recording counts as clipped by the device. */
        const val HONOURED_RATIO = 0.85

        /** Encoders overshoot their target slightly, so allow some slack when reading a limit off a file. */
        const val MEASURED_TOLERANCE = 1.05

        const val MAX_PROFILE_FILE_SIZE = 1024L * 1024L

        const val MEDIA_SETTINGS_PROPERTY = "media.settings.xml"

        val MEDIA_PROFILES_PATHS = listOf(
            "/odm/etc/media_profiles_V1_0.xml",
            "/vendor/etc/media_profiles_V1_0.xml",
            "/system/etc/media_profiles_V1_0.xml",
            "/system/etc/media_profiles.xml",
        )

        val AAC_ENCODER_CAP = Regex("""<AudioEncoderCap[^>]*\bname="aac"[^>]*>""")
        val MAX_BIT_RATE = Regex("""maxBitRate="(\d+)"""")
        val DISABLED_CAP = Regex("""enabled="false"""")
    }
}
