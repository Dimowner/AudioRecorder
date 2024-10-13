package com.dimowner.audiorecorder.v2.di

import com.dimowner.audiorecorder.v2.data.FileDataSource
import com.dimowner.audiorecorder.v2.data.FileDataSourceImpl
import com.dimowner.audiorecorder.v2.data.PrefsV2
import com.dimowner.audiorecorder.v2.data.PrefsV2Impl
import com.dimowner.audiorecorder.v2.data.RecordsDataSource
import com.dimowner.audiorecorder.v2.data.RecordsDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class DataSourceModule {

    @Singleton
    @Binds
    abstract fun bindPrefs(impl: PrefsV2Impl): PrefsV2

    @Singleton
    @Binds
    abstract fun bindFileDataSource(impl: FileDataSourceImpl): FileDataSource

    @Singleton
    @Binds
    abstract fun bindRecordsDataSource(impl: RecordsDataSourceImpl): RecordsDataSource
}
