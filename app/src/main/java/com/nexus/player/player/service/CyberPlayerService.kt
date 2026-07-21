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

        const val EXTRA_FILE_URI = "FILE_URI"
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
    private val _currentPosition = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _currentTrack = MutableStateFlow<MediaItem?>(null)
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
        Log.d("NEXUS_SERVICE", "onCreate")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nexus:WakeLock")
        createNotificationChannels()
        registerNotificationReceiver()
        initializePlayer()
        initializeMediaSession()
        startForegroundService()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {}

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
            .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d("NEXUS_PLAYER", "PlaybackState: $state")
                        if (state == Player.STATE_READY) { _isPlaying.value = this@apply.isPlaying; updateNotification(); broadcastPlaybackState() }
                        if (state == Player.STATE_ENDED) playNext()
                    }
                    override fun onPlayerError(error: PlaybackException) { Log.e("NEXUS_PLAYER", "Player error: ${error.errorCodeName}", error); handlePlaybackError(error) }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("NEXUS_PLAYER", "isPlayingChanged: $isPlaying")
                        _isPlaying.value = isPlaying; updateNotification(); broadcastPlaybackState()
                        if (isPlaying) wakeLock?.acquire(3600000)
                        else safeReleaseWakeLock()
                    }
                })
            }
        startPositionTracking()
        serviceScope.launch { preferencesManager.equalizerBands.collect { equalizerEngine.applyBands(it) } }
    }

    private fun safeReleaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e("NEXUS_PLAYER", "WakeLock release error", e)
        }
    }

    private fun startPositionTracking() {
        serviceScope.launch {
            while (isActive) { player?.let { if (it.isPlaying) { _currentPosition.value = it.currentPosition; broadcastPositionUpdate() } }; delay(250) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_FILE_URI) ?: intent.getStringExtra(EXTRA_FILE_PATH)
                if (uri != null) serviceScope.launch { playFile(uri, intent.getLongExtra(EXTRA_START_POSITION, 0L)) }
                else player?.playWhenReady = true
            }
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private suspend fun playFile(uriString: String, startPosition: Long = 0L) = withContext(Dispatchers.Main) {
        _playbackState.value = PlaybackResult.Success
        val mediaUri = when {
            uriString.startsWith("content://") -> Uri.parse(uriString)
            uriString.startsWith("/") -> Uri.parse("file://$uriString")
            else -> Uri.parse(uriString)
        }
        currentMediaUri = mediaUri
        Log.d("NEXUS_PLAYER", "Playing: $mediaUri")

        if (!uriString.startsWith("content://")) {
            val damage = corruptedFileHandler.analyzeDamage(uriString)
            _playbackState.value = damage
            when (damage) {
                is PlaybackResult.FatalError -> {
                    broadcastError(damage.userMessage)
                    if (damage.canAttemptRecovery) { repairAndPlay(uriString, startPosition); return@withContext }
                    else { showErrorNotification(damage.userMessage); return@withContext }
                }
                is PlaybackResult.CorruptedButPlaying -> { showDamageNotification(damage); broadcastDamageWarning(damage) }
                else -> {}
            }
        }

        acquireAudioFocus()
        player?.apply {
            stop()
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            if (startPosition > 0) seekTo(startPosition)
            playWhenReady = true
        }
        wakeLock?.acquire(3600000)
        _currentTrack.value = MediaItem.fromUri(mediaUri)
        updateNotification(); broadcastTrackChanged()
    }

    private suspend fun repairAndPlay(uriString: String, startPosition: Long) {
        _playbackState.value = PlaybackResult.RecoveryInProgress(0f); broadcastRecoveryProgress(0f)
        when (val r = AppModule.provideMediaRepository().repairStream(uriString)) {
            is PlaybackResult.RecoveryComplete -> if (r.success && r.recoveredPath != null) playFile(r.recoveredPath, startPosition)
            else { _playbackState.value = PlaybackResult.FatalError(Exception("Recovery failed"), userMessage = "Не удалось восстановить"); broadcastError("Не удалось восстановить") }
            else -> {}
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        Log.e("NEXUS_PLAYER", "Playback error: ${error.errorCodeName}", error)
        _playbackState.value = when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                PlaybackResult.FatalError(throwable = error, userMessage = "Файл не найден или поврежден")
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                serviceScope.launch { delay(100); player?.apply { seekTo(currentPosition + 1000); playWhenReady = true } }
                PlaybackResult.CorruptedButPlaying(damagePercent = 50f, message = "Ошибка декодирования")
            }
            else -> PlaybackResult.FatalError(throwable = error, userMessage = "Неизвестная ошибка")
        }
        updateNotification()
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
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.2f
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
        .setContentTitle(_currentTrack.value?.mediaMetadata?.title?.toString() ?: "NEXUS PLAYER")
        .setContentText(_currentTrack.value?.mediaMetadata?.artist?.toString() ?: "Кибернетический поток активен")
        .setSmallIcon(R.drawable.ic_neon_skull).setColor(Color.parseColor("#FF007F")).setOngoing(player?.isPlaying ?: false)
        .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .addAction(R.drawable.ic_previous, "Prev", createPI(ACTION_PREVIOUS))
        .addAction(if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play, "Play/Pause", createPI(if (player?.isPlaying == true) ACTION_PAUSE else ACTION_PLAY))
        .addAction(R.drawable.ic_next, "Next", createPI(ACTION_NEXT))
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        .apply { player?.let { if (it.duration > 0) setProgress(it.duration.toInt(), it.currentPosition.toInt(), false) else setProgress(0, 0, true) } }.build()

    private fun showErrorNotification(msg: String) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID + 1, NotificationCompat.Builder(this, ERROR_CHANNEL_ID).setContentTitle("Ошибка").setContentText(msg).setSmallIcon(R.drawable.ic_error).setColor(Color.RED).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    private fun showDamageNotification(d: PlaybackResult.CorruptedButPlaying) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID + 2, NotificationCompat.Builder(this, ERROR_CHANNEL_ID).setContentTitle("Повреждённые данные").setContentText(d.message).setSmallIcon(R.drawable.ic_warning).setColor(Color.YELLOW).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setTimeoutAfter(5000).build())
    private fun createPI(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, CyberPlayerService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun broadcastPlaybackState() = sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).putExtra(EXTRA_IS_PLAYING, _isPlaying.value).putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value).also { player?.let { p -> it.putExtra(EXTRA_DURATION, p.duration) } })
    private fun broadcastPositionUpdate() = sendBroadcast(Intent(ACTION_POSITION_UPDATED).putExtra(EXTRA_CURRENT_POSITION, _currentPosition.value).also { player?.let { p -> it.putExtra(EXTRA_DURATION, p.duration) } })
    private fun broadcastTrackChanged() = sendBroadcast(Intent(ACTION_TRACK_CHANGED).apply { currentMediaUri?.toString()?.let { putExtra(EXTRA_FILE_PATH, it) } })
    private fun broadcastError(msg: String) = sendBroadcast(Intent(ACTION_ERROR_OCCURRED).putExtra(EXTRA_ERROR_MESSAGE, msg))
    private fun broadcastDamageWarning(d: PlaybackResult.CorruptedButPlaying) = sendBroadcast(Intent(ACTION_ERROR_OCCURRED).putExtra(EXTRA_ERROR_MESSAGE, d.message).putExtra(EXTRA_DAMAGE_PERCENT, d.damagePercent))
    private fun broadcastRecoveryProgress(p: Float) = sendBroadcast(Intent(ACTION_RECOVERY_PROGRESS).putExtra(EXTRA_RECOVERY_PROGRESS, p))

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ERROR_CHANNEL_ID, "Errors", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    override fun onDestroy() {
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
