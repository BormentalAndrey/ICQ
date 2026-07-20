package com.nexus.player.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes as ExoAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexus.player.NexusApplication
import com.nexus.player.R
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.di.AppModule
import com.nexus.player.player.audio.EqualizerEngine
import com.nexus.player.player.audio.KaraokeProcessor
import com.nexus.player.player.core.CorruptedFileHandler
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
        const val EXTRA_FILE_PATH = "FILE_PATH"
        const val EXTRA_START_POSITION = "START_POSITION"
    }
    
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val preferencesManager: PreferencesManager by lazy { AppModule.providePreferencesManager() }
    private val corruptedFileHandler: CorruptedFileHandler by lazy { AppModule.provideCorruptedFileHandler() }
    private val equalizerEngine: EqualizerEngine by lazy { AppModule.provideEqualizerEngine() }
    private val karaokeProcessor: KaraokeProcessor by lazy { AppModule.provideKaraokeProcessor() }
    
    private val _playbackState = MutableStateFlow<PlaybackResult>(PlaybackResult.Success)
    val playbackState: StateFlow<PlaybackResult> = _playbackState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentTrack = MutableStateFlow<MediaItem?>(null)
    val currentTrack: StateFlow<MediaItem?> = _currentTrack.asStateFlow()
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> player?.play()
                ACTION_PAUSE -> player?.pause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> stopSelf()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Create wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NexusPlayer:WakeLock"
        )
        
        // Register notification receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
        }
        registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        // Initialize MediaSession
        initializeMediaSession()
        
        // Initialize ExoPlayer
        initializePlayer()
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "NexusPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player?.play()
                }
                
                override fun onPause() {
                    player?.pause()
                }
                
                override fun onSkipToNext() {
                    playNext()
                }
                
                override fun onSkipToPrevious() {
                    playPrevious()
                }
                
                override fun onStop() {
                    stopSelf()
                }
                
                override fun onSeekTo(pos: Long) {
                    player?.seekTo(pos)
                }
            })
            
            isActive = true
        }
    }
    
    private fun initializePlayer() {
        val audioAttributes = ExoAudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _isPlaying.value = player?.isPlaying ?: false
                                updateNotification()
                            }
                            Player.STATE_ENDED -> {
                                playNext()
                            }
                            Player.STATE_BUFFERING -> {
                                // Update notification to show buffering
                            }
                            Player.STATE_IDLE -> {
                                // Player is idle
                            }
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlaybackError(error)
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        updateNotification()
                        
                        if (isPlaying) {
                            acquireAudioFocus()
                            wakeLock?.acquire(3600000) // 1 hour timeout
                        } else {
                            wakeLock?.release()
                        }
                    }
                    
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        _currentPosition.value = newPosition.positionMs
                        savePlaybackPosition(newPosition.positionMs)
                    }
                })
                
                playWhenReady = false
            }
        
        // Start position tracking
        startPositionTracking()
        
        // Load saved equalizer settings
        serviceScope.launch {
            preferencesManager.equalizerBands.collect { bands ->
                equalizerEngine.applyBands(bands)
            }
        }
    }
    
    private fun startPositionTracking() {
        serviceScope.launch {
            while (isActive) {
                player?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                    }
                }
                delay(250) // Update 4 times per second
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
                
                if (filePath != null) {
                    serviceScope.launch {
                        playFile(filePath, startPosition)
                    }
                } else {
                    player?.play()
                }
            }
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
            else -> {
                // Initial start
                val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
                if (filePath != null) {
                    serviceScope.launch {
                        playFile(filePath)
                    }
                }
            }
        }
        
        return START_STICKY
    }
    
    private suspend fun playFile(filePath: String, startPosition: Long = 0L) {
        _playbackState.value = PlaybackResult.Success
        
        // Analyze file for corruption
        val damageResult = corruptedFileHandler.analyzeDamage(filePath)
        _playbackState.value = damageResult
        
        when (damageResult) {
            is PlaybackResult.FatalError -> {
                if (damageResult.canAttemptRecovery) {
                    // Try recovery
                    repairAndPlay(filePath, startPosition)
                    return
                } else {
                    showErrorNotification(damageResult.userMessage)
                    return
                }
            }
            is PlaybackResult.CorruptedButPlaying -> {
                showDamageNotification(damageResult)
            }
            else -> {
                // File is OK
            }
        }
        
        val mediaItem = MediaItem.fromUri("file://$filePath")
        _currentTrack.value = mediaItem
        
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            
            if (startPosition > 0) {
                seekTo(startPosition)
            }
            
            play()
        }
        
        updateNotification()
    }
    
    private suspend fun repairAndPlay(filePath: String, startPosition: Long) {
        _playbackState.value = PlaybackResult.RecoveryInProgress(0f)
        
        AppModule.provideMediaRepository().repairStream(filePath).collect { result ->
            when (result) {
                is PlaybackResult.RecoveryComplete -> {
                    if (result.success && result.recoveredPath != null) {
                        playFile(result.recoveredPath, startPosition)
                    } else {
                        _playbackState.value = PlaybackResult.FatalError(
                            throwable = Exception("Recovery failed"),
                            userMessage = "Не удалось восстановить файл"
                        )
                    }
                }
                is PlaybackResult.RecoveryInProgress -> {
                    _playbackState.value = result
                }
                else -> {}
            }
        }
    }
    
    private fun handlePlaybackError(error: PlaybackException) {
        Log.e("CyberPlayerService", "Playback error", error)
        
        when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                _playbackState.value = PlaybackResult.FatalError(
                    throwable = error,
                    userMessage = "Файл не найден или поврежден"
                )
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                _playbackState.value = PlaybackResult.CorruptedButPlaying(
                    damagePercent = 50f,
                    message = "Ошибка декодирования. Пропускаем поврежденные данные."
                )
                
                // Try to skip corrupted data and continue
                serviceScope.launch {
                    delay(100)
                    player?.apply {
                        seekTo(currentPosition + 1000) // Skip 1 second
                        playWhenReady = true
                    }
                }
            }
            else -> {
                _playbackState.value = PlaybackResult.FatalError(
                    throwable = error,
                    userMessage = "Неизвестная ошибка воспроизведения"
                )
            }
        }
        
        updateNotification()
    }
    
    private fun playNext() {
        // Implement playlist logic
        // For now, just loop the current track
        player?.seekTo(0)
        player?.play()
    }
    
    private fun playPrevious() {
        player?.seekTo(0)
        player?.play()
    }
    
    private fun acquireAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            player?.pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            player?.pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            player?.volume = 0.2f
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            player?.volume = 1.0f
                            player?.play()
                        }
                    }
                }
                .build()
            
            audioFocusRequest?.let {
                audioManager?.requestAudioFocus(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> player?.pause()
                        AudioManager.AUDIOFOCUS_GAIN -> player?.play()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }
    
    private fun updateNotification() {
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun buildNotification(): Notification {
        val player = this.player
        val isPlaying = player?.isPlaying ?: false
        val currentTrack = _currentTrack.value
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack?.mediaMetadata?.title?.toString() ?: "NEXUS PLAYER")
            .setContentText(currentTrack?.mediaMetadata?.artist?.toString() ?: "Кибернетический поток активен")
            .setSmallIcon(R.drawable.ic_neon_skull)
            .setColor(Color.parseColor("#FF007F"))
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_previous,
                "Previous",
                createPendingIntent(ACTION_PREVIOUS)
            )
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                createPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(
                R.drawable.ic_next,
                "Next",
                createPendingIntent(ACTION_NEXT)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        
        if (player != null) {
            builder.setProgress(
                player.duration.toInt(),
                player.currentPosition.toInt(),
                false
            )
        }
        
        return builder.build()
    }
    
    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
            .setContentTitle("Ошибка воспроизведения")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_error)
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun showDamageNotification(damageResult: PlaybackResult.CorruptedButPlaying) {
        val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
            .setContentTitle("Внимание: поврежденные данные")
            .setContentText(damageResult.message)
            .setSmallIcon(R.drawable.ic_warning)
            .setColor(Color.YELLOW)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, CyberPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private suspend fun savePlaybackPosition(position: Long) {
        preferencesManager.saveLastPosition(position)
        _currentTrack.value?.let { mediaItem ->
            preferencesManager.saveLastTrack(mediaItem.mediaId)
        }
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        
        unregisterReceiver(notificationReceiver)
        
        player?.release()
        player = null
        
        mediaSession?.release()
        mediaSession = null
        
        audioFocusRequest?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager?.abandonAudioFocusRequest(it)
            }
        }
        
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
