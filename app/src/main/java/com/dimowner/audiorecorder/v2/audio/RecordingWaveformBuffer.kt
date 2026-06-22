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

/**
 * Memory-bounded full-session amplitude accumulator for V2 recording.
 *
 * Unlike the sliding-window [recordingAmplitudes] buffer in [AudioRecordingService]
 * (used only for the live waveform display), this buffer captures the *entire* recording
 * session while staying bounded in memory. When the buffer reaches
 * [HALVING_CAP_MULTIPLIER] × [targetSize] samples a [compressUniformly] pass fires,
 * resampling the **entire original-sample timeline** down to [cap]/2 slots so every
 * slot always represents an equal time fraction of the recording up to that point.
 *
 * ### Why uniform resampling matters
 * A naïve pair-average halve produces slots with non-equal time widths: previously
 * compressed slots cover many original samples each, while freshly-added raw slots
 * cover only one. Treating them as uniform in [downsampleToIntArray] would shrink
 * the early part of the waveform and stretch the recent part. The fix is to always
 * sample in **original-sample-index space** (via [getAmplitudeAtOriginalIndex]) rather
 * than in buffer-slot space, both during compression and during final downsampling.
 *
 * At recording stop, call [downsampleToIntArray] to obtain a [targetSize]-element
 * [IntArray] suitable for persisting as the initial `amps` on a [Record]. This gives
 * the UI an immediate real waveform to display before [DecodeService] replaces it with
 * the fully-decoded version.
 *
 * **Memory bound:** worst case ≈ [HALVING_CAP_MULTIPLIER] × [targetSize] boxed integers.
 * For a typical 400 dp screen [targetSize] ≈ 600, so the cap is ≈ 2 400 elements (~38 KB)
 * regardless of recording length.
 *
 * **Thread-safety:** [add], [reset], and [downsampleToIntArray] are individually
 * `@Synchronized` and may be called from any thread. [compressUniformly] is internal
 * and always called under the same lock (from [add]).
 *
 * @param targetSize the number of output samples produced by [downsampleToIntArray].
 *   Usually [ARApplication.longWaveformSampleCount]. Passed explicitly so the class is
 *   testable without a real Application context.
 */
class RecordingWaveformBuffer(private val targetSize: Int) {

    companion object {
        /**
         * When the sample count reaches this multiple of [targetSize] a uniform
         * compression pass is triggered. After compression the buffer holds [cap]/2 slots,
         * giving headroom for the next batch of raw samples.
         */
        internal const val HALVING_CAP_MULTIPLIER = 4
    }

    private val samples = ArrayList<Int>(targetSize * HALVING_CAP_MULTIPLIER)
    private val cap: Int = targetSize * HALVING_CAP_MULTIPLIER

    /** Total number of raw amplitude samples ever passed to [add] since the last [reset]. */
    private var totalSamplesAdded: Int = 0

    /**
     * Value of [totalSamplesAdded] captured immediately after the most recent
     * [compressUniformly] pass. Zero if no compression has occurred yet.
     *
     * Together with [cap]/2 this defines the two regions of the buffer:
     * - **Compressed region** slots `0..[cap]/2-1`: uniformly cover original samples
     *   `0..[totalSamplesAtLastCompression]-1`.
     * - **Raw region** slots `[cap]/2..[samples.size]-1`: one slot per original sample,
     *   covering `[totalSamplesAtLastCompression]..[totalSamplesAdded]-1`.
     */
    private var totalSamplesAtLastCompression: Int = 0

    /** Number of slots currently held in the buffer. */
    fun size(): Int = samples.size

    /**
     * Appends [amplitude] (raw 0–32 767 from MediaRecorder.getMaxAmplitude) and triggers
     * a [compressUniformly] pass if the buffer reaches [cap].
     */
    @Synchronized
    fun add(amplitude: Int) {
        samples.add(amplitude)
        totalSamplesAdded++
        if (samples.size >= cap) {
            compressUniformly()
        }
    }

    /** Clears all accumulated samples and resets the timeline counters. */
    @Synchronized
    fun reset() {
        samples.clear()
        totalSamplesAdded = 0
        totalSamplesAtLastCompression = 0
    }

    /**
     * Produces a [targetSize]-element [IntArray] by downsampling the accumulated data
     * using the **original-sample timeline** as the reference axis:
     *
     * - Fewer total samples than [targetSize]: left-aligned, remainder zero-filled.
     * - Otherwise: equal-width averaging windows of `totalSamplesAdded / targetSize`
     *   original samples are mapped to output bins via [getAmplitudeAtOriginalIndex],
     *   which correctly handles the mixed compressed+raw buffer structure.
     *
     * Output values are in the 0–32 767 range, matching
     * [AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE].
     * [adjustWaveformHeights] is applied at display time by HomeViewModel / Mapper.
     */
    @Synchronized
    fun downsampleToIntArray(): IntArray {
        val result = IntArray(targetSize)
        if (totalSamplesAdded == 0) return result

        if (totalSamplesAdded <= targetSize) {
            // Short recording: copy the raw slots as-is; rest stays zero.
            for (i in 0 until totalSamplesAdded) {
                result[i] = getAmplitudeAtOriginalIndex(i)
            }
            return result
        }

        // General case: iterate over original-sample-index space so that every output
        // bin covers an equal duration of the recording regardless of compression state.
        val scale = totalSamplesAdded.toFloat() / targetSize.toFloat()
        val step = scale.toInt().coerceAtLeast(1)
        for (i in 0 until targetSize) {
            var sum = 0
            for (j in 0 until step) {
                val origIdx = (i * scale + j).toInt().coerceIn(0, totalSamplesAdded - 1)
                sum += getAmplitudeAtOriginalIndex(origIdx)
            }
            result[i] = sum / step
        }
        return result
    }

    /**
     * Resamples the entire buffer — which may be a mix of previously-compressed slots
     * and freshly-added raw slots — down to [cap]/2 uniformly-spaced slots by iterating
     * in **original-sample-index space** via [getAmplitudeAtOriginalIndex].
     *
     * After this call every slot covers an equal `totalSamplesAdded / (cap/2)` fraction
     * of the original recording timeline, eliminating the time-width mismatch that a
     * naïve buffer-slot-space average would produce.
     */
    internal fun compressUniformly() {
        val newSize = cap / 2
        val scale = totalSamplesAdded.toFloat() / newSize.toFloat()
        val step = scale.toInt().coerceAtLeast(1)
        val compressed = ArrayList<Int>(newSize)
        for (i in 0 until newSize) {
            var sum = 0
            for (j in 0 until step) {
                val origIdx = (i * scale + j).toInt().coerceIn(0, totalSamplesAdded - 1)
                sum += getAmplitudeAtOriginalIndex(origIdx)
            }
            compressed.add(sum / step)
        }
        samples.clear()
        samples.addAll(compressed)
        totalSamplesAtLastCompression = totalSamplesAdded
    }

    /**
     * Returns the amplitude for logical original-sample index [origIdx] by mapping it
     * into the correct physical buffer region:
     *
     * - If [origIdx] falls before [totalSamplesAtLastCompression] it is in the compressed
     *   region: scale to a slot in `0..[cap]/2-1`.
     * - Otherwise it is in the raw region: offset by the number of compressed slots.
     */
    private fun getAmplitudeAtOriginalIndex(origIdx: Int): Int {
        val compressedSlotCount = if (totalSamplesAtLastCompression > 0) cap / 2 else 0
        return if (totalSamplesAtLastCompression > 0 && origIdx < totalSamplesAtLastCompression) {
            val bufIdx = (origIdx.toLong() * compressedSlotCount / totalSamplesAtLastCompression)
                .toInt()
                .coerceIn(0, compressedSlotCount - 1)
            samples[bufIdx]
        } else {
            val bufIdx = compressedSlotCount + (origIdx - totalSamplesAtLastCompression)
            if (bufIdx < samples.size) samples[bufIdx] else 0
        }
    }
}

