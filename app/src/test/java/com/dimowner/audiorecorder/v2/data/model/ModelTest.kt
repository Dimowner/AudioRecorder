package com.dimowner.audiorecorder.v2.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTest {

    @Test
    fun test_sortOrder_convertToSortOrder_success() {
        //Success cases
        assertEquals(SortOrder.DateAsc, SortOrder.DateAsc.toString().convertToSortOrder())
        assertEquals(SortOrder.DateDesc, SortOrder.DateDesc.toString().convertToSortOrder())
        assertEquals(SortOrder.NameAsc, SortOrder.NameAsc.toString().convertToSortOrder())
        assertEquals(SortOrder.NameDesc, SortOrder.NameDesc.toString().convertToSortOrder())
        assertEquals(SortOrder.DurationShortest, SortOrder.DurationShortest.toString().convertToSortOrder())
        assertEquals(SortOrder.DurationLongest, SortOrder.DurationLongest.toString().convertToSortOrder())

        //Fail cases
        assertNull("".convertToSortOrder())
        assertNull("Any text string".convertToSortOrder())
    }

    @Test
    fun test_nameFormat_convertToNameFormat_success() {
        //Success cases
        assertEquals(NameFormat.Record, NameFormat.Record.toString().convertToNameFormat())
        assertEquals(NameFormat.Timestamp, NameFormat.Timestamp.toString().convertToNameFormat())
        assertEquals(NameFormat.Date, NameFormat.Date.toString().convertToNameFormat())
        assertEquals(NameFormat.DateUs, NameFormat.DateUs.toString().convertToNameFormat())
        assertEquals(NameFormat.DateIso8601, NameFormat.DateIso8601.toString().convertToNameFormat())

        //Fail cases
        assertNull("".convertToNameFormat())
        assertNull("Any text string".convertToNameFormat())
    }

    @Test
    fun test_recordingFormat_convertToRecordingFormat_success() {
        //Success cases
        assertEquals(RecordingFormat.M4a, RecordingFormat.M4a.value.convertToRecordingFormat())
        assertEquals(RecordingFormat.Wav, RecordingFormat.Wav.value.convertToRecordingFormat())
        assertEquals(RecordingFormat.ThreeGp, RecordingFormat.ThreeGp.value.convertToRecordingFormat())

        //Fail cases
        assertNull("".convertToRecordingFormat())
        assertNull("Any text string".convertToRecordingFormat())
    }

    @Test
    fun test_sampleRate_convertToSampleRate_success() {
        //Success cases
        assertEquals(SampleRate.SR8000, SampleRate.SR8000.value.convertToSampleRate())
        assertEquals(SampleRate.SR16000, SampleRate.SR16000.value.convertToSampleRate())
        assertEquals(SampleRate.SR22500, SampleRate.SR22500.value.convertToSampleRate())
        assertEquals(SampleRate.SR32000, SampleRate.SR32000.value.convertToSampleRate())
        assertEquals(SampleRate.SR44100, SampleRate.SR44100.value.convertToSampleRate())
        assertEquals(SampleRate.SR48000, SampleRate.SR48000.value.convertToSampleRate())

        //Fail cases
        assertNull((-1000).convertToSampleRate())
        assertNull((-1).convertToSampleRate())
        assertNull(0.convertToSampleRate())
        assertNull(1000.convertToSampleRate())
        assertNull(Int.MAX_VALUE.convertToSampleRate())
        assertNull(Int.MIN_VALUE.convertToSampleRate())
    }

    @Test
    fun test_bitRate_convertToBitRate_success() {
        //Success cases
        assertEquals(BitRate.BR12, BitRate.BR12.value.convertToBitRate())
        assertEquals(BitRate.BR24, BitRate.BR24.value.convertToBitRate())
        assertEquals(BitRate.BR48, BitRate.BR48.value.convertToBitRate())
        assertEquals(BitRate.BR96, BitRate.BR96.value.convertToBitRate())
        assertEquals(BitRate.BR128, BitRate.BR128.value.convertToBitRate())
        assertEquals(BitRate.BR192, BitRate.BR192.value.convertToBitRate())
        assertEquals(BitRate.BR256, BitRate.BR256.value.convertToBitRate())

        //Fail cases
        assertNull((-1000).convertToBitRate())
        assertNull((-1).convertToBitRate())
        assertNull(0.convertToBitRate())
        assertNull(1000.convertToBitRate())
        assertNull(Int.MAX_VALUE.convertToBitRate())
        assertNull(Int.MIN_VALUE.convertToBitRate())
    }

    @Test
    fun test_channelCount_convertToChannelCount_success() {
        //Success cases
        assertEquals(ChannelCount.Mono, ChannelCount.Mono.value.convertToChannelCount())
        assertEquals(ChannelCount.Stereo, ChannelCount.Stereo.value.convertToChannelCount())

        //Fail cases
        assertNull((-1000).convertToChannelCount())
        assertNull((-1).convertToChannelCount())
        assertNull(0.convertToChannelCount())
        assertNull(1000.convertToChannelCount())
        assertNull(Int.MAX_VALUE.convertToChannelCount())
        assertNull(Int.MIN_VALUE.convertToChannelCount())
    }
}
