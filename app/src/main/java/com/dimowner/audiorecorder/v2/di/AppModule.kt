package com.dimowner.audiorecorder.v2.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.dimowner.audiorecorder.audio.player.AudioPlayerNew
import com.dimowner.audiorecorder.audio.player.ExoAudioPlayer
import com.dimowner.audiorecorder.audio.player.PlayerContractNew
import com.dimowner.audiorecorder.v2.di.qualifiers.IoDispatcher
import com.dimowner.audiorecorder.v2.di.qualifiers.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @UnstableApi
    @Singleton
    @Provides
    fun providePlayerContractNew(@ApplicationContext context: Context): PlayerContractNew.Player {
        return ExoAudioPlayer(context)
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
