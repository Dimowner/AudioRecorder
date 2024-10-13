/*
 * Copyright 2024 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dimowner.audiorecorder.v2.data.room

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
