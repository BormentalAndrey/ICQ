package com.nexus.player.di

import android.content.Context
import com.nexus.player.NexusApplication
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.data.repository.MediaRepository
import com.nexus.player.player.audio.EqualizerEngine
import com.nexus.player.player.core.CorruptedFileHandler
import com.nexus.player.player.video.SubtitleRenderer
import java.util.concurrent.atomic.AtomicBoolean

object AppModule {
    
    @Volatile
    private var context: Context? = null
    private val isInitialized = AtomicBoolean(false)
    
    private val safeContext: Context
        get() = context ?: throw IllegalStateException(
            "AppModule не инициализирован! Вызовите AppModule.initialize(application) в NexusApplication.onCreate()"
        )
    
    private val preferencesManager: PreferencesManager by lazy { 
        PreferencesManager(safeContext) 
    }
    
    private val mediaRepository: MediaRepository by lazy { 
        MediaRepository(safeContext) 
    }
    
    private val corruptedFileHandler: CorruptedFileHandler by lazy { 
        CorruptedFileHandler() 
    }
    
    private val equalizerEngine: EqualizerEngine by lazy { 
        EqualizerEngine() 
    }
    
    private val subtitleRenderer: SubtitleRenderer by lazy { 
        SubtitleRenderer(safeContext) 
    }
    
    @Synchronized
    fun initialize(application: NexusApplication) {
        if (isInitialized.compareAndSet(false, true)) {
            context = application.applicationContext
        }
    }
    
    fun providePreferencesManager(): PreferencesManager = preferencesManager
    fun provideMediaRepository(): MediaRepository = mediaRepository
    fun provideCorruptedFileHandler(): CorruptedFileHandler = corruptedFileHandler
    fun provideEqualizerEngine(): EqualizerEngine = equalizerEngine
    fun provideSubtitleRenderer(): SubtitleRenderer = subtitleRenderer
}
