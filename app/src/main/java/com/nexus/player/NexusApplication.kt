package com.nexus.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nexus.player.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NexusApplication : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        AppModule.initialize(this)
    }

    /**
     * Глобальная конфигурация Coil ImageLoader для всего приложения.
     * Автоматически подключает VideoFrameDecoder для генерации миниатюр из видеофайлов
     * и настраивает кэширование для плавного скролла списков с медиаконтентом.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // Регистрируем декодер для автоматического извлечения кадров из видеофайлов
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Выделяем 25% доступной оперативной памяти под кэш обложек и кадров
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // Создаем отдельную директорию в кэше приложения для превью
                    .directory(cacheDir.resolve("media_thumbnails_cache"))
                    // Ограничиваем кэш на диске до 2% от свободного места (но не менее 10МБ и не более 250МБ)
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            // Игнорируем системные заголовки кэширования для локальных файлов MediaStore
            .respectCacheHeaders(false)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nexus Player Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Foreground service for audio playback"
                    setShowBadge(false)
                },
                NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "Playback Errors",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical playback errors and recovery"
                }
            )

            val notificationManager = getSystemService(NotificationManager::class.java)
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_ID = "nexus_player_channel"
        const val ERROR_CHANNEL_ID = "nexus_player_errors"

        lateinit var instance: NexusApplication
            private set
    }
}
