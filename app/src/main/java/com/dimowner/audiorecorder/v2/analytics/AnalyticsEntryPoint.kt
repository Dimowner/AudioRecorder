/*
 * Copyright 2026 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.v2.analytics

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt [EntryPoint] that exposes [AnalyticsTracker] to non-Hilt code.
 *
 * Intended for use by legacy V1 classes that cannot receive constructor
 * injection from Hilt (e.g. [com.dimowner.audiorecorder.Injector]).
 *
 * Usage:
 * ```java
 * AnalyticsEntryPoint entryPoint = EntryPointAccessors.fromApplication(
 *     context.getApplicationContext(),
 *     AnalyticsEntryPoint.class
 * );
 * AnalyticsTracker tracker = entryPoint.analyticsTracker();
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsEntryPoint {
    fun analyticsTracker(): AnalyticsTracker
}

