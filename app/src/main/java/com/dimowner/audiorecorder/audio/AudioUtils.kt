package com.dimowner.audiorecorder.audio

import kotlin.math.abs

/**
 * Computes the sum of absolute values of 16-bit little-endian PCM samples
 * contained in [this] byte array, reading up to [bytesRead] bytes.
 *
 * Each sample is 2 bytes (little-endian): low byte first, high byte second.
 * Any trailing odd byte is ignored.
 *
 * @param bytesRead the number of valid bytes in the array to process.
 * @return the sum of absolute sample values.
 */
@Suppress("MagicNumber")
fun ByteArray.sumOfAmplitudes(bytesRead: Int): Long {
    var sum = 0L
    var i = 0
    while (i + 1 < bytesRead) {
        // Convert to Int, mask the LSB, shift the MSB, and combine
        val lsb = this[i].toInt() and 0xFF
        val msb = this[i + 1].toInt() shl 8

        val sample = (lsb or msb).toShort()
        sum += abs(sample.toInt())
        i += 2
    }
    return sum
}
