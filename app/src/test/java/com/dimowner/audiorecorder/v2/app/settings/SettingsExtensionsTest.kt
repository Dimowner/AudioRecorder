package com.dimowner.audiorecorder.v2.app.settings

import android.app.Application
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.compose.ui.text.font.FontWeight
import androidx.core.text.HtmlCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dimowner.audiorecorder.util.TestARApplication
import com.dimowner.audiorecorder.v2.DefaultValues
import com.dimowner.audiorecorder.v2.data.model.BitRate
import com.dimowner.audiorecorder.v2.data.model.ChannelCount
import com.dimowner.audiorecorder.v2.data.model.RecordingFormat
import com.dimowner.audiorecorder.v2.data.model.SampleRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestARApplication::class, sdk = [36])
class SettingsExtensionsTest {

    // -------------------------------------------------------------------------
    // getThreeGpBitRateForSampleRate
    // -------------------------------------------------------------------------

    @Test
    fun `getThreeGpBitRateForSampleRate returns NB bitrate for SR8000 on ThreeGp format`() {
        val result = RecordingFormat.ThreeGp.getThreeGpBitRateForSampleRate(SampleRate.SR8000)
        assertEquals(DefaultValues.MAX_3GP_BITRATE_NB, result)
    }

    @Test
    fun `getThreeGpBitRateForSampleRate returns WB bitrate for SR16000 on ThreeGp format`() {
        val result = RecordingFormat.ThreeGp.getThreeGpBitRateForSampleRate(SampleRate.SR16000)
        assertEquals(DefaultValues.MAX_3GP_BITRATE_WB, result)
    }

    @Test
    fun `getThreeGpBitRateForSampleRate returns null for unsupported sample rate on ThreeGp`() {
        listOf(SampleRate.SR22050, SampleRate.SR32000, SampleRate.SR44100, SampleRate.SR48000)
            .forEach { sr ->
                assertNull(
                    "Expected null for $sr",
                    RecordingFormat.ThreeGp.getThreeGpBitRateForSampleRate(sr)
                )
            }
    }

    @Test
    fun `getThreeGpBitRateForSampleRate returns null when sampleRate is null on ThreeGp`() {
        assertNull(RecordingFormat.ThreeGp.getThreeGpBitRateForSampleRate(null))
    }

    @Test
    fun `getThreeGpBitRateForSampleRate returns null for M4a format regardless of sample rate`() {
        SampleRate.entries.forEach { sr ->
            assertNull(
                "Expected null for M4a with $sr",
                RecordingFormat.M4a.getThreeGpBitRateForSampleRate(sr)
            )
        }
    }

    @Test
    fun `getThreeGpBitRateForSampleRate returns null for Wav format regardless of sample rate`() {
        SampleRate.entries.forEach { sr ->
            assertNull(
                "Expected null for Wav with $sr",
                RecordingFormat.Wav.getThreeGpBitRateForSampleRate(sr)
            )
        }
    }

    // -------------------------------------------------------------------------
    // sizeMbPerMin
    // -------------------------------------------------------------------------

    @Test
    fun `sizeMbPerMin returns 0 when format is null`() {
        assertEquals(0f, sizeMbPerMin(null, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo), 0f)
    }

    @Test
    fun `sizeMbPerMin returns 0 for M4a when bitrate is null`() {
        assertEquals(0f, sizeMbPerMin(RecordingFormat.M4a, SampleRate.SR44100, null, ChannelCount.Stereo), 0f)
    }

    @Test
    fun `sizeMbPerMin computes correct value for M4a at 128kbps`() {
        // 60s * (128000 / 8) / 1_000_000 = 60 * 16000 / 1_000_000 = 0.96
        val expected = (60f * (128000f / 8f)) / 1_000_000f
        assertEquals(expected, sizeMbPerMin(RecordingFormat.M4a, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo), 0.0001f)
    }

    @Test
    fun `sizeMbPerMin computes correct value for ThreeGp at SR8000 (AMR-NB)`() {
        val expected = (60f * (DefaultValues.MAX_3GP_BITRATE_NB / 8f)) / 1_000_000f
        assertEquals(expected, sizeMbPerMin(RecordingFormat.ThreeGp, SampleRate.SR8000, null, ChannelCount.Mono), 0.0001f)
    }

    @Test
    fun `sizeMbPerMin computes correct value for ThreeGp at SR16000 (AMR-WB)`() {
        val expected = (60f * (DefaultValues.MAX_3GP_BITRATE_WB / 8f)) / 1_000_000f
        assertEquals(expected, sizeMbPerMin(RecordingFormat.ThreeGp, SampleRate.SR16000, null, ChannelCount.Mono), 0.0001f)
    }

    @Test
    fun `sizeMbPerMin falls back to WB bitrate for ThreeGp with unsupported sample rate`() {
        // null sampleRate → fallback to MAX_3GP_BITRATE_WB
        val expected = (60f * (DefaultValues.MAX_3GP_BITRATE_WB / 8f)) / 1_000_000f
        assertEquals(expected, sizeMbPerMin(RecordingFormat.ThreeGp, null, null, ChannelCount.Mono), 0.0001f)
    }

    @Test
    fun `sizeMbPerMin computes correct value for Wav stereo 44100`() {
        // 60 * (44100 * 2 * 2) / 1_000_000
        val expected = (60f * (44100 * 2 * 2f)) / 1_000_000f
        assertEquals(expected, sizeMbPerMin(RecordingFormat.Wav, SampleRate.SR44100, null, ChannelCount.Stereo), 0.0001f)
    }

    @Test
    fun `sizeMbPerMin returns 0 for Wav when sampleRate is null`() {
        assertEquals(0f, sizeMbPerMin(RecordingFormat.Wav, null, null, ChannelCount.Stereo), 0f)
    }

    @Test
    fun `sizeMbPerMin returns 0 for Wav when channels is null`() {
        assertEquals(0f, sizeMbPerMin(RecordingFormat.Wav, SampleRate.SR44100, null, null), 0f)
    }

    // -------------------------------------------------------------------------
    // spaceToRecordingTimeMills
    // -------------------------------------------------------------------------

    @Test
    fun `spaceToRecordingTimeMills returns 0 when format is null`() {
        assertEquals(0L, spaceToRecordingTimeMills(1_000_000L, null, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills returns 0 when space is 0`() {
        assertEquals(0L, spaceToRecordingTimeMills(0L, RecordingFormat.M4a, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills returns 0 when space is negative`() {
        assertEquals(0L, spaceToRecordingTimeMills(-1L, RecordingFormat.M4a, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills returns 0 for M4a when bitrate is null`() {
        assertEquals(0L, spaceToRecordingTimeMills(1_000_000L, RecordingFormat.M4a, SampleRate.SR44100, null, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills computes correct value for M4a at 128kbps`() {
        val spaceBytes = 1_000_000L
        val expected = 1000L * (spaceBytes / (128000L / 8L))
        assertEquals(expected, spaceToRecordingTimeMills(spaceBytes, RecordingFormat.M4a, SampleRate.SR44100, BitRate.BR128, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills computes correct value for ThreeGp at SR8000 (AMR-NB)`() {
        val spaceBytes = 500_000L
        val expected = 1000L * (spaceBytes / (DefaultValues.MAX_3GP_BITRATE_NB / 8L))
        assertEquals(expected, spaceToRecordingTimeMills(spaceBytes, RecordingFormat.ThreeGp, SampleRate.SR8000, null, ChannelCount.Mono))
    }

    @Test
    fun `spaceToRecordingTimeMills computes correct value for ThreeGp at SR16000 (AMR-WB)`() {
        val spaceBytes = 500_000L
        val expected = 1000L * (spaceBytes / (DefaultValues.MAX_3GP_BITRATE_WB / 8L))
        assertEquals(expected, spaceToRecordingTimeMills(spaceBytes, RecordingFormat.ThreeGp, SampleRate.SR16000, null, ChannelCount.Mono))
    }

    @Test
    fun `spaceToRecordingTimeMills falls back to WB for ThreeGp with null sample rate`() {
        val spaceBytes = 500_000L
        val expected = 1000L * (spaceBytes / (DefaultValues.MAX_3GP_BITRATE_WB / 8L))
        assertEquals(expected, spaceToRecordingTimeMills(spaceBytes, RecordingFormat.ThreeGp, null, null, ChannelCount.Mono))
    }

    @Test
    fun `spaceToRecordingTimeMills computes correct value for Wav stereo 44100`() {
        val spaceBytes = 10_000_000L
        val expected = 1000L * (spaceBytes / (44100L * 2L * 2L))
        assertEquals(expected, spaceToRecordingTimeMills(spaceBytes, RecordingFormat.Wav, SampleRate.SR44100, null, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills returns 0 for Wav when sampleRate is null`() {
        assertEquals(0L, spaceToRecordingTimeMills(1_000_000L, RecordingFormat.Wav, null, null, ChannelCount.Stereo))
    }

    @Test
    fun `spaceToRecordingTimeMills returns 0 for Wav when channels is null`() {
        assertEquals(0L, spaceToRecordingTimeMills(1_000_000L, RecordingFormat.Wav, SampleRate.SR44100, null, null))
    }

    // -------------------------------------------------------------------------
    // isDurationLongerThanTwoHours
    // -------------------------------------------------------------------------

    @Test
    fun `isDurationLongerThanTwoHours returns false for exactly 2h 0m`() {
        assertFalse(isDurationLongerThanTwoHours(2, 0))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns true for 2h 1m`() {
        assertTrue(isDurationLongerThanTwoHours(2, 1))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns false for 1h 59m`() {
        assertFalse(isDurationLongerThanTwoHours(1, 59))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns true for 3h 0m`() {
        assertTrue(isDurationLongerThanTwoHours(3, 0))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns false for 0h 0m`() {
        assertFalse(isDurationLongerThanTwoHours(0, 0))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns false for negative hours`() {
        assertFalse(isDurationLongerThanTwoHours(-1, 30))
    }

    @Test
    fun `isDurationLongerThanTwoHours returns false for negative minutes`() {
        assertFalse(isDurationLongerThanTwoHours(3, -1))
    }

    // -------------------------------------------------------------------------
    // toAnnotatedString
    // -------------------------------------------------------------------------

    @Test
    fun `toAnnotatedString preserves bold span`() {
        val spannableString = SpannableString("This is bold text")
        spannableString.setSpan(StyleSpan(Typeface.BOLD), 8, 12, 0)

        val annotatedString = spannableString.toAnnotatedString()

        // Verify the text content is preserved
        assertEquals("This is bold text", annotatedString.text)
        val boldSpan = annotatedString.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should contain a bold span", boldSpan != null)
        assertEquals(8, boldSpan?.start)
        assertEquals(12, boldSpan?.end)
    }

    @Test
    fun `toAnnotatedString works with HTML bold`() {
        val spanned = HtmlCompat.fromHtml("<b>Bold text</b> and regular text", HtmlCompat.FROM_HTML_MODE_COMPACT)
        val annotatedString = spanned.toAnnotatedString()

        assertEquals("Bold text and regular text", annotatedString.text)
        val boldSpan = annotatedString.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should contain a bold span", boldSpan != null)
    }

    @Test
    fun `toAnnotatedString handles multiple bold spans`() {
        val spanned = HtmlCompat.fromHtml("<b>First:</b> text <b>Second:</b> more text", HtmlCompat.FROM_HTML_MODE_COMPACT)
        val annotatedString = spanned.toAnnotatedString()

        assertEquals("First: text Second: more text", annotatedString.text)
        val boldSpans = annotatedString.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue("Should have at least two bold spans", boldSpans.size >= 2)
    }
}

class TestARApplication : Application() {
    override fun onTerminate() {
        // Do nothing - avoid calling Injector.closeTasks() in tests
    }
}
