package com.nexus.player.player.core

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.nexus.player.NexusApplication

@UnstableApi
class NexusMediaSource {
    
    private val dataSourceFactory: DataSource.Factory by lazy {
        DefaultDataSource.Factory(NexusApplication.instance)
    }
    
    private val extractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory()
    }
    
    fun createMediaSource(uri: Uri): MediaSource {
        return ProgressiveMediaSource.Factory(
            dataSourceFactory,
            extractorsFactory
        ).setLoadErrorHandlingPolicy(
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                    return Int.MAX_VALUE
                }
            }
        ).createMediaSource(MediaItem.fromUri(uri))
    }
}
