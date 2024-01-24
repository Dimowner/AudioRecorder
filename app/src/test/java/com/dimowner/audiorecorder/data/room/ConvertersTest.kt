package com.dimowner.audiorecorder.data.room

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    @Test
    fun test_fromIntArray() {
        val intArray = intArrayOf(1, 2, 3, 4, 5)
        val expectedString = "1,2,3,4,5"

        val result = Converters().fromIntArray(intArray)

        assertEquals(expectedString, result)
    }

    @Test
    fun test_fromIntArray_withEmptyArray() {
        val intArray = intArrayOf()
        val expectedString = ""

        val result = Converters().fromIntArray(intArray)

        assertEquals(expectedString, result)
    }

    @Test
    fun test_toIntArray() {
        val stringValue = "1,2,3,4,5"
        val expectedIntArray = intArrayOf(1, 2, 3, 4, 5)

        val result = Converters().toIntArray(stringValue)

        assertArrayEquals(expectedIntArray, result)
    }

    @Test
    fun test_toIntArray_withEmptyString() {
        val stringValue = ""
        val expectedIntArray = intArrayOf()

        val result = Converters().toIntArray(stringValue)

        assertArrayEquals(expectedIntArray, result)
    }
}
