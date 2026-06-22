package com.dimowner.audiorecorder.v2.app.overlay

import com.dimowner.audiorecorder.AppConstantsV2.RECORD_DESCRIPTION_MAX_LENGTH
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal data class OverlayPosition(val x: Int, val y: Int)

internal data class OverlaySizeBounds(val minSize: Int, val maxSize: Int)

internal data class RenameOverlayStyle(val panelColor: Int, val textColor: Int)

internal data class RenameResetState(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val showInlineMessage: Boolean,
)

internal data class RenameKeyboardPolicy(
    val focusInputOnOpen: Boolean,
    val showKeyboardOnOpen: Boolean,
    val focusInputAfterReset: Boolean,
)

internal data class RenameDescriptionInputConfig(
    val hintRes: Int,
    val visibleLines: Int,
    val minimumHeightPx: Int,
    val verticalPaddingPx: Int,
    val clearDefaultMinimumHeight: Boolean,
)

internal data class RenameOverlaySaveRequest(
    val name: String,
    val description: String,
    val shouldRename: Boolean,
    val shouldUpdateDescription: Boolean,
    val showNameEmptyError: Boolean,
)

internal fun renameSpeechModeLabelRes(mode: RenameSpeechMode): Int {
    return when (mode) {
        RenameSpeechMode.Append -> R.string.rename_speech_mode_append_filename
        RenameSpeechMode.Replace -> R.string.rename_speech_mode_replace_filename
        RenameSpeechMode.AppendToAudioNote -> R.string.rename_speech_mode_append_description
    }
}

internal fun renameDescriptionInputConfig(): RenameDescriptionInputConfig {
    return RenameDescriptionInputConfig(
        hintRes = R.string.floating_rename_description_hint,
        visibleLines = 1,
        minimumHeightPx = 0,
        verticalPaddingPx = 0,
        clearDefaultMinimumHeight = true,
    )
}

internal fun buildRenameOverlaySaveRequest(
    originalName: String,
    originalDescription: String,
    inputName: String,
    inputDescription: String,
    maxDescriptionCharacters: Int = RECORD_DESCRIPTION_MAX_LENGTH,
): RenameOverlaySaveRequest {
    val trimmedName = inputName.trim()
    val boundedDescription = inputDescription.take(maxDescriptionCharacters.coerceAtLeast(0))

    return RenameOverlaySaveRequest(
        name = trimmedName,
        description = boundedDescription,
        shouldRename = trimmedName.isNotEmpty() && trimmedName != originalName,
        shouldUpdateDescription = boundedDescription != originalDescription,
        showNameEmptyError = trimmedName.isEmpty(),
    )
}

internal fun applyRenameSpeechTranscription(
    currentName: String,
    transcript: String,
    mode: RenameSpeechMode,
    maxVisibleNameCharacters: Int = 251,
): String {
    val normalizedTranscript = sanitizeFilenameSpeechTranscript(transcript)
    if (normalizedTranscript.isBlank()) return currentName

    val combined = when (mode) {
        RenameSpeechMode.Append -> listOf(currentName.trimEnd(), normalizedTranscript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        RenameSpeechMode.Replace -> normalizedTranscript
        RenameSpeechMode.AppendToAudioNote -> currentName
    }
    return combined.take(maxVisibleNameCharacters.coerceAtLeast(0))
}

private fun sanitizeFilenameSpeechTranscript(transcript: String): String {
    val normalized = transcript
        .filterNot(::isForbiddenCrossPlatformFilenameCharacter)
        .trim()
        .replace(Regex("\\s+"), " ")
        // Windows disallows filenames ending with a space or period; trim them from speech
        // results so a dictated replacement remains portable to common desktop filesystems.
        .trimEnd(' ', '.')
    if (normalized == "." || normalized == "..") return ""

    val windowsDeviceName = normalized.substringBefore('.').uppercase(Locale.ROOT)
    if (windowsDeviceName in WINDOWS_RESERVED_DEVICE_NAMES) return ""

    return normalized
}

private fun isForbiddenCrossPlatformFilenameCharacter(character: Char): Boolean {
    return Character.isISOControl(character) || character in CROSS_PLATFORM_FORBIDDEN_FILENAME_CHARACTERS
}

internal fun applyRenameSpeechTranscriptionToAudioNote(
    currentDescription: String,
    transcript: String,
    maxDescriptionCharacters: Int = RECORD_DESCRIPTION_MAX_LENGTH,
): String {
    val normalizedTranscript = transcript
        .filterNot { Character.isISOControl(it) }
        .trim()
        .replace(Regex("\\s+"), " ")
    if (normalizedTranscript.isBlank()) return currentDescription

    return listOf(currentDescription.trimEnd(), normalizedTranscript)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .take(maxDescriptionCharacters.coerceAtLeast(0))
}

internal fun buildRenameResetState(originalName: String): RenameResetState {
    val cursorPosition = originalName.length
    return RenameResetState(
        text = originalName,
        selectionStart = cursorPosition,
        selectionEnd = cursorPosition,
        showInlineMessage = false,
    )
}

internal fun renameKeyboardPolicy(): RenameKeyboardPolicy {
    return RenameKeyboardPolicy(
        // The floating overlay is optimized for GPS/driving use: open quietly and let the
        // dedicated speech button handle hands-light renaming without covering the host app.
        focusInputOnOpen = false,
        showKeyboardOnOpen = false,
        focusInputAfterReset = false,
    )
}

internal fun calculateOverlaySizeBounds(
    defaultSize: Int,
    screenWidth: Int,
    screenHeight: Int,
): OverlaySizeBounds {
    val minSize = defaultSize.coerceAtLeast(1)
    val halfSmallerScreen = min(screenWidth, screenHeight) / 2

    return OverlaySizeBounds(
        minSize = minSize,
        // Keep the range valid even on very small or transiently unmeasured displays.
        maxSize = halfSmallerScreen.coerceAtLeast(minSize),
    )
}

internal fun clampOverlaySize(
    savedSize: Int,
    defaultSize: Int,
    screenWidth: Int,
    screenHeight: Int,
): Int {
    val bounds = calculateOverlaySizeBounds(
        defaultSize = defaultSize,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
    )
    val requestedSize = if (savedSize == -1) defaultSize else savedSize
    return requestedSize.coerceIn(bounds.minSize, bounds.maxSize)
}

internal fun calculateRecordDiscSize(
    overlaySize: Int,
    defaultOverlaySize: Int,
    defaultDiscSize: Int,
): Int {
    if (defaultOverlaySize <= 0) return defaultDiscSize.coerceAtLeast(1)

    return (overlaySize * (defaultDiscSize.toFloat() / defaultOverlaySize.toFloat()))
        .roundToInt()
        .coerceAtLeast(1)
}

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

private val CROSS_PLATFORM_FORBIDDEN_FILENAME_CHARACTERS = setOf(
    '<',
    '>',
    ':',
    '"',
    '/',
    '\\',
    '|',
    '?',
    '*',
)

private val WINDOWS_RESERVED_DEVICE_NAMES = setOf(
    "CON",
    "PRN",
    "AUX",
    "NUL",
    "COM1",
    "COM2",
    "COM3",
    "COM4",
    "COM5",
    "COM6",
    "COM7",
    "COM8",
    "COM9",
    "LPT1",
    "LPT2",
    "LPT3",
    "LPT4",
    "LPT5",
    "LPT6",
    "LPT7",
    "LPT8",
    "LPT9",
)
