package com.dimowner.audiorecorder.v2.app

import java.util.Locale

internal fun cleanRecordFileNameForSave(inputName: String): String {
    val normalized = inputName
        .filterNot(::isForbiddenCrossPlatformFilenameCharacter)
        .trim()
        .replace(Regex("\\s+"), " ")
        // Android can persist many names that later fail on Windows, macOS, Linux, or common
        // sync tools. Saving through this helper keeps every rename path portable while allowing
        // the editing UI to stay quiet and simply drop invalid punctuation at save time.
        .trimEnd(' ', '.')
    if (normalized == "." || normalized == "..") return ""

    val windowsDeviceName = normalized.substringBefore('.').uppercase(Locale.ROOT)
    if (windowsDeviceName in WINDOWS_RESERVED_DEVICE_NAMES) return ""

    return normalized
}

private fun isForbiddenCrossPlatformFilenameCharacter(character: Char): Boolean {
    return Character.isISOControl(character) || character in CROSS_PLATFORM_FORBIDDEN_FILENAME_CHARACTERS
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
