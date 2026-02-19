package com.dimowner.audiorecorder.v2.app.settings

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.compose.ui.text.font.FontWeight
import androidx.core.text.HtmlCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class)
class SettingsExtensionsTest {

    @Test
    fun test_toAnnotatedString_preserves_bold() {
        // Create a SpannableString with bold text
        val spannableString = SpannableString("This is bold text")
        spannableString.setSpan(StyleSpan(Typeface.BOLD), 8, 12, 0) // "bold" is bold

        // Convert to AnnotatedString
        val annotatedString = spannableString.toAnnotatedString()

        // Verify the text content is preserved
        assertEquals("This is bold text", annotatedString.text)

        // Verify that bold styling is applied
        val spanStyles = annotatedString.spanStyles
        assertTrue("Should have at least one span style", spanStyles.isNotEmpty())
        
        val boldSpan = spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should contain a bold span", boldSpan != null)
        assertEquals(8, boldSpan?.start)
        assertEquals(12, boldSpan?.end)
    }

    @Test
    fun test_toAnnotatedString_with_html_bold() {
        // Parse HTML with bold tags
        val html = "<b>Bold text</b> and regular text"
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        
        // Convert to AnnotatedString
        val annotatedString = spanned.toAnnotatedString()

        // Verify the text content
        assertEquals("Bold text and regular text", annotatedString.text)

        // Verify that bold styling is applied
        val spanStyles = annotatedString.spanStyles
        assertTrue("Should have at least one span style", spanStyles.isNotEmpty())
        
        val boldSpan = spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should contain a bold span", boldSpan != null)
    }

    @Test
    fun test_toAnnotatedString_with_multiple_bold_spans() {
        // Parse HTML with multiple bold tags
        val html = "<b>First:</b> text <b>Second:</b> more text"
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        
        // Convert to AnnotatedString
        val annotatedString = spanned.toAnnotatedString()

        // Verify the text content
        assertEquals("First: text Second: more text", annotatedString.text)

        // Verify that we have multiple bold spans
        val spanStyles = annotatedString.spanStyles
        val boldSpans = spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should have at least two bold spans", boldSpans.size >= 2)
    }
}

class TestARApplication : Application() {
    override fun onTerminate() {
        // Do nothing - avoid calling Injector.closeTasks() in tests
    }
}
