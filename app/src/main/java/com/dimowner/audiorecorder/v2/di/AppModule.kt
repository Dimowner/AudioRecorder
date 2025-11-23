package com.dimowner.audiorecorder.v2.di

import com.dimowner.audiorecorder.audio.player.AudioPlayerNew
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.v2.audio.AudioRecorderDelegate
import com.dimowner.audiorecorder.v2.audio.RecorderV2
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @IoDispatcher
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @MainDispatcher
    @Provides
    fun provideMainDispatcher(): CoroutineDispatcher {
        return Dispatchers.Main
    }

    @Singleton
    @Provides
    fun providePlayerContractNew(): PlayerContractNew.Player {
        return AudioPlayerNew()
    }

    @Singleton
    @Provides
    fun provideRecorderV2(
        audioRecorderDelegate: AudioRecorderDelegate
    ): RecorderV2 {
        return audioRecorderDelegate.provideAudioRecorder()
    }

    /**
     * Provides a CoroutineScope scoped to the application's lifetime.
     * It uses a SupervisorJob so that a failure of a child coroutine does not cancel others.
     * It uses Dispatchers.Default for background work.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        // Use SupervisorJob() to prevent child coroutine failures from propagating
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
