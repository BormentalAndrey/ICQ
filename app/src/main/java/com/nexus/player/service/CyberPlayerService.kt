package com.nexus.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nexus.player.R
import com.nexus.player.data.local.PreferencesManager
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.di.AppModule
import com.nexus.player.player.audio.EqualizerEngine
import com.nexus.player.player.core.CorruptedFileHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class CyberPlayerService : MediaSessionService() {
    
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
        const val ACTION_ERROR_OCCURRED = "com.nexus.player.ERROR_OCCURRED"
        const val ACTION_RECOVERY_PROGRESS = "com.nexus.player.RECOVERY_PROGRESS"
        
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
    }
    
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    
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
        createNotificationChannels()
        
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
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
        
        initializePlayer()
        initializeMediaSession()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val playbackChannel = NotificationChannel(
                CHANNEL_ID,
                "Cyberpunk Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал управления медиаплеером Nexus"
                setShowBadge(false)
            }
            
            val errorChannel = NotificationChannel(
                ERROR_CHANNEL_ID,
                "Cyberpunk Errors & Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ошибки декодирования и повреждения файлов"
                enableLights(true)
                lightColor = Color.RED
            }
            
            notificationManager.createNotificationChannels(listOf(playbackChannel, errorChannel))
        }
    }
    
    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
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
                    }
                    
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentTrack.value = mediaItem
                        mediaItem?.localConfiguration?.uri?.path?.let { path ->
                            currentFilePath = path
                        }
                        updateNotification()
                        broadcastTrackChanged()
                    }
                })
                playWhenReady = false
            }
        
        startPositionTracking()
        
        serviceScope.launch {
            preferencesManager.equalizerBands
                .catch { e -> Log.e("CyberPlayerService", "Error loading EQ bands", e) }
                .collect { bands ->
                    equalizerEngine.applyBands(bands)
                }
        }
    }
    
    private fun initializeMediaSession() {
        player?.let { exoPlayer ->
            val sessionCallback = object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_SET_EQUALIZER -> {
                            val preset = args.getString(EXTRA_EQUALIZER_PRESET)
                            val bands = args.getFloatArray(EXTRA_EQUALIZER_BANDS)
                            if (preset != null) {
                                equalizerEngine.applyPreset(preset)
                            } else if (bands != null) {
                                equalizerEngine.applyBands(bands.toList())
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            
            mediaSession = MediaSession.Builder(this, exoPlayer)
                .setCallback(sessionCallback)
                .build()
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
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
        super.onStartCommand(intent, flags, startId)
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
        
        val metadata = MediaMetadata.Builder()
            .setTitle(filePath.substringAfterLast("/"))
            .setArtist("Cyberpunk Stream")
            .build()
        
        val uriString = if (filePath.startsWith("http://") || 
                            filePath.startsWith("https://") || 
                            filePath.startsWith("content://") || 
                            filePath.startsWith("file://")) {
            filePath
        } else {
            "file://$filePath"
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uriString)
            .setMediaMetadata(metadata)
            .build()
            
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
        
        AppModule.provideMediaRepository().repairStream(filePath)
            .catch { e ->
                val error = PlaybackResult.FatalError(
                    throwable = e,
                    userMessage = "Ошибка при восстановлении файла"
                )
                _playbackState.value = error
                broadcastError(error.userMessage)
            }
            .collect { result ->
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
                    player?.apply { 
                        val nextPos = (currentPosition + 1000L).coerceAtMost(duration.coerceAtLeast(0L))
                        seekTo(nextPos)
                        playWhenReady = true 
                    }
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
        player?.apply {
            if (hasNextMediaItem()) {
                seekToNextMediaItem()
            } else {
                seekTo(0)
            }
            play()
        }
        broadcastTrackChanged()
    }
    
    private fun playPrevious() {
        player?.apply {
            if (hasPreviousMediaItem()) {
                seekToPreviousMediaItem()
            } else {
                seekTo(0)
            }
            play()
        }
        broadcastTrackChanged()
    }
    
    private fun updateNotification() {
        val notification = buildNotification()
        val isPlayingOrBuffering = player?.let { 
            it.isPlaying || it.playbackState == Player.STATE_BUFFERING 
        } ?: false

        if (isPlayingOrBuffering) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        }
    }
    
    private fun buildNotification(): Notification {
        val isPlaying = player?.isPlaying ?: false
        val currentTrack = _currentTrack.value
        val session = mediaSession ?: return NotificationCompat.Builder(this, CHANNEL_ID).build()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack?.mediaMetadata?.title ?: "NEXUS PLAYER")
            .setContentText(currentTrack?.mediaMetadata?.artist ?: "Кибернетический поток активен")
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
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .apply {
                player?.let { p ->
                    val duration = p.duration
                    val position = p.currentPosition
                    if (duration != C.TIME_UNSET && duration > 0) {
                        val safeDuration = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val safePosition = position.coerceIn(0L, duration).toInt()
                        setProgress(safeDuration, safePosition, false)
                    } else {
                        setProgress(0, 0, false)
                    }
                }
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + 2, notification)
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, CyberPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    
    private fun sendNexusBroadcast(action: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        extras?.invoke(intent)
        sendBroadcast(intent)
    }
    
    private fun broadcastPlaybackState() {
        sendNexusBroadcast(ACTION_PLAYBACK_STATE_CHANGED) {
            putExtra(EXTRA_IS_PLAYING, _isPlaying.value)
            putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
            player?.let { putExtra(EXTRA_DURATION, it.duration) }
        }
    }
    
    private fun broadcastPositionUpdate() {
        sendNexusBroadcast(ACTION_POSITION_UPDATED) {
            putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value)
            player?.let { putExtra(EXTRA_DURATION, it.duration) }
        }
    }
    
    private fun broadcastTrackChanged() {
        sendNexusBroadcast(ACTION_TRACK_CHANGED) {
            currentFilePath?.let { putExtra(EXTRA_FILE_PATH, it) }
            _currentTrack.value?.let { track ->
                putExtra(EXTRA_TRACK_NAME, track.mediaMetadata.title?.toString())
                putExtra(EXTRA_TRACK_ARTIST, track.mediaMetadata.artist?.toString())
            }
        }
    }
    
    private fun broadcastError(message: String) {
        sendNexusBroadcast(ACTION_ERROR_OCCURRED) {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
    }
    
    private fun broadcastDamageWarning(damageResult: PlaybackResult.CorruptedButPlaying) {
        sendNexusBroadcast(ACTION_ERROR_OCCURRED) {
            putExtra(EXTRA_ERROR_MESSAGE, damageResult.message)
            putExtra(EXTRA_DAMAGE_PERCENT, damageResult.damagePercent)
        }
    }
    
    private fun broadcastRecoveryProgress(progress: Float) {
        sendNexusBroadcast(ACTION_RECOVERY_PROGRESS) {
            putExtra(EXTRA_RECOVERY_PROGRESS, progress)
        }
    }
    
    override fun onDestroy() {
        serviceScope.cancel()
        unregisterReceiver(notificationReceiver)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
