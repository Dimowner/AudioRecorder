package com.dimowner.audiorecorder.v2.audio
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

import android.system.ErrnoException
import android.system.OsConstants
import java.io.IOException

/**
 * Thrown when device runs out of storage space during an import operation.
 * Distinguished from a generic failure so the caller can surface an
 * actionable "not enough space" message instead of a plain "save failed".
 */
class NotEnoughSpaceException : IOException("Not enough storage space to save the record")

/**
 * Returns `true` when this throwable (or anything in its cause chain) is an out-of-space error.
 *
 * A full disk surfaces as an [ErrnoException] with `errno == ENOSPC`, usually wrapped in an
 * [IOException] ("write failed: ENOSPC (No space left on device)"). Codec/muxer layers may also
 * only expose the text, so the message is checked as a fallback.
 */
fun Throwable.isOutOfSpace(): Boolean {
    var cause: Throwable? = this
    // Guard against a self-referential cause chain.
    val seen = HashSet<Throwable>()
    while (cause != null && seen.add(cause)) {
        if (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) {
            return true
        }
        val message = cause.message
        if (message != null &&
            (message.contains("ENOSPC") || message.contains("No space left", ignoreCase = true))
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}
