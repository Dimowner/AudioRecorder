package com.dimowner.audiorecorder.v2.app.overlay

import android.speech.RecognizerIntent
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.v2.data.model.RenameSpeechMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingRecorderOverlayGeometryTest {

    @Test
    fun `renameSpeechRecognitionConfig uses activity recognizer action`() {
        val config = renameSpeechRecognitionConfig()

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, config.action)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, config.languageModel)
        assertEquals(1, config.maxResults)
        assertFalse(config.partialResults)
    }

    @Test
    fun `applyRenameSpeechTranscription appends transcript with one separating space`() {
        val result = applyRenameSpeechTranscription(
            currentName = "Drive note",
            transcript = "  fuel   stop  ",
            mode = RenameSpeechMode.Append,
        )

        assertEquals("Drive note fuel stop", result)
    }

    @Test
    fun `applyRenameSpeechTranscription appends to blank current name without leading space`() {
        val result = applyRenameSpeechTranscription(
            currentName = "",
            transcript = "parking level two",
            mode = RenameSpeechMode.Append,
        )

        assertEquals("parking level two", result)
    }

    @Test
    fun `applyRenameSpeechTranscription replaces current name`() {
        val result = applyRenameSpeechTranscription(
            currentName = "Old name",
            transcript = "new voice title",
            mode = RenameSpeechMode.Replace,
        )

        assertEquals("new voice title", result)
    }

    @Test
    fun `applyRenameSpeechTranscription keeps current name when transcript is blank`() {
        val result = applyRenameSpeechTranscription(
            currentName = "Existing",
            transcript = "   ",
            mode = RenameSpeechMode.Replace,
        )

        assertEquals("Existing", result)
    }

    @Test
    fun `applyRenameSpeechTranscription removes filename hostile characters`() {
        val result = applyRenameSpeechTranscription(
            currentName = "Existing",
            transcript = "road / bridge \\ tunnel\u0000",
            mode = RenameSpeechMode.Replace,
        )

        assertEquals("road bridge tunnel", result)
    }

    @Test
    fun `applyRenameSpeechTranscription caps visible name to 251 characters`() {
        val result = applyRenameSpeechTranscription(
            currentName = "",
            transcript = "a".repeat(300),
            mode = RenameSpeechMode.Replace,
        )

        assertEquals(251, result.length)
    }

    @Test
    fun `renameSpeechModeLabelRes describes filename and description targets explicitly`() {
        assertEquals(R.string.rename_speech_mode_append_filename, renameSpeechModeLabelRes(RenameSpeechMode.Append))
        assertEquals(R.string.rename_speech_mode_replace_filename, renameSpeechModeLabelRes(RenameSpeechMode.Replace))
        assertEquals(R.string.rename_speech_mode_append_description, renameSpeechModeLabelRes(RenameSpeechMode.AppendToAudioNote))
    }

    @Test
    fun `applyRenameSpeechTranscriptionToAudioNote appends transcript on a new line`() {
        val result = applyRenameSpeechTranscriptionToAudioNote(
            currentDescription = "First thought",
            transcript = "  second   thought  ",
        )

        assertEquals("First thought\nsecond thought", result)
    }

    @Test
    fun `applyRenameSpeechTranscriptionToAudioNote appends to blank note without leading newline`() {
        val result = applyRenameSpeechTranscriptionToAudioNote(
            currentDescription = "",
            transcript = "new note",
        )

        assertEquals("new note", result)
    }

    @Test
    fun `applyRenameSpeechTranscriptionToAudioNote caps note to max description length`() {
        val result = applyRenameSpeechTranscriptionToAudioNote(
            currentDescription = "Existing",
            transcript = "a".repeat(600),
            maxDescriptionCharacters = 20,
        )

        assertEquals("Existing\naaaaaaaaaaa", result)
    }

    @Test
    fun `buildRenameOverlaySaveRequest keeps pending filename and description changes`() {
        val request = buildRenameOverlaySaveRequest(
            originalName = "Original name",
            originalDescription = "Original description",
            inputName = "  New name  ",
            inputDescription = "Original description\nVoice note",
        )

        assertEquals("New name", request.name)
        assertEquals("Original description\nVoice note", request.description)
        assertTrue(request.shouldRename)
        assertTrue(request.shouldUpdateDescription)
        assertFalse(request.showNameEmptyError)
    }

    @Test
    fun `buildRenameOverlaySaveRequest rejects blank filename without dropping description draft`() {
        val request = buildRenameOverlaySaveRequest(
            originalName = "Original name",
            originalDescription = "",
            inputName = "   ",
            inputDescription = "Voice note",
        )

        assertEquals("", request.name)
        assertEquals("Voice note", request.description)
        assertFalse(request.shouldRename)
        assertTrue(request.shouldUpdateDescription)
        assertTrue(request.showNameEmptyError)
    }

    @Test
    fun `buildRenameResetState restores original name and moves cursor to end`() {
        val result = buildRenameResetState(originalName = "Original recording")

        assertEquals("Original recording", result.text)
        assertEquals("Original recording".length, result.selectionStart)
        assertEquals("Original recording".length, result.selectionEnd)
        assertFalse(result.showInlineMessage)
    }

    @Test
    fun `renameKeyboardPolicy keeps keyboard hidden until user edits manually`() {
        val policy = renameKeyboardPolicy()

        assertFalse(policy.focusInputOnOpen)
        assertFalse(policy.showKeyboardOnOpen)
        assertFalse(policy.focusInputAfterReset)
    }

    @Test
    fun `renameDescriptionInputConfig keeps description field compact`() {
        val config = renameDescriptionInputConfig()

        assertEquals(R.string.floating_rename_description_hint, config.hintRes)
        assertEquals(1, config.visibleLines)
        assertEquals(0, config.minimumHeightPx)
        assertEquals(0, config.verticalPaddingPx)
        assertTrue(config.clearDefaultMinimumHeight)
    }

    @Test
    fun `calculateOverlaySizeBounds uses default size as minimum and half smaller screen as maximum`() {
        val bounds = calculateOverlaySizeBounds(defaultSize = 56, screenWidth = 1080, screenHeight = 1920)

        assertEquals(56, bounds.minSize)
        assertEquals(540, bounds.maxSize)
    }

    @Test
    fun `clampOverlaySize uses default size when saved size is unset`() {
        val size = clampOverlaySize(savedSize = -1, defaultSize = 56, screenWidth = 1080, screenHeight = 1920)

        assertEquals(56, size)
    }

    @Test
    fun `clampOverlaySize clamps size to current screen bounds`() {
        assertEquals(56, clampOverlaySize(savedSize = 12, defaultSize = 56, screenWidth = 1080, screenHeight = 1920))
        assertEquals(540, clampOverlaySize(savedSize = 1000, defaultSize = 56, screenWidth = 1080, screenHeight = 1920))
    }

    @Test
    fun `calculateRecordDiscSize scales with overlay size`() {
        assertEquals(30, calculateRecordDiscSize(overlaySize = 56, defaultOverlaySize = 56, defaultDiscSize = 30))
        assertEquals(60, calculateRecordDiscSize(overlaySize = 112, defaultOverlaySize = 56, defaultDiscSize = 30))
    }

    @Test
    fun `clampOverlayPosition uses default when saved position is unset`() {
        val position = clampOverlayPosition(
            savedX = -1,
            savedY = -1,
            screenWidth = 1080,
            screenHeight = 1920,
            overlayWidth = 96,
            overlayHeight = 96,
        )

        assertEquals(960, position.x)
        assertEquals(912, position.y)
    }

    @Test
    fun `clampOverlayPosition keeps saved position inside visible bounds`() {
        val position = clampOverlayPosition(
            savedX = 200,
            savedY = 300,
            screenWidth = 1080,
            screenHeight = 1920,
            overlayWidth = 96,
            overlayHeight = 96,
        )

        assertEquals(200, position.x)
        assertEquals(300, position.y)
    }

    @Test
    fun `clampOverlayPosition clamps saved position outside visible bounds`() {
        val position = clampOverlayPosition(
            savedX = 5000,
            savedY = -50,
            screenWidth = 1080,
            screenHeight = 1920,
            overlayWidth = 96,
            overlayHeight = 96,
        )

        assertEquals(984, position.x)
        assertEquals(0, position.y)
    }

    @Test
    fun `calculateBoundedOverlayWidth caps panel width on large screens`() {
        val width = calculateBoundedOverlayWidth(
            screenWidth = 1080,
            horizontalMargin = 32,
            minimumWidth = 240,
            maximumWidth = 360,
        )

        assertEquals(360, width)
    }

    @Test
    fun `calculateBoundedOverlayWidth preserves side margins when possible`() {
        val width = calculateBoundedOverlayWidth(
            screenWidth = 320,
            horizontalMargin = 32,
            minimumWidth = 240,
            maximumWidth = 360,
        )

        assertEquals(288, width)
    }

    @Test
    fun `calculateBoundedOverlayWidth uses full width when screen is narrower than minimum`() {
        val width = calculateBoundedOverlayWidth(
            screenWidth = 200,
            horizontalMargin = 32,
            minimumWidth = 240,
            maximumWidth = 360,
        )

        assertEquals(200, width)
    }

    @Test
    fun `calculateSaveFeedbackColor starts with vivid red`() {
        val color = calculateSaveFeedbackColor(progress = 0f, idleColor = 0xFF444444.toInt())

        assertEquals(0xFFFF0000.toInt(), color)
    }

    @Test
    fun `calculateSaveFeedbackColor produces desaturated rainbow color mid animation`() {
        val color = calculateSaveFeedbackColor(progress = 0.5f, idleColor = 0xFF444444.toInt())

        assertEquals(0xFF80FFFF.toInt(), color)
    }

    @Test
    fun `calculateSaveFeedbackColor ends at idle color`() {
        val color = calculateSaveFeedbackColor(progress = 1f, idleColor = 0xFF444444.toInt())

        assertEquals(0xFF444444.toInt(), color)
    }

    @Test
    fun `renameOverlayStyle uses dark background and white text in dark theme`() {
        val style = renameOverlayStyle(isDarkTheme = true)

        assertEquals(0xEC202020.toInt(), style.panelColor)
        assertEquals(0xFFFFFFFF.toInt(), style.textColor)
    }

    @Test
    fun `renameOverlayStyle uses white background and black text in light theme`() {
        val style = renameOverlayStyle(isDarkTheme = false)

        assertEquals(0xFFFFFFFF.toInt(), style.panelColor)
        assertEquals(0xFF000000.toInt(), style.textColor)
    }
}
