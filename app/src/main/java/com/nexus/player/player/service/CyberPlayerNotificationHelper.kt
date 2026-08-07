package com.nexus.player.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.nexus.player.R

class CyberPlayerNotificationHelper(private val context: Context) {

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm?.createNotificationChannel(
                NotificationChannel(
                    ERROR_CHANNEL_ID,
                    "Errors",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    fun buildInitialNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("NEXUS PLAYER")
            .setContentText("Готов к воспроизведению")
            .setSmallIcon(R.drawable.ic_neon_skull)
            .setColor(Color.parseColor("#FF007F"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun buildNotification(
        isPlaying: Boolean,
        title: String,
        artist: String,
        sessionToken: MediaSessionCompat.Token?
    ): Notification {
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_neon_skull)
            .setColor(Color.parseColor("#FF007F"))
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_previous, "Prev", createPI(ACTION_PREVIOUS))
            .addAction(playPauseIcon, playPauseTitle, createPI(playPauseAction))
            .addAction(R.drawable.ic_next, "Next", createPI(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createPI(action: String): PendingIntent {
        val intent = Intent(context, CyberPlayerService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
