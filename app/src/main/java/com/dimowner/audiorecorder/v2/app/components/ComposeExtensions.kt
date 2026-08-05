package com.dimowner.audiorecorder.v2.app.components

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import timber.log.Timber
import androidx.core.graphics.createBitmap

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

/**
 * A composable that keeps the screen on while [enabled] is true.
 * When [enabled] becomes false or the composable leaves the composition,
 * the FLAG_KEEP_SCREEN_ON flag is cleared.
 *
 * @param enabled Whether the screen should be kept on.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * A safe alternative to `painterResource` that guards against [Resources.NotFoundException]
 * crashes seen in the wild (Crashlytics reports of `ResourceIdCache.resolveResourcePath` throwing
 * for drawable ids that are present in the resource table but have no value for the device's
 * configuration — e.g. `R.drawable.waveform`, our only raster drawable, when the matching density
 * split isn't installed, plus app-updated-while-running and repacked-APK cases).
 *
 * Note this is only a safety net: a drawable should also always have a density-agnostic definition
 * in `res/drawable/` so it ships in the base APK and stays resolvable on every configuration.
 *
 * Deliberately avoids calling Compose's `painterResource` (and its internal `ResourceIdCache`)
 * since try/catch isn't supported around composable invocations, and the cache itself is the
 * source of the crash. Instead, the drawable is resolved and rasterized with plain framework
 * APIs inside a `remember` block, where exceptions can be safely caught.
 *
 * Returns `null` instead of crashing when the resource can't be resolved, so callers
 * can render a fallback (or nothing) instead of taking down the whole app.
 */
@Composable
fun rememberSafePainterResource(@DrawableRes id: Int): Painter? {
    val context = LocalContext.current
    return remember(id) {
        try {
            val drawable = ContextCompat.getDrawable(context, id) ?: return@remember null
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        } catch (e: Resources.NotFoundException) {
            Timber.e(e, "Failed to resolve drawable resource id=$id")
            null
        }
    }
}

