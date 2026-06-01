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

package com.dimowner.audiorecorder.v2.app

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppExtensionsTest {

    @Test
    fun formatDuration_correctly_formats_duration() {
        val resources: Resources = ApplicationProvider.getApplicationContext<Context?>().resources

        val durationMillis = (365L * 24 * 60 * 60 + 24L * 60 * 60 + 60L * 60 + 60 + 1) * 1000
        Assert.assertEquals("1year 1day 01h:01m:01s", formatDuration(resources, durationMillis))

        val durationMillis2 = (365L * 24 * 60 * 60) * 1000
        Assert.assertEquals("1year 00m:00s", formatDuration(resources, durationMillis2))

        val durationMillis3 = (24L * 60 * 60 + 60L * 60 + 60 + 1) * 1000
        Assert.assertEquals("1day 01h:01m:01s", formatDuration(resources, durationMillis3))

        val durationMillis4 = (23L * 60 * 60 + 59 * 60 + 59) * 1000
        Assert.assertEquals("23h:59m:59s", formatDuration(resources, durationMillis4))

        val durationMillis5 = (10 * 365L * 24 * 60 * 60 + 125 * 24 * 60 * 60 + 23L * 60 * 60 + 59 * 60 + 59) * 1000
        Assert.assertEquals("10years 125days 23h:59m:59s", formatDuration(resources, durationMillis5))
    }

    @Test
    fun formatDuration_handles_zero_duration() {
        // Example input: 0 milliseconds
        val durationMillis = 0L

        val resources: Resources = ApplicationProvider.getApplicationContext<Context?>().resources

        val formattedDuration = formatDuration(resources, durationMillis)

        Assert.assertEquals("00m:00s", formattedDuration)
    }
}