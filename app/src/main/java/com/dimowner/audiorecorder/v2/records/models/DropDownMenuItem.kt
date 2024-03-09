package com.dimowner.audiorecorder.v2.records.models

data class DropDownMenuItem<T>(
    val id: T,
    val textResId: Int,
    val imageResId: Int
)
