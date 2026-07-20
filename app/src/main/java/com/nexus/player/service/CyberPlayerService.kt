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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes as ExoAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexus.player.R
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.di.AppModule
import com.nexus.player.player.audio.EqualizerEngine
import com.nexus.player.player.core.CorruptedFileHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@UnstableApi
class CyberPlayerService : Service() {
    
    companion object {
        // Notification channels
        const val CHANNEL_ID = "nexus_player_channel"
        const val ERROR_CHANNEL_ID = "nexus_player_errors"
        const val NOTIFICATION_ID = 1337
        
        // Actions for controlling playback
        const val ACTION_PLAY = "com.nexus.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nexus.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.nexus.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.nexus.player.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.nexus.player.ACTION_STOP"
        const val ACTION_SEEK_TO = "com.nexus.player.ACTION_SEEK_TO"
        const val ACTION_SET_EQUALIZER = "com.nexus.player.ACTION_SET_EQUALIZER"
        
        // Actions for broadcasting state to UI
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.nexus.player.PLAYBACK_STATE_CHANGED"
        const val ACTION_POSITION_UPDATED = "com.nexus.player.POSITION_UPDATED"
        const val ACTION_TRACK_CHANGED = "com.nexus.player.TRACK_CHANGED"
        const val ACTION_ERROR_OCCURRED = "com.nexus.player.ERROR_OCCURRED"
        const val ACTION_RECOVERY_PROGRESS = "com.nexus.player.RECOVERY_PROGRESS"
        
        // Intent extras
        const val EXTRA_FILE_PATH = "FILE_PATH"
        const val EXTRA_START_POSITION = "START_POSITION"
        const val EXTRA_IS_PLAYING = "IS_PLAYING"
        const val EXTRA_CURRENT_POSITION = "CURRENT_POSITION"
        const val EXTRA_DURATION = "DURATION"
        const val EXTRA_TRACK_NAME = "TRACK_NAME"
        const val EXTRA_TRACK_ARTIST = "TRACK_ARTIST"
        const val EXTRA_ERROR_MESSAGE = "ERROR_MESSAGE"
        const val EXTRA_DAMAGE_PERCENT = "DAMAGE_PERCENT"
        const val EXTRA_RECOVERY_PROGRESS = "RECOVERY_PROGRESS"
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
    private val corruptedFileHandler: CorruptedFileHandler by lazy { AppModule.provideCorruptedFileHandler() }
    private val equalizerEngine: EqualizerEngine by lazy { AppModule.provideEqualizerEngine() }
    
    private val _playbackState = MutableStateFlow<PlaybackResult>(PlaybackResult.Success)
    val playbackState: StateFlow<PlaybackResult> = _playbackState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentTrack = MutableStateFlow<MediaItem?>(null)
    val currentTrack: StateFlow<MediaItem?> = _currentTrack.asStateFlow()
    
    private var currentFilePath: String? = null
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> player?.play()
                ACTION_PAUSE -> player?.pause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> stopSelf()
                ACTION_SEEK_TO -> {
                    val position = intent.getLongExtra(EXTRA_CURRENT_POSITION, 0L)
                    player?.seekTo(position)
                }
                ACTION_SET_EQUALIZER -> {
                    val preset = intent.getStringExtra(EXTRA_EQUALIZER_PRESET)
                    val bands = intent.getFloatArrayExtra(EXTRA_EQUALIZER_BANDS)
                    if (preset != null) {
                        equalizerEngine.applyPreset(preset)
                    } else if (bands != null) {
                        equalizerEngine.applyBands(bands.toList())
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NexusPlayer:WakeLock"
        )
        
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
            addAction(ACTION_SEEK_TO)
            addAction(ACTION_SET_EQUALIZER)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
        
        initializeMediaSession()
        initializePlayer()
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "NexusPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { player?.play() }
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
                                _isPlaying.value = this@apply.isPlaying
                                updateNotification()
                                broadcastPlaybackState()
                            }
                            Player.STATE_ENDED -> playNext()
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlaybackError(error)
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        updateNotification()
                        broadcastPlaybackState()
                        if (isPlaying) {
                            acquireAudioFocus()
                            wakeLock?.acquire(3600000)
                        } else {
                            wakeLock?.release()
                        }
                    }
                })
                playWhenReady = false
            }
        
        startPositionTracking()
        
        serviceScope.launch {
            preferencesManager.equalizerBands.collect { bands ->
                equalizerEngine.applyBands(bands)
            }
        }
    }
    
    private fun startPositionTracking() {
        serviceScope.launch {
            while (isActive) {
                player?.let { p ->
                    if (p.isPlaying) {
                        _currentPosition.value = p.currentPosition
                        broadcastPositionUpdate()
                    }
                }
                delay(250)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
                if (filePath != null) {
                    currentFilePath = filePath
                    serviceScope.launch { playFile(filePath, startPosition) }
                } else {
                    player?.play()
                }
            }
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
            else -> {
                val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
                if (filePath != null) {
                    currentFilePath = filePath
                    serviceScope.launch { playFile(filePath) }
                }
            }
        }
        return START_STICKY
    }
    
    private suspend fun playFile(filePath: String, startPosition: Long = 0L) {
        _playbackState.value = PlaybackResult.Success
        
        val damageResult = corruptedFileHandler.analyzeDamage(filePath)
        _playbackState.value = damageResult
        
        when (damageResult) {
            is PlaybackResult.FatalError -> {
                broadcastError(damageResult.userMessage)
                if (damageResult.canAttemptRecovery) {
                    repairAndPlay(filePath, startPosition)
                    return
                } else {
                    showErrorNotification(damageResult.userMessage)
                    return
                }
            }
            is PlaybackResult.CorruptedButPlaying -> {
                showDamageNotification(damageResult)
                broadcastDamageWarning(damageResult)
            }
            else -> {}
        }
        
        val mediaItem = MediaItem.fromUri("file://$filePath")
        _currentTrack.value = mediaItem
        
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            if (startPosition > 0) seekTo(startPosition)
            play()
        }
        
        updateNotification()
        broadcastTrackChanged()
    }
    
    private suspend fun repairAndPlay(filePath: String, startPosition: Long) {
        _playbackState.value = PlaybackResult.RecoveryInProgress(0f)
        broadcastRecoveryProgress(0f)
        
        AppModule.provideMediaRepository().repairStream(filePath).collect { result ->
            when (result) {
                is PlaybackResult.RecoveryComplete -> {
                    if (result.success && result.recoveredPath != null) {
                        playFile(result.recoveredPath, startPosition)
                    } else {
                        val error = PlaybackResult.FatalError(
                            throwable = Exception("Recovery failed"),
                            userMessage = "Не удалось восстановить файл"
                        )
                        _playbackState.value = error
                        broadcastError(error.userMessage)
                    }
                }
                is PlaybackResult.RecoveryInProgress -> {
                    _playbackState.value = result
                    broadcastRecoveryProgress(result.progress)
                }
                else -> {}
            }
        }
    }
    
    private fun handlePlaybackError(error: PlaybackException) {
        Log.e("CyberPlayerService", "Playback error", error)
        
        val result = when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                PlaybackResult.FatalError(throwable = error, userMessage = "Файл не найден или поврежден")
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                serviceScope.launch {
                    delay(100)
                    player?.apply { seekTo(currentPosition + 1000); playWhenReady = true }
                }
                PlaybackResult.CorruptedButPlaying(damagePercent = 50f, message = "Ошибка декодирования. Пропускаем поврежденные данные.")
            }
            else -> {
                PlaybackResult.FatalError(throwable = error, userMessage = "Неизвестная ошибка воспроизведения")
            }
        }
        
        _playbackState.value = result
        if (result is PlaybackResult.FatalError) {
            broadcastError(result.userMessage)
        }
        updateNotification()
    }
    
    private fun playNext() {
        player?.seekTo(0)
        player?.play()
        broadcastTrackChanged()
    }
    
    private fun playPrevious() {
        player?.seekTo(0)
        player?.play()
        broadcastTrackChanged()
    }
    
    private fun acquireAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> player?.pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.2f
                        AudioManager.AUDIOFOCUS_GAIN -> { player?.volume = 1.0f; player?.play() }
                    }
                }
                .build()
            
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
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
        val isPlaying = player?.isPlaying ?: false
        val currentTrack = _currentTrack.value
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack?.mediaMetadata?.title?.toString() ?: "NEXUS PLAYER")
            .setContentText(currentTrack?.mediaMetadata?.artist?.toString() ?: "Кибернетический поток активен")
            .setSmallIcon(R.drawable.ic_neon_skull)
            .setColor(Color.parseColor("#FF007F"))
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_previous, "Previous", createPendingIntent(ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                createPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(R.drawable.ic_next, "Next", createPendingIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .apply {
                player?.let { setProgress(it.duration.toInt(), it.currentPosition.toInt(), false) }
            }
            .build()
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 1, notification)
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 2, notification)
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, CyberPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    
    private fun broadcastPlaybackState() {
        sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_PLAYING, _isPlaying.value)
            putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
            player?.let { putExtra(EXTRA_DURATION, it.duration) }
        })
    }
    
    private fun broadcastPositionUpdate() {
        sendBroadcast(Intent(ACTION_POSITION_UPDATED).apply {
            putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
            player?.let { putExtra(EXTRA_DURATION, it.duration) }
        })
    }
    
    private fun broadcastTrackChanged() {
        sendBroadcast(Intent(ACTION_TRACK_CHANGED).apply {
            currentFilePath?.let { putExtra(EXTRA_FILE_PATH, it) }
            _currentTrack.value?.let { track ->
                putExtra(EXTRA_TRACK_NAME, track.mediaMetadata?.title?.toString())
                putExtra(EXTRA_TRACK_ARTIST, track.mediaMetadata?.artist?.toString())
            }
        })
    }
    
    private fun broadcastError(message: String) {
        sendBroadcast(Intent(ACTION_ERROR_OCCURRED).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        })
    }
    
    private fun broadcastDamageWarning(damageResult: PlaybackResult.CorruptedButPlaying) {
        sendBroadcast(Intent(ACTION_ERROR_OCCURRED).apply {
            putExtra(EXTRA_ERROR_MESSAGE, damageResult.message)
            putExtra(EXTRA_DAMAGE_PERCENT, damageResult.damagePercent)
        })
    }
    
    private fun broadcastRecoveryProgress(progress: Float) {
        sendBroadcast(Intent(ACTION_RECOVERY_PROGRESS).apply {
            putExtra(EXTRA_RECOVERY_PROGRESS, progress)
        })
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(notificationReceiver)
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        audioFocusRequest?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager?.abandonAudioFocusRequest(it)
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
