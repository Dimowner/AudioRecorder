package com.dimowner.audiorecorder.data.room

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromIntArray(intArray: IntArray): String {
        return intArray.joinToString(separator = ",")
    }

    @TypeConverter
    fun toIntArray(value: String): IntArray {
        return if (value.isBlank()) {
            // If the input string is blank, return an empty IntArray.
            intArrayOf()
        } else {
            value.split(",").map { it.toInt() }.toIntArray()
        }
    }
}