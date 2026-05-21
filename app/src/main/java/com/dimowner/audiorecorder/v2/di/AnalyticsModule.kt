package com.dimowner.audiorecorder.v2.di

import com.dimowner.audiorecorder.v2.analytics.AnalyticsTracker
import com.dimowner.audiorecorder.v2.analytics.NoOpAnalyticsTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Singleton
    @Binds
    abstract fun bindAnalyticsTracker(impl: NoOpAnalyticsTracker): AnalyticsTracker
}
