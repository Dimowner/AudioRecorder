package com.dimowner.audiorecorder.v2.app.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class OverlayPosition(val x: Int, val y: Int)

internal data class RenameOverlayStyle(val panelColor: Int, val textColor: Int)

internal fun clampOverlayPosition(
    savedX: Int,
    savedY: Int,
    screenWidth: Int,
    screenHeight: Int,
    overlayWidth: Int,
    overlayHeight: Int,
): OverlayPosition {
    val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
    val maxY = (screenHeight - overlayHeight).coerceAtLeast(0)
    val defaultX = maxX - 24.coerceAtMost(maxX)
    val defaultY = maxY / 2

    return OverlayPosition(
        // -1 is the persisted sentinel for "no user-selected position yet".
        // Other out-of-bounds values can happen after display-size changes and should clamp.
        x = if (savedX == -1) defaultX else savedX.coerceIn(0, maxX),
        y = if (savedY == -1) defaultY else savedY.coerceIn(0, maxY),
    )
}

internal fun calculateBoundedOverlayWidth(
    screenWidth: Int,
    horizontalMargin: Int,
    minimumWidth: Int,
    maximumWidth: Int,
): Int {
    val availableWidth = (screenWidth - horizontalMargin).coerceAtLeast(0)
    val effectiveMinimumWidth = minimumWidth.coerceAtMost(screenWidth)
    return availableWidth
        .coerceAtLeast(effectiveMinimumWidth)
        .coerceAtMost(maximumWidth)
}

internal fun calculateSaveFeedbackColor(progress: Float, idleColor: Int): Int {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress >= 1f) return idleColor

    val hue = 360f * clampedProgress
    val saturation = (1f - clampedProgress).coerceIn(0f, 1f)
    val rainbowColor = hsvToOpaqueColor(hue = hue, saturation = saturation, value = 1f)

    // The last slice intentionally settles into the exact idle color instead of ending on
    // an almost-white red hue. That makes the completion state deterministic and calm.
    val settleProgress = ((clampedProgress - 0.85f) / 0.15f).coerceIn(0f, 1f)
    return blendArgb(from = rainbowColor, to = idleColor, ratio = settleProgress)
}

internal fun renameOverlayStyle(isDarkTheme: Boolean): RenameOverlayStyle {
    return if (isDarkTheme) {
        RenameOverlayStyle(panelColor = 0xEC202020.toInt(), textColor = 0xFFFFFFFF.toInt())
    } else {
        RenameOverlayStyle(panelColor = 0xFFFFFFFF.toInt(), textColor = 0xFF000000.toInt())
    }
}

private fun hsvToOpaqueColor(hue: Float, saturation: Float, value: Float): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = value * saturation
    val hueSection = normalizedHue / 60f
    val secondary = chroma * (1f - abs(hueSection % 2f - 1f))
    val match = value - chroma
    val (redPrime, greenPrime, bluePrime) = when {
        hueSection < 1f -> Triple(chroma, secondary, 0f)
        hueSection < 2f -> Triple(secondary, chroma, 0f)
        hueSection < 3f -> Triple(0f, chroma, secondary)
        hueSection < 4f -> Triple(0f, secondary, chroma)
        hueSection < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }

    return argb(
        alpha = 255,
        red = ((redPrime + match) * 255f).roundToInt(),
        green = ((greenPrime + match) * 255f).roundToInt(),
        blue = ((bluePrime + match) * 255f).roundToInt(),
    )
}

private fun blendArgb(from: Int, to: Int, ratio: Float): Int {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverseRatio = 1f - clampedRatio
    return argb(
        alpha = (((from ushr 24) and 0xFF) * inverseRatio + ((to ushr 24) and 0xFF) * clampedRatio).roundToInt(),
        red = (((from ushr 16) and 0xFF) * inverseRatio + ((to ushr 16) and 0xFF) * clampedRatio).roundToInt(),
        green = (((from ushr 8) and 0xFF) * inverseRatio + ((to ushr 8) and 0xFF) * clampedRatio).roundToInt(),
        blue = ((from and 0xFF) * inverseRatio + (to and 0xFF) * clampedRatio).roundToInt(),
    )
}

private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
    return (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)
}
