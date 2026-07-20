package com.nexus.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nexus.player.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NexusApplication : Application() {
    
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        AppModule.initialize(this)
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
