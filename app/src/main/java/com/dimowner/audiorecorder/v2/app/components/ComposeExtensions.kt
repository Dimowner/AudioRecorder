package com.dimowner.audiorecorder.v2.app.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Wraps an [onClick] lambda with another one that supports debounce clicks.
 * The default debounce time is 300ms.
 *
 * @param debounceTimeMillis The minimum time interval (in milliseconds) required between click
 * executions. Defaults to 300ms.
 * @param onClick The action to be executed when a valid (non-debounced) click occurs.
 * @return Debounced lambda onClick
 */
@Composable
fun onDebounceClick(
    onClick: () -> Unit,
    debounceTimeMillis: Long = 300L,
): () -> Unit {
    var lastClickTimeMillis: Long by remember { mutableLongStateOf(0L) }
    return {
        val currentTimeMillis = System.currentTimeMillis()

        // Check if enough time has passed since the last click
        if (currentTimeMillis - lastClickTimeMillis >= debounceTimeMillis) {
            onClick()
            lastClickTimeMillis = currentTimeMillis
        } else {
            //Do nothing
        }
    }
}

/**
 * A [Modifier] extension function that applies a debounced click listener to any Composable.
 *
 * @param debounceTimeMillis The minimum time interval (in milliseconds) required between click
 * executions. Defaults to 300ms.
 * @param onClick The action to be executed when a valid (non-debounced) click occurs.
 * @return A [Modifier] that makes the Composable element clickable with debouncing logic.
 */
fun Modifier.onDebounceClickable(
    debounceTimeMillis: Long = 300L,
    onClick: () -> Unit
): Modifier {
    return this.composed {
        val clickable = onDebounceClick(debounceTimeMillis = debounceTimeMillis, onClick = { onClick() })
        this.clickable { clickable() }
    }
}
