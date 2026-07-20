package com.nexus.player.player.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.nexus.player.NexusApplication

@UnstableApi
class NexusMediaSource {
    
    private val dataSourceFactory: DataSource.Factory by lazy {
        DefaultDataSourceFactory(
            NexusApplication.instance,
            "NexusPlayer/1.0"
        )
    }
    
    private val extractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory().apply {
            setMp3ExtractorFlags(0) // Disable seeking optimization for corrupted files
            setFlacExtractorFlags(0)
            setWavExtractorFlags(0)
        }
    }
    
    fun createMediaSource(uri: Uri): MediaSource {
        return ProgressiveMediaSource.Factory(
            dataSourceFactory,
            extractorsFactory
        ).setLoadErrorHandlingPolicy(
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                    // Increase retry count for corrupted files
                    return Int.MAX_VALUE
                }
                
                override fun getRetryDelayMsFor(
                    loadErrorHandlingInfo: LoadErrorHandlingInfo
                ): Long {
                    // Shorter delay for corrupted file recovery
                    return 100L
                }
            }
        ).createMediaSource(MediaItem.fromUri(uri))
    }
}
