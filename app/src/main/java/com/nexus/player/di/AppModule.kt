package com.nexus.player.di

import android.content.Context
import com.nexus.player.NexusApplication
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.data.repository.MediaRepository
import com.nexus.player.player.audio.EqualizerEngine
import com.nexus.player.player.audio.FftAnalyzer
import com.nexus.player.player.audio.KaraokeProcessor
import com.nexus.player.player.core.CorruptedFileHandler
import com.nexus.player.player.video.SubtitleRenderer

object AppModule {
    
    private lateinit var context: Context
    
    private val preferencesManager: PreferencesManager by lazy { PreferencesManager(context) }
    private val mediaRepository: MediaRepository by lazy { MediaRepository(context) }
    private val corruptedFileHandler: CorruptedFileHandler by lazy { CorruptedFileHandler() }
    private val equalizerEngine: EqualizerEngine by lazy { EqualizerEngine() }
    private val fftAnalyzer: FftAnalyzer by lazy { FftAnalyzer() }
    private val karaokeProcessor: KaraokeProcessor by lazy { KaraokeProcessor() }
    private val subtitleRenderer: SubtitleRenderer by lazy { SubtitleRenderer(context) }
    
    fun initialize(application: NexusApplication) {
        context = application.applicationContext
    }
    
    fun providePreferencesManager(): PreferencesManager = preferencesManager
    fun provideMediaRepository(): MediaRepository = mediaRepository
    fun provideCorruptedFileHandler(): CorruptedFileHandler = corruptedFileHandler
    fun provideEqualizerEngine(): EqualizerEngine = equalizerEngine
    fun provideFftAnalyzer(): FftAnalyzer = fftAnalyzer
    fun provideKaraokeProcessor(): KaraokeProcessor = karaokeProcessor
    fun provideSubtitleRenderer(): SubtitleRenderer = subtitleRenderer
}
