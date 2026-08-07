package com.nexus.player.ui.screen

import android.Manifest
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.nexus.player.NexusApplication
import com.nexus.player.data.model.MediaItem
import com.nexus.player.player.service.CyberPlayerService
import com.nexus.player.ui.components.*
import com.nexus.player.ui.state.MediaTab
import com.nexus.player.ui.state.PlayerUiState
import com.nexus.player.ui.state.RepeatMode
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors
import com.nexus.player.ui.viewmodel.MainViewModel
import com.nexus.player.ui.viewmodel.viewModelFactory
import com.nexus.player.utils.findActivity
import kotlinx.coroutines.delay
import java.util.ArrayList

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain(viewModel: MainViewModel = viewModel(factory = viewModelFactory())) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, state.isFullScreen) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    var showControls by remember { mutableStateOf(true) }

    fun onUserInteraction() {
        showControls = true
    }

    fun startPlayback(item: MediaItem, customQueue: List<MediaItem>? = null) {
        onUserInteraction()
        viewModel.setCurrentTrack(item)
        viewModel.showPlayer()
        
        val player = (context.applicationContext as? NexusApplication)?.exoPlayer
        val queue = customQueue ?: if (item.isVideo) state.videoItems else state.audioItems
        viewModel.setCurrentQueue(queue)
        
        if (player != null && queue.isNotEmpty()) {
            val media3Items = queue.map { 
                androidx.media3.common.MediaItem.fromUri(it.uri) 
            }
            val startIndex = queue.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            
            player.shuffleModeEnabled = state.isShuffle
            player.repeatMode = when (state.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }

            val currentMediaId = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMediaId != item.uri.toString() || player.mediaItemCount == 0) {
                player.setMediaItems(media3Items, startIndex, 0L)
                player.prepare()
            }
            player.play()
            
            viewModel.onPlaybackStateChanged(true, player.currentPosition, if (player.duration > 0) player.duration else item.duration)
        } else {
            viewModel.onPlaybackStateChanged(true, 0L, item.duration)
        }

        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_PLAY
            putExtra(CyberPlayerService.EXTRA_FILE_URI, item.uri.toString())
            putStringArrayListExtra("EXTRA_FILE_URI_LIST", ArrayList(queue.map { it.uri.toString() }))
        }
        try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        onUserInteraction()
        if (state.currentTrack == null) {
            val items = if (state.currentQueue.isNotEmpty()) state.currentQueue else if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
            items.firstOrNull()?.let { startPlayback(it, null) }
            return
        }
        
        val player = (context.applicationContext as? NexusApplication)?.exoPlayer
        val willPlay = if (player != null) !player.isPlaying else !state.isPlaying

        if (player != null) {
            if (willPlay) {
                player.play()
            } else {
                player.pause()
            }
            viewModel.onPlaybackStateChanged(willPlay, player.currentPosition, if (player.duration > 0) player.duration else state.duration)
        } else {
            viewModel.onPlaybackStateChanged(willPlay, state.currentPosition, state.duration)
        }

        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = if (willPlay) CyberPlayerService.ACTION_PLAY else CyberPlayerService.ACTION_PAUSE
        }
        try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
    }

    fun playNext(autoTriggered: Boolean = false) {
        onUserInteraction()
        val player = (context.applicationContext as? NexusApplication)?.exoPlayer
        val items = if (state.currentQueue.isNotEmpty()) state.currentQueue else if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.uri == state.currentTrack?.uri }

        if (autoTriggered && state.repeatMode == RepeatMode.OFF && !state.isShuffle && idx >= items.size - 1) {
            player?.pause()
            player?.seekTo(0)
            viewModel.onPlaybackStateChanged(false, 0L, state.duration)
            val intent = Intent(context, CyberPlayerService::class.java).apply {
                action = CyberPlayerService.ACTION_PAUSE
            }
            try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
            return
        }

        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
            val currentMedia = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMedia != null) {
                viewModel.onTrackChanged(currentMedia)
                val nextItem = items.find { it.uri.toString() == currentMedia }
                if (nextItem != null) {
                    val intent = Intent(context, CyberPlayerService::class.java).apply {
                        action = CyberPlayerService.ACTION_PLAY
                        putExtra(CyberPlayerService.EXTRA_FILE_URI, nextItem.uri.toString())
                        putStringArrayListExtra("EXTRA_FILE_URI_LIST", ArrayList(items.map { it.uri.toString() }))
                    }
                    try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
                }
            }
            return
        }

        val nextIdx = when {
            state.isShuffle -> items.indices.random()
            state.repeatMode == RepeatMode.ONE -> idx
            idx < items.size - 1 -> idx + 1
            else -> 0
        }
        startPlayback(items[nextIdx], if (state.currentQueue.isNotEmpty()) state.currentQueue else null)
    }

    fun playPrevious() {
        onUserInteraction()
        val player = (context.applicationContext as? NexusApplication)?.exoPlayer
        val items = if (state.currentQueue.isNotEmpty()) state.currentQueue else if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.uri == state.currentTrack?.uri }

        if (player != null && player.hasPreviousMediaItem() && player.currentPosition < 3000L) {
            player.seekToPreviousMediaItem()
            player.play()
            val currentMedia = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMedia != null) {
                viewModel.onTrackChanged(currentMedia)
                val prevItem = items.find { it.uri.toString() == currentMedia }
                if (prevItem != null) {
                    val intent = Intent(context, CyberPlayerService::class.java).apply {
                        action = CyberPlayerService.ACTION_PLAY
                        putExtra(CyberPlayerService.EXTRA_FILE_URI, prevItem.uri.toString())
                        putStringArrayListExtra("EXTRA_FILE_URI_LIST", ArrayList(items.map { it.uri.toString() }))
                    }
                    try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
                }
            }
            return
        } else if (player != null && player.currentPosition >= 3000L) {
            player.seekTo(0)
            return
        }

        val prevIdx = when {
            state.isShuffle -> items.indices.random()
            state.repeatMode == RepeatMode.ONE -> idx
            idx > 0 -> idx - 1
            else -> items.size - 1
        }
        startPlayback(items[prevIdx], if (state.currentQueue.isNotEmpty()) state.currentQueue else null)
    }

    fun performSeek(position: Long) {
        onUserInteraction()
        val safePos = position.coerceIn(0L, if (state.duration > 0) state.duration else Long.MAX_VALUE)
        val player = (context.applicationContext as? NexusApplication)?.exoPlayer
        player?.seekTo(safePos)
        viewModel.onPositionUpdated(safePos, state.duration)
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SEEK_TO
            putExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, safePos)
        }
        try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
    }

    fun applyPreset(preset: String) {
        onUserInteraction()
        viewModel.setPreset(preset)
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SET_EQUALIZER
            putExtra(CyberPlayerService.EXTRA_EQUALIZER_PRESET, preset)
        }
        try { ContextCompat.startForegroundService(context, intent) } catch (_: Exception) {}
    }

    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val act = context.findActivity() ?: return
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                try {
                    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                    act.enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val exoPlayer = remember(context) { (context.applicationContext as? NexusApplication)?.exoPlayer }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val uri = mediaItem?.localConfiguration?.uri?.toString()
                if (uri != null) {
                    viewModel.onTrackChanged(uri)
                    val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: state.duration
                    viewModel.onPlaybackStateChanged(exoPlayer?.isPlaying == true, exoPlayer?.currentPosition ?: 0L, dur)
                    
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                        val intent = Intent(context, CyberPlayerService::class.java).apply {
                            action = CyberPlayerService.ACTION_TRACK_CHANGED
                            putExtra(CyberPlayerService.EXTRA_FILE_URI, uri)
                        }
                        try { context.startService(intent) } catch (_: Exception) {}
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.onPlaybackStateChanged(false, exoPlayer?.currentPosition ?: 0L, exoPlayer?.duration ?: 0L)
                    playNext(autoTriggered = true)
                } else if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: state.duration
                    viewModel.onPlaybackStateChanged(exoPlayer?.isPlaying == true, exoPlayer?.currentPosition ?: 0L, dur)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: state.duration
                viewModel.onPlaybackStateChanged(isPlaying, exoPlayer?.currentPosition ?: 0L, dur)
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
        }
    }

    LaunchedEffect(state.isPlaying, exoPlayer) {
        while (state.isPlaying && exoPlayer != null) {
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            if (duration > 0 && abs(currentPos - state.currentPosition) > 250) {
                viewModel.onPositionUpdated(currentPos, duration)
            }
            delay(250)
        }
    }

    LaunchedEffect(state.gestureIndicator) {
        if (state.gestureIndicator != null) {
            delay(1200)
            viewModel.hideGestureIndicator()
        }
    }

    LaunchedEffect(showControls, state.isPlaying, state.showPlaylist) {
        if (showControls && state.isPlaying && !state.showPlaylist) {
            delay(5000)
            showControls = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.READ_MEDIA_AUDIO] == true || perms[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) viewModel.startMediaScan() else viewModel.setLoading(false)
    }

    LaunchedEffect(Unit) {
        val perms = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(perms)
        } else {
            viewModel.startMediaScan()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED -> viewModel.onPlaybackStateChanged(
                        intent.getBooleanExtra(CyberPlayerService.EXTRA_IS_PLAYING, false),
                        intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L),
                        intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L)
                    )
                    CyberPlayerService.ACTION_POSITION_UPDATED -> viewModel.onPositionUpdated(
                        intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L),
                        intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L)
                    )
                    CyberPlayerService.ACTION_TRACK_CHANGED -> viewModel.onTrackChanged(
                        intent.getStringExtra(CyberPlayerService.EXTRA_FILE_URI)
                            ?: intent.getStringExtra(CyberPlayerService.EXTRA_FILE_PATH)
                    )
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED)
            addAction(CyberPlayerService.ACTION_POSITION_UPDATED)
            addAction(CyberPlayerService.ACTION_TRACK_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!state.isFullScreen) ParticleBackground()

        Column(
            Modifier
                .fillMaxSize()
                .padding(if (state.isFullScreen) 0.dp else 16.dp)
                .padding(bottom = if (!state.isFullScreen && (showControls || !state.isPlaying || state.showPlaylist)) 130.dp else 0.dp)
                .systemBarsPadding()
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !state.isFullScreen && (showControls || !state.isPlaying || state.showPlaylist),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        NeonGradientText("NEXUS", fontSize = 32.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.startMediaScan(forceReload = true) }) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = if (state.isScanning) NexusColors.NeonPink else NexusColors.Cyan)
                            }
                            IconButton(onClick = { viewModel.togglePlaylist() }) {
                                Icon(if (state.showPlaylist) Icons.Default.GraphicEq else Icons.Default.QueueMusic, "Playlist", tint = if (state.showPlaylist) NexusColors.NeonPink else NexusColors.Cyan)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            AnimatedContent(
                targetState = state.showPlaylist,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 })
                        .togetherWith(fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 })
                },
                label = "PlaylistTransition",
                modifier = Modifier.weight(1f)
            ) { show ->
                if (show) {
                    PlaylistView(state, viewModel, ::startPlayback)
                } else {
                    PlayerView(
                        state = state,
                        viewModel = viewModel,
                        showControls = showControls,
                        onInteraction = { onUserInteraction() },
                        onToggleControls = { showControls = !showControls },
                        onPlay = ::startPlayback,
                        onTogglePlayPause = ::togglePlayPause,
                        onNext = { playNext() },
                        onPrevious = ::playPrevious,
                        onPreset = ::applyPreset,
                        onSeek = { pos -> performSeek(pos) },
                        onPiP = ::enterPiP,
                        onSpeed = { 
                            onUserInteraction()
                            viewModel.cyclePlaybackSpeed(context) 
                        },
                        onShuffle = { 
                            onUserInteraction()
                            viewModel.toggleShuffle(context) 
                        },
                        onRepeat = { 
                            onUserInteraction()
                            viewModel.toggleRepeat(context) 
                        }
                    )
                }
            }
        }

        if (!state.isFullScreen) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls || !state.isPlaying || state.showPlaylist,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            ) {
                GlassMorphicPanel(
                    Modifier.fillMaxWidth(),
                    state.isPlaying,
                    state.currentPosition,
                    if (state.duration > 0) state.duration else (state.currentTrack?.duration ?: 0L),
                    { togglePlayPause() },
                    { playNext() },
                    { playPrevious() },
                    { 
                        onUserInteraction()
                        viewModel.toggleFullScreen() 
                    },
                    onSeek = { performSeek(it) },
                    onPiP = { enterPiP() },
                    onQueue = { 
                        onUserInteraction()
                        viewModel.toggleQueue() 
                    }
                )
            }
        }

        state.gestureIndicator?.let { text ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.border(2.dp, NexusColors.Cyan, RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = text,
                        color = NexusColors.Cyan,
                        fontSize = 26.sp,
                        fontFamily = CyberpunkFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (state.showQueue) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 135.dp)) {
                QueuePanel(state, ::startPlayback)
            }
        }

        if (state.showCreatePlaylistDialog) {
            var playlistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.showCreatePlaylistDialog(false) },
                containerColor = Color.Black.copy(alpha = 0.9f),
                title = { Text("НОВЫЙ ПЛЕЙЛИСТ", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily) },
                text = {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("Имя (напр. CYBER RUN)", color = Color.Gray, fontFamily = CyberpunkFontFamily) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NexusColors.NeonPink, unfocusedBorderColor = NexusColors.Cyan, cursorColor = NexusColors.NeonPink),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontFamily = CyberpunkFontFamily)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.createPlaylist(playlistName) }) {
                        Text("СОЗДАТЬ", color = NexusColors.NeonPink, fontFamily = CyberpunkFontFamily, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showCreatePlaylistDialog(false) }) {
                        Text("ОТМЕНА", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily)
                    }
                }
            )
        }

        state.trackToAddUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { viewModel.openAddToPlaylistMenu(null) },
                containerColor = Color.Black.copy(alpha = 0.95f),
                title = { Text("ДОБАВИТЬ В ПЛЕЙЛИСТ", color = NexusColors.NeonPink, fontFamily = CyberpunkFontFamily) },
                text = {
                    if (state.customPlaylists.isEmpty()) {
                        Text("Сначала создайте плейлист во вкладке 'ПЛЕЙЛИСТЫ'", color = Color.White, fontFamily = CyberpunkFontFamily)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                            items(state.customPlaylists) { playlist ->
                                val isAdded = playlist.trackUris.contains(uri)
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (isAdded) viewModel.removeTrackFromPlaylist(playlist.id, uri)
                                        else viewModel.addTrackToPlaylist(playlist.id, uri)
                                    }.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(playlist.name, color = Color.White, fontFamily = CyberpunkFontFamily, fontSize = 16.sp)
                                    Icon(
                                        if (isAdded) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = if (isAdded) NexusColors.NeonGreen else NexusColors.Cyan
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.openAddToPlaylistMenu(null) }) {
                        Text("ГОТОВО", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
