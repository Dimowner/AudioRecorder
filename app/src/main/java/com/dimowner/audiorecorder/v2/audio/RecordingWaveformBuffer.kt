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
 * session while staying bounded in both memory **and** CPU cost.
 *
 * ### How it stays uniform
 * Every filled slot represents exactly [samplesPerSlot] original amplitude samples — a running
 * average is accumulated in [pendingSum]/[pendingCount] and flushed into a slot once that many
 * samples have arrived. When the buffer fills to [cap] slots, [compressUniformly] merges adjacent
 * pairs and doubles [samplesPerSlot]. Because every slot has the same time width before *and*
 * after a merge, the timeline stays uniform without ever having to resample the whole history.
 *
 * ### Why not resample the full timeline on every compression
 * Rebuilding the buffer from the complete original-sample timeline on each pass costs O(total
 * samples added), which makes a recording session cost O(N²) overall. At the 20 ms sampling
 * interval a 20-hour recording produces ~3.6M samples and ~3000 compression passes — billions of
 * operations spent on the waveform preview alone. Pairwise merging is O([cap]) per pass and
 * amortises to O(1) per sample, so cost no longer grows with recording length.
 *
 * At recording stop, call [downsampleToIntArray] to obtain an [IntArray] of at most [targetSize]
 * elements, suitable for persisting as the initial `amps` on a [Record]. This gives the UI an
 * immediate real waveform to display before [DecodeService] replaces it with the fully-decoded
 * version.
 *
 * **Memory bound:** a single [IntArray] of [HALVING_CAP_MULTIPLIER] × [targetSize] elements.
 * For a typical 400 dp screen [targetSize] ≈ 600, so ≈ 2 400 ints (~9.6 KB) regardless of
 * recording length.
 *
 * **Thread-safety:** all public methods are `@Synchronized` and may be called from any thread.
 * [compressUniformly] is internal and always called under the same lock (from [add]).
 *
 * @param targetSize the number of output samples produced by [downsampleToIntArray].
 *   Usually [ARApplication.longWaveformSampleCount]. Passed explicitly so the class is
 *   testable without a real Application context.
 */
class RecordingWaveformBuffer(private val targetSize: Int) {

    companion object {
        /**
         * When the slot count reaches this multiple of [targetSize] a compression pass is
         * triggered. After compression the buffer holds [cap]/2 slots, giving headroom for the
         * next batch of samples.
         */
        internal const val HALVING_CAP_MULTIPLIER = 4
    }

    private val cap: Int = targetSize * HALVING_CAP_MULTIPLIER
    private val slots = IntArray(cap)

    /** Number of slots currently filled in [slots]. */
    private var slotCount: Int = 0

    /** How many original amplitude samples each filled slot represents. Doubles on every merge. */
    private var samplesPerSlot: Int = 1

    /** Running average of the slot currently being filled; not yet part of [slots]. */
    private var pendingSum: Long = 0
    private var pendingCount: Int = 0

    /** Number of slots currently held in the buffer. */
    @Synchronized
    fun size(): Int = slotCount

    /**
     * Appends [amplitude] (raw 0–32 767 from MediaRecorder.getMaxAmplitude), folding it into the
     * slot currently being filled and triggering a [compressUniformly] pass when the buffer is full.
     */
    @Synchronized
    fun add(amplitude: Int) {
        pendingSum += amplitude
        pendingCount++
        if (pendingCount >= samplesPerSlot) {
            slots[slotCount++] = (pendingSum / pendingCount).toInt()
            pendingSum = 0
            pendingCount = 0
            if (slotCount >= cap) {
                compressUniformly()
            }
        }
    }

    /** Clears all accumulated samples and resets the timeline counters. */
    @Synchronized
    fun reset() {
        slotCount = 0
        samplesPerSlot = 1
        pendingSum = 0
        pendingCount = 0
    }

    /**
     * Produces an [IntArray] covering the whole recorded timeline, at most [targetSize] elements.
     *
     * - Fewer slots than [targetSize] while no compression has happened yet (one slot == one
     *   original sample): the captured slots are returned as-is, *without* padding. Every
     *   consumer spreads `amps` evenly across the record duration, so padding up to [targetSize]
     *   would claim a longer timeline than was recorded - a 5 s recording would be squeezed into
     *   the first `slots / targetSize` of the width with the zero tail drawn as silence.
     * - Otherwise: equal-width averaging windows over slot space. Slots all cover the same amount
     *   of time, so slot space *is* the recording timeline and no index remapping is needed.
     *
     * Output values are in the 0–32 767 range, matching
     * [AppConstantsV2.WAVEFORM_AMPLITUDE_MAX_VALUE].
     * [adjustWaveformHeights] is applied at display time by HomeViewModel / Mapper.
     */
    @Synchronized
    fun downsampleToIntArray(): IntArray {
        // The partially filled slot is included so the tail of a short recording is not lost.
        val hasPending = pendingCount > 0
        val pendingAverage = if (hasPending) (pendingSum / pendingCount).toInt() else 0
        val effective = slotCount + if (hasPending) 1 else 0
        if (effective == 0) return IntArray(0)

        if (samplesPerSlot == 1 && effective <= targetSize) {
            return IntArray(effective) { slotAt(it, pendingAverage) }
        }

        val result = IntArray(targetSize)
        val scale = effective.toFloat() / targetSize.toFloat()
        // step is at most HALVING_CAP_MULTIPLIER, so the Int accumulator below cannot overflow.
        val step = scale.toInt().coerceAtLeast(1)
        for (i in 0 until targetSize) {
            var sum = 0
            for (j in 0 until step) {
                val slotIndex = (i * scale + j).toInt().coerceIn(0, effective - 1)
                sum += slotAt(slotIndex, pendingAverage)
            }
            result[i] = sum / step
        }
        return result
    }

    /**
     * Halves the buffer by averaging adjacent slot pairs and doubling [samplesPerSlot], so every
     * slot keeps covering an equal slice of the recording. O([cap]) — independent of how much has
     * been recorded so far.
     *
     * An odd trailing slot (only reachable through an explicit call, since [add] compresses at the
     * even [cap]) is carried over as-is rather than dropped; it then represents half a slot width,
     * which is corrected as soon as the next pair merge covers it.
     */
    @Synchronized
    internal fun compressUniformly() {
        var newSize = slotCount / 2
        for (i in 0 until newSize) {
            slots[i] = (slots[2 * i] + slots[2 * i + 1]) / 2
        }
        if (slotCount % 2 == 1) {
            slots[newSize] = slots[slotCount - 1]
            newSize++
        }
        slotCount = newSize
        samplesPerSlot *= 2
    }

    private fun slotAt(index: Int, pendingAverage: Int): Int =
        if (index < slotCount) slots[index] else pendingAverage
}
