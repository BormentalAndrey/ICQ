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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.nexus.player.NexusApplication
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.repository.MediaRepository
import com.nexus.player.di.AppModule
import com.nexus.player.player.service.CyberPlayerService
import com.nexus.player.ui.components.*
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MediaTab { AUDIO, VIDEO }

data class PlayerUiState(
    val audioItems: List<MediaItem> = emptyList(),
    val videoItems: List<MediaItem> = emptyList(),
    val isPlaying: Boolean = false,
    val currentTrack: MediaItem? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val showPlaylist: Boolean = false,
    val selectedTab: MediaTab = MediaTab.AUDIO,
    val selectedPreset: String = "Flat",
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val pendingTrackUri: String? = null,
    val isFullScreen: Boolean = false,
    val gestureIndicator: String? = null,
    val showQueue: Boolean = false
)

class MainViewModel(
    private val repository: MediaRepository = AppModule.provideMediaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun startMediaScan(forceReload: Boolean = false) {
        if (!forceReload && (_uiState.value.audioItems.isNotEmpty() || _uiState.value.videoItems.isNotEmpty())) {
            _uiState.update { it.copy(isLoading = false, isScanning = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isScanning = true) }
            try {
                val all = withContext(Dispatchers.IO) { repository.loadAllMedia() }
                _uiState.update { state ->
                    val audio = all.filter { !it.isVideo }.sortedBy { it.name.lowercase() }
                    val video = all.filter { it.isVideo }.sortedBy { it.name.lowercase() }
                    val pending = state.pendingTrackUri
                    val track = if (pending != null) {
                        (audio + video).find { it.uri.toString() == pending || it.path == pending }
                    } else state.currentTrack
                    state.copy(
                        audioItems = audio, videoItems = video, currentTrack = track,
                        pendingTrackUri = null, isLoading = false, isScanning = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isScanning = false) }
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean, position: Long, duration: Long) {
        _uiState.update { it.copy(isPlaying = isPlaying, currentPosition = position, duration = duration) }
    }
    fun onPositionUpdated(position: Long, duration: Long) {
        _uiState.update { it.copy(currentPosition = position, duration = duration) }
    }
    fun onTrackChanged(trackUri: String?) {
        if (trackUri == null) return
        val state = _uiState.value
        val found = (state.audioItems + state.videoItems).find { it.uri.toString() == trackUri || it.path == trackUri }
        if (found != null) _uiState.update { it.copy(currentTrack = found, pendingTrackUri = null) }
        else _uiState.update { it.copy(pendingTrackUri = trackUri) }
    }
    fun setCurrentTrack(track: MediaItem) { _uiState.update { it.copy(currentTrack = track) } }
    fun togglePlaylist() { _uiState.update { it.copy(showPlaylist = !it.showPlaylist) } }
    fun toggleQueue() { _uiState.update { it.copy(showQueue = !it.showQueue) } }
    fun selectTab(tab: MediaTab) { _uiState.update { it.copy(selectedTab = tab) } }
    fun setPreset(preset: String) { _uiState.update { it.copy(selectedPreset = preset) } }
    fun setLoading(loading: Boolean) { _uiState.update { it.copy(isLoading = loading, isScanning = loading) } }
    fun toggleFullScreen() { _uiState.update { it.copy(isFullScreen = !it.isFullScreen) } }
    fun showGestureIndicator(text: String) { _uiState.update { it.copy(gestureIndicator = text) } }
    fun hideGestureIndicator() { _uiState.update { it.copy(gestureIndicator = null) } }
}

class MainViewModelFactory(private val repository: MediaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
}

@Composable
fun viewModelFactory(): MainViewModelFactory {
    val repository = remember { AppModule.provideMediaRepository() }
    return remember { MainViewModelFactory(repository) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain(viewModel: MainViewModel = viewModel(factory = viewModelFactory())) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.READ_MEDIA_AUDIO] == true || perms[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else { perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true }
        if (granted) viewModel.startMediaScan() else viewModel.setLoading(false)
    }

    LaunchedEffect(Unit) {
        val perms = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(perms)
        else viewModel.startMediaScan()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED -> viewModel.onPlaybackStateChanged(
                        intent.getBooleanExtra(CyberPlayerService.EXTRA_IS_PLAYING, false),
                        intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L),
                        intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L))
                    CyberPlayerService.ACTION_POSITION_UPDATED -> viewModel.onPositionUpdated(
                        intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L),
                        intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L))
                    CyberPlayerService.ACTION_TRACK_CHANGED -> viewModel.onTrackChanged(
                        intent.getStringExtra(CyberPlayerService.EXTRA_FILE_URI)
                            ?: intent.getStringExtra(CyberPlayerService.EXTRA_FILE_PATH))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED)
            addAction(CyberPlayerService.ACTION_POSITION_UPDATED)
            addAction(CyberPlayerService.ACTION_TRACK_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    fun startPlayback(item: MediaItem) {
        viewModel.setCurrentTrack(item)
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_PLAY; putExtra(CyberPlayerService.EXTRA_FILE_URI, item.uri.toString())
        })
    }
    fun togglePlayPause() {
        if (state.currentTrack == null) { (if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems).firstOrNull()?.let { startPlayback(it) }; return }
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = if (state.isPlaying) CyberPlayerService.ACTION_PAUSE else CyberPlayerService.ACTION_PLAY })
    }
    fun playNext() {
        val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        startPlayback(items[(items.indexOfFirst { it.uri == state.currentTrack?.uri } + 1) % items.size])
    }
    fun playPrevious() {
        val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.uri == state.currentTrack?.uri }
        startPlayback(items[if (idx <= 0) items.size - 1 else idx - 1])
    }
    fun seekTo(position: Long) {
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SEEK_TO; putExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, position)
        })
    }
    fun applyPreset(preset: String) {
        viewModel.setPreset(preset)
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SET_EQUALIZER; putExtra(CyberPlayerService.EXTRA_EQUALIZER_PRESET, preset)
        })
    }
    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? android.app.Activity
            activity?.enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!state.isFullScreen) ParticleBackground()

        Column(Modifier.fillMaxSize().padding(if (state.isFullScreen) 0.dp else 16.dp).systemBarsPadding()) {
            if (!state.isFullScreen) {
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
                if (!state.showPlaylist) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("🎵 ${state.audioItems.size}", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("🎬 ${state.videoItems.size}", color = NexusColors.Purple, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            AnimatedContent(targetState = state.showPlaylist, transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 })
                    .togetherWith(fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 })
            }, label = "PlaylistTransition") { show ->
                if (show) {
                    PlaylistView(state, viewModel, ::startPlayback)
                } else {
                    PlayerView(state, viewModel, ::startPlayback, ::togglePlayPause, ::playNext, ::playPrevious, ::applyPreset, ::seekTo, ::enterPiP)
                }
            }
        }

        if (!state.isFullScreen) {
            GlassMorphicPanel(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                state.isPlaying, state.currentPosition,
                if (state.duration > 0) state.duration else (state.currentTrack?.duration ?: 0L),
                { togglePlayPause() }, { playNext() }, { playPrevious() },
                { viewModel.toggleFullScreen() },
                onSeek = { viewModel.seekTo(it) },
                onPiP = { enterPiP() },
                onQueue = { viewModel.toggleQueue() }
            )
        }

        state.gestureIndicator?.let { text ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(text, color = NexusColors.Cyan, fontSize = 48.sp, fontFamily = CyberpunkFontFamily, fontWeight = FontWeight.Bold)
            }
        }

        // Queue panel
        AnimatedVisibility(
            visible = state.showQueue,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(300)) { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
        ) {
            QueuePanel(state, ::startPlayback)
        }
    }
}

@Composable
private fun PlaylistView(state: PlayerUiState, viewModel: MainViewModel, onPlay: (MediaItem) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(state.selectedTab == MediaTab.AUDIO, { viewModel.selectTab(MediaTab.AUDIO) }, {
                Text("🎵 АУДИО (${state.audioItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily)
            }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f), selectedLabelColor = NexusColors.NeonPink))
            Spacer(Modifier.width(8.dp))
            FilterChip(state.selectedTab == MediaTab.VIDEO, { viewModel.selectTab(MediaTab.VIDEO) }, {
                Text("🎬 ВИДЕО (${state.videoItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily)
            }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.Purple.copy(alpha = 0.3f), selectedLabelColor = NexusColors.Purple))
        }
        Spacer(Modifier.height(8.dp))
        if (state.isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NexusColors.NeonPink) }
        else {
            val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("НЕТ ФАЙЛОВ", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily) }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
                items(items, key = { "${it.id}_${it.isVideo}" }) { item ->
                    MediaItemRow(item, state.currentTrack?.uri == item.uri && state.isPlaying) { onPlay(item) }
                }
            }
        }
    }
}

@Composable
private fun PlayerView(
    state: PlayerUiState, viewModel: MainViewModel, onPlay: (MediaItem) -> Unit,
    onTogglePlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onPreset: (String) -> Unit, onSeek: (Long) -> Unit, onPiP: () -> Unit
) {
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var brightnessAccumulator by remember { mutableFloatStateOf(0.5f) }
    var volumeAccumulator by remember { mutableFloatStateOf(0.5f) }

    Column(
        Modifier.fillMaxSize().weight(1f).then(if (state.isFullScreen) Modifier.background(Color.Black).pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val w = size.width.toFloat(); val h = size.height.toFloat()
                when {
                    abs(dragAmount.x) > abs(dragAmount.y) -> {
                        seekAccumulator += dragAmount.x / w * (state.duration / 1000f)
                        val s = seekAccumulator.roundToInt()
                        if (abs(s) > 0) {
                            onSeek((state.currentPosition + s * 1000L).coerceIn(0, state.duration))
                            viewModel.showGestureIndicator(if (s > 0) "+${s}s" else "${s}s"); seekAccumulator = 0f
                        }
                    }
                    change.position.x < w / 2 -> {
                        brightnessAccumulator = (brightnessAccumulator - dragAmount.y / h).coerceIn(0.01f, 1f)
                        viewModel.showGestureIndicator("☀ ${(brightnessAccumulator * 100).toInt()}%")
                    }
                    else -> {
                        volumeAccumulator = (volumeAccumulator - dragAmount.y / h).coerceIn(0f, 1f)
                        viewModel.showGestureIndicator("🔊 ${(volumeAccumulator * 100).toInt()}%")
                    }
                }
            }
        } else Modifier),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.currentTrack?.isVideo == true) {
            Box(Modifier.fillMaxWidth().then(if (state.isFullScreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
                AndroidView(factory = { ctx ->
                    PlayerView(ctx).apply { player = (ctx.applicationContext as NexusApplication).exoPlayer; useController = false }
                }, modifier = Modifier.fillMaxSize())
                if (state.isFullScreen) {
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)), modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                        FullScreenControls(state, onTogglePlayPause, onNext, onPrevious, { viewModel.toggleFullScreen() }, onPiP)
                    }
                }
            }
        } else {
            GlitchArtWork(Modifier.size(280.dp), state.currentTrack?.albumArtUri, state.isPlaying, false)
        }
        if (!state.isFullScreen) {
            Spacer(Modifier.height(32.dp))
            state.currentTrack?.let {
                NeonText(it.name, fontSize = 22.sp, color = NexusColors.Cyan)
                Spacer(Modifier.height(4.dp))
                Text("${it.artist} — ${it.album}", color = NexusColors.NeonPink, fontSize = 14.sp, fontFamily = CyberpunkFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } ?: run {
                NeonText("NEXUS PLAYER", fontSize = 28.sp, color = NexusColors.Cyan)
                Text("🎵${state.audioItems.size} аудио • 🎬${state.videoItems.size} видео", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
            }
            if (state.currentTrack?.isVideo != true) {
                Spacer(Modifier.height(24.dp))
                SpectrumVisualizer(Modifier.fillMaxWidth().height(120.dp), FloatArray(64) { kotlin.random.Random.nextFloat() }, state.isPlaying)
                Spacer(Modifier.height(16.dp))
                Text("ЭКВАЛАЙЗЕР", color = NexusColors.Cyan.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Flat", "Кибер", "Техно", "Акустика").forEach { preset ->
                        FilterChip(state.selectedPreset == preset, { onPreset(preset) }, { Text(preset, fontSize = 10.sp, fontFamily = CyberpunkFontFamily) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f), selectedLabelColor = NexusColors.NeonPink))
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenControls(state: PlayerUiState, onTogglePlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onExitFullScreen: () -> Unit, onPiP: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.SkipPrevious, "Prev", tint = NexusColors.Cyan, modifier = Modifier.size(28.dp)) }
        Box(Modifier.size(60.dp).clip(CircleShape).background(NexusColors.NeonPink).clickable { onTogglePlayPause() }, contentAlignment = Alignment.Center) {
            Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.SkipNext, "Next", tint = NexusColors.Cyan, modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onPiP, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.PictureInPicture, "PiP", tint = NexusColors.NeonGreen, modifier = Modifier.size(24.dp)) }
        IconButton(onClick = onExitFullScreen, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.FullscreenExit, "Exit", tint = NexusColors.Purple, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun QueuePanel(state: PlayerUiState, onPlay: (MediaItem) -> Unit) {
    Card(Modifier.fillMaxWidth().height(300.dp).padding(16.dp), colors = CardDefaults.cardColors(containerColor = NexusColors.DarkGrey.copy(alpha = 0.95f)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("📋 ОЧЕРЕДЬ", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
                items(items.take(20)) { item ->
                    Text(
                        "${item.name} • ${item.artist}",
                        color = if (item.uri == state.currentTrack?.uri) NexusColors.NeonPink else NexusColors.White,
                        fontFamily = CyberpunkFontFamily, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 6.dp).clickable { onPlay(item) },
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MediaItemRow(item: MediaItem, isPlaying: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = if (isPlaying) NexusColors.NeonPink.copy(alpha = 0.2f) else NexusColors.GlassBlack), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(if (item.isVideo) listOf(NexusColors.Purple, NexusColors.BloodRed) else listOf(NexusColors.Purple, NexusColors.Cyan))), contentAlignment = Alignment.Center) {
                Icon(if (item.isVideo) Icons.Default.VideoFile else if (isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = if (isPlaying) NexusColors.NeonPink else NexusColors.White, fontFamily = CyberpunkFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = NexusColors.White.copy(alpha = 0.6f), fontFamily = CyberpunkFontFamily)
            }
            Text(item.formattedDuration, style = MaterialTheme.typography.bodySmall, color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily)
        }
    }
}
