package com.nexus.player.player.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexus.player.NexusApplication
import com.nexus.player.R
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.di.AppModule
import com.nexus.player.player.audio.EqualizerEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@UnstableApi
class CyberPlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "nexus_player_channel"
        const val ERROR_CHANNEL_ID = "nexus_player_errors"
        const val NOTIFICATION_ID = 1337

        const val ACTION_PLAY = "com.nexus.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nexus.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.nexus.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.nexus.player.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.nexus.player.ACTION_STOP"
        const val ACTION_SEEK_TO = "com.nexus.player.ACTION_SEEK_TO"
        const val ACTION_SET_EQUALIZER = "com.nexus.player.ACTION_SET_EQUALIZER"

        const val ACTION_PLAYBACK_STATE_CHANGED = "com.nexus.player.PLAYBACK_STATE_CHANGED"
        const val ACTION_POSITION_UPDATED = "com.nexus.player.POSITION_UPDATED"
        const val ACTION_TRACK_CHANGED = "com.nexus.player.TRACK_CHANGED"

        const val EXTRA_FILE_URI = "FILE_URI"
        const val EXTRA_FILE_PATH = "FILE_PATH"
        const val EXTRA_IS_PLAYING = "IS_PLAYING"
        const val EXTRA_CURRENT_POSITION = "CURRENT_POSITION"
        const val EXTRA_DURATION = "DURATION"
        const val EXTRA_EQUALIZER_PRESET = "EQUALIZER_PRESET"
        const val EXTRA_EQUALIZER_BANDS = "EQUALIZER_BANDS"
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val preferencesManager: PreferencesManager by lazy { AppModule.providePreferencesManager() }
    private val equalizerEngine: EqualizerEngine by lazy { AppModule.provideEqualizerEngine() }

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private var currentMediaUri: Uri? = null

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ACTION_PLAY -> player?.playWhenReady = true
                ACTION_PAUSE -> player?.pause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> stopSelf()
                ACTION_SEEK_TO -> player?.seekTo(intent.getLongExtra(EXTRA_CURRENT_POSITION, 0L))
                ACTION_SET_EQUALIZER -> {
                    intent.getStringExtra(EXTRA_EQUALIZER_PRESET)?.let { equalizerEngine.applyPreset(it) }
                    intent.getFloatArrayExtra(EXTRA_EQUALIZER_BANDS)?.let { equalizerEngine.applyBands(it.toList()) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nexus:WakeLock")
        createNotificationChannels()
        registerNotificationReceiver()
        initializePlayer()
        initializeMediaSession()
        startForegroundService()
    }

    private fun startForegroundService() {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NEXUS PLAYER").setContentText("Готов к воспроизведению")
            .setSmallIcon(R.drawable.ic_neon_skull).setColor(Color.parseColor("#FF007F"))
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun registerNotificationReceiver() {
        val f = IntentFilter().apply {
            addAction(ACTION_PLAY); addAction(ACTION_PAUSE); addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS); addAction(ACTION_STOP); addAction(ACTION_SEEK_TO)
            addAction(ACTION_SET_EQUALIZER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(notificationReceiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(notificationReceiver, f)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "NexusPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.playWhenReady = true }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopSelf() }
                override fun onSeekTo(pos: Long) { player?.seekTo(pos) }
            })
            isActive = true
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(), true
            )
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _isPlaying.value = this@apply.isPlaying
                            updateNotification()
                            broadcastPlaybackState()
                        }
                        if (state == Player.STATE_ENDED) playNext()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        updateNotification()
                        broadcastPlaybackState()
                        if (isPlaying) wakeLock?.acquire(3600000) else safeReleaseWakeLock()
                    }
                })
            }
        NexusApplication.instance.exoPlayer = player
        startPositionTracking()
        serviceScope.launch { preferencesManager.equalizerBands.collect { equalizerEngine.applyBands(it) } }
    }

    private fun safeReleaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) {} }

    private fun startPositionTracking() {
        serviceScope.launch {
            while (isActive) {
                player?.let { if (it.isPlaying) { _currentPosition.value = it.currentPosition; broadcastPositionUpdate() } }
                delay(250)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_FILE_URI) ?: intent.getStringExtra(EXTRA_FILE_PATH)
                if (uri != null) serviceScope.launch { playFile(uri) } else player?.playWhenReady = true
            }
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private suspend fun playFile(uriString: String) = withContext(Dispatchers.Main) {
        val mediaUri = Uri.parse(uriString)
        currentMediaUri = mediaUri
        acquireAudioFocus()
        player?.apply {
            stop()
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }
        wakeLock?.acquire(3600000)
        updateNotification()
        broadcastTrackChanged()
    }

    private fun playNext() { player?.seekTo(0); player?.playWhenReady = true; broadcastTrackChanged() }
    private fun playPrevious() { player?.seekTo(0); player?.playWhenReady = true; broadcastTrackChanged() }

    private fun acquireAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener {
                        when (it) {
                            AudioManager.AUDIOFOCUS_LOSS -> player?.pause()
                            AudioManager.AUDIOFOCUS_GAIN -> { player?.volume = 1.0f; player?.playWhenReady = true }
                        }
                    }.build()
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION") am.requestAudioFocus({ if (it == AudioManager.AUDIOFOCUS_LOSS) player?.pause() }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NEXUS PLAYER").setContentText("Воспроизведение")
        .setSmallIcon(R.drawable.ic_neon_skull).setColor(Color.parseColor("#FF007F")).setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(R.drawable.ic_previous, "Prev", createPI(ACTION_PREVIOUS))
        .addAction(R.drawable.ic_pause, "Pause", createPI(ACTION_PAUSE))
        .addAction(R.drawable.ic_next, "Next", createPI(ACTION_NEXT))
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        .build()

    private fun createPI(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, CyberPlayerService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun broadcastPlaybackState() {
        val intent = Intent(ACTION_PLAYBACK_STATE_CHANGED)
        intent.putExtra(EXTRA_IS_PLAYING, _isPlaying.value)
        intent.putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
        player?.let { intent.putExtra(EXTRA_DURATION, it.duration) }
        sendBroadcast(intent)
    }

    private fun broadcastPositionUpdate() {
        val intent = Intent(ACTION_POSITION_UPDATED)
        intent.putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
        player?.let { intent.putExtra(EXTRA_DURATION, it.duration) }
        sendBroadcast(intent)
    }

    private fun broadcastTrackChanged() {
        val intent = Intent(ACTION_TRACK_CHANGED)
        currentMediaUri?.toString()?.let { intent.putExtra(EXTRA_FILE_PATH, it) }
        sendBroadcast(intent)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ERROR_CHANNEL_ID, "Errors", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    override fun onDestroy() {
        NexusApplication.instance.exoPlayer = null
        serviceScope.cancel()
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) {}
        player?.release(); player = null
        mediaSession?.release(); mediaSession = null
        audioFocusRequest?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager?.abandonAudioFocusRequest(it) }
        safeReleaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
