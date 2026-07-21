package com.nexus.player.ui.screen

import android.Manifest
import android.app.Activity
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
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
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
enum class RepeatMode { OFF, ALL, ONE }

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
    val showQueue: Boolean = false,
    val searchQuery: String = "",
    val playbackSpeed: Float = 1.0f,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
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
                        audioItems = audio,
                        videoItems = video,
                        currentTrack = track,
                        pendingTrackUri = null,
                        isLoading = false,
                        isScanning = false
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
    fun updateSearchQuery(query: String) { _uiState.update { it.copy(searchQuery = query) } }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleRepeat() {
        _uiState.update { state ->
            val nextMode = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = nextMode)
        }
    }

    fun cyclePlaybackSpeed(context: Context) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOf(_uiState.value.playbackSpeed)
        val nextSpeed = if (currentIdx != -1) speeds[(currentIdx + 1) % speeds.size] else 1.0f
        _uiState.update { it.copy(playbackSpeed = nextSpeed) }
        showGestureIndicator("⚡ СКОРОСТЬ ${nextSpeed}x")
        try {
            val app = context.applicationContext as? NexusApplication
            app?.exoPlayer?.setPlaybackSpeed(nextSpeed)
        } catch (_: Exception) {}
    }
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

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain(viewModel: MainViewModel = viewModel(factory = viewModelFactory())) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

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

    fun startPlayback(item: MediaItem) {
        viewModel.setCurrentTrack(item)
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_PLAY
            putExtra(CyberPlayerService.EXTRA_FILE_URI, item.uri.toString())
        })
    }

    fun togglePlayPause() {
        if (state.currentTrack == null) {
            (if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems).firstOrNull()?.let { startPlayback(it) }
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = if (state.isPlaying) CyberPlayerService.ACTION_PAUSE else CyberPlayerService.ACTION_PLAY
        })
    }

    fun playNext() {
        val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.uri == state.currentTrack?.uri }
        val nextIdx = when {
            state.isShuffle -> items.indices.random()
            state.repeatMode == RepeatMode.ONE -> idx
            idx < items.size - 1 -> idx + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> 0
        }
        startPlayback(items[nextIdx])
    }

    fun playPrevious() {
        val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.uri == state.currentTrack?.uri }
        val prevIdx = when {
            state.isShuffle -> items.indices.random()
            state.repeatMode == RepeatMode.ONE -> idx
            idx > 0 -> idx - 1
            state.repeatMode == RepeatMode.ALL -> items.size - 1
            else -> items.size - 1
        }
        startPlayback(items[prevIdx])
    }

    fun performSeek(position: Long) {
        val safePos = position.coerceIn(0L, if (state.duration > 0) state.duration else Long.MAX_VALUE)
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SEEK_TO
            putExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, safePos)
        })
    }

    fun applyPreset(preset: String) {
        viewModel.setPreset(preset)
        ContextCompat.startForegroundService(context, Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SET_EQUALIZER
            putExtra(CyberPlayerService.EXTRA_EQUALIZER_PRESET, preset)
        })
    }

    fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? Activity ?: return
            val hasPipFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPipFeature) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    activity.enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                if (!state.showPlaylist) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("🎵 ${state.audioItems.size}", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                        Spacer(Modifier.width(16.dp))
                        Text("🎬 ${state.videoItems.size}", color = NexusColors.Purple, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = state.showPlaylist,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 })
                        .togetherWith(fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 2 })
                },
                label = "PlaylistTransition"
            ) { show ->
                if (show) {
                    PlaylistView(state, viewModel, ::startPlayback)
                } else {
                    PlayerView(
                        state = state,
                        viewModel = viewModel,
                        onPlay = ::startPlayback,
                        onTogglePlayPause = ::togglePlayPause,
                        onNext = ::playNext,
                        onPrevious = ::playPrevious,
                        onPreset = ::applyPreset,
                        onSeek = { pos -> performSeek(pos) },
                        onPiP = ::enterPiP,
                        onSpeed = { viewModel.cyclePlaybackSpeed(context) },
                        onShuffle = { viewModel.toggleShuffle() },
                        onRepeat = { viewModel.toggleRepeat() }
                    )
                }
            }
        }

        if (!state.isFullScreen) {
            GlassMorphicPanel(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                state.isPlaying,
                state.currentPosition,
                if (state.duration > 0) state.duration else (state.currentTrack?.duration ?: 0L),
                { togglePlayPause() },
                { playNext() },
                { playPrevious() },
                { viewModel.toggleFullScreen() },
                onSeek = { performSeek(it) },
                onPiP = { enterPiP() },
                onQueue = { viewModel.toggleQueue() }
            )
        }

        state.gestureIndicator?.let { text ->
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.border(2.dp, NexusColors.Cyan, RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = text,
                        color = NexusColors.Cyan,
                        fontSize = 32.sp,
                        fontFamily = CyberpunkFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (state.showQueue) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) {
                QueuePanel(state, ::startPlayback)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistView(state: PlayerUiState, viewModel: MainViewModel, onPlay: (MediaItem) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(
                selected = state.selectedTab == MediaTab.AUDIO,
                onClick = { viewModel.selectTab(MediaTab.AUDIO) },
                label = { Text("🎵 АУДИО (${state.audioItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f), selectedLabelColor = NexusColors.NeonPink)
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = state.selectedTab == MediaTab.VIDEO,
                onClick = { viewModel.selectTab(MediaTab.VIDEO) },
                label = { Text("🎬 ВИДЕО (${state.videoItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.Purple.copy(alpha = 0.3f), selectedLabelColor = NexusColors.Purple)
            )
        }
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            placeholder = { Text("ПОИСК В БАЗЕ NEXUS...", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NexusColors.Cyan) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = NexusColors.NeonPink)
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = NexusColors.White, fontFamily = CyberpunkFontFamily),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NexusColors.NeonPink,
                unfocusedBorderColor = NexusColors.Cyan.copy(alpha = 0.5f),
                cursorColor = NexusColors.NeonPink
            )
        )
        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusColors.NeonPink)
            }
        } else {
            val rawItems = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
            val items = if (state.searchQuery.isBlank()) rawItems else rawItems.filter {
                it.name.contains(state.searchQuery, ignoreCase = true) ||
                it.artist.contains(state.searchQuery, ignoreCase = true) ||
                it.album.contains(state.searchQuery, ignoreCase = true)
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("НИЧЕГО НЕ НАЙДЕНО", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
                    items(items, key = { "${it.id}_${it.isVideo}_${it.uri}" }) { item ->
                        MediaItemRow(item, state.currentTrack?.uri == item.uri && state.isPlaying) { onPlay(item) }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerView(
    state: PlayerUiState,
    viewModel: MainViewModel,
    onPlay: (MediaItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPreset: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onPiP: () -> Unit,
    onSpeed: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var brightnessAccumulator by remember { mutableFloatStateOf(0.5f) }
    var volumeAccumulator by remember { mutableFloatStateOf(0.5f) }

    val gestureModifier = if (state.isFullScreen) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w > 0 && h > 0) {
                    when {
                        abs(dragAmount.x) > abs(dragAmount.y) -> {
                            if (state.duration > 0) {
                                seekAccumulator += dragAmount.x / w * (state.duration / 1000f)
                                val s = seekAccumulator.roundToInt()
                                if (abs(s) > 0) {
                                    onSeek((state.currentPosition + s * 1000L).coerceIn(0, state.duration))
                                    viewModel.showGestureIndicator(if (s > 0) "⏩ +${s}с" else "⏪ ${s}с")
                                    seekAccumulator = 0f
                                }
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
            }
        }
    } else Modifier

    val doubleTapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { offset ->
                val w = size.width
                if (offset.x < w / 2) {
                    onSeek((state.currentPosition - 10000L).coerceAtLeast(0L))
                    viewModel.showGestureIndicator("⏪ -10с")
                } else {
                    onSeek((state.currentPosition + 10000L).coerceAtMost(if (state.duration > 0) state.duration else Long.MAX_VALUE))
                    viewModel.showGestureIndicator("⏩ +10с")
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (state.isFullScreen) Modifier.background(Color.Black).then(gestureModifier) else gestureModifier)
            .then(doubleTapModifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.currentTrack?.isVideo == true) {
            Box(Modifier.fillMaxWidth().then(if (state.isFullScreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = (ctx.applicationContext as NexusApplication).exoPlayer
                            useController = false
                        }
                    },
                    update = { view ->
                        view.player = (view.context.applicationContext as NexusApplication).exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.isFullScreen) {
                    Box(Modifier.align(Alignment.TopCenter)) {
                        FullScreenControls(state, onTogglePlayPause, onNext, onPrevious, { viewModel.toggleFullScreen() }, onPiP, onSpeed)
                    }
                }
            }
        } else {
            GlitchArtWork(Modifier.size(280.dp), state.currentTrack?.albumArtUri, state.isPlaying, false)
        }
        if (!state.isFullScreen) {
            Spacer(Modifier.height(24.dp))
            state.currentTrack?.let {
                NeonText(it.name, fontSize = 22.sp, color = NexusColors.Cyan)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${it.artist} — ${it.album}",
                    color = NexusColors.NeonPink,
                    fontSize = 14.sp,
                    fontFamily = CyberpunkFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: run {
                NeonText("NEXUS PLAYER", fontSize = 28.sp, color = NexusColors.Cyan)
                Text("🎵${state.audioItems.size} аудио • 🎬${state.videoItems.size} видео", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
            }
            
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.isShuffle) NexusColors.NeonPink else NexusColors.Cyan.copy(alpha = 0.5f)
                    )
                }
                FilterChip(
                    selected = state.playbackSpeed != 1.0f,
                    onClick = onSpeed,
                    label = { Text("${state.playbackSpeed}x", fontSize = 11.sp, fontFamily = CyberpunkFontFamily) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.Purple.copy(alpha = 0.4f), selectedLabelColor = NexusColors.White)
                )
                IconButton(onClick = onRepeat) {
                    Icon(
                        when (state.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) NexusColors.NeonPink else NexusColors.Cyan.copy(alpha = 0.5f)
                    )
                }
            }

            if (state.currentTrack?.isVideo != true) {
                Spacer(Modifier.height(12.dp))
                SpectrumVisualizer(Modifier.fillMaxWidth().height(100.dp), FloatArray(64) { kotlin.random.Random.nextFloat() }, state.isPlaying)
                Spacer(Modifier.height(12.dp))
                Text("ЭКВАЛАЙЗЕР", color = NexusColors.Cyan.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Flat", "Кибер", "Техно", "Акустика").forEach { preset ->
                        FilterChip(
                            selected = state.selectedPreset == preset,
                            onClick = { onPreset(preset) },
                            label = { Text(preset, fontSize = 10.sp, fontFamily = CyberpunkFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f), selectedLabelColor = NexusColors.NeonPink)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExitFullScreen: () -> Unit,
    onPiP: () -> Unit,
    onSpeed: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(16.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp)).border(1.dp, NexusColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, "Prev", tint = NexusColors.Cyan, modifier = Modifier.size(28.dp))
            }
            Box(Modifier.size(60.dp).clip(CircleShape).background(NexusColors.NeonPink).clickable { onTogglePlayPause() }, contentAlignment = Alignment.Center) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, "Next", tint = NexusColors.Cyan, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onSpeed, modifier = Modifier.size(44.dp)) {
                Text("${state.playbackSpeed}x", color = NexusColors.White, fontFamily = CyberpunkFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onPiP, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.PictureInPicture, "PiP", tint = NexusColors.NeonGreen, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onExitFullScreen, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.FullscreenExit, "Exit", tint = NexusColors.Purple, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun QueuePanel(state: PlayerUiState, onPlay: (MediaItem) -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(320.dp).padding(16.dp).shadow(16.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = NexusColors.DarkGrey.copy(alpha = 0.98f)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NexusColors.NeonPink.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📋 ТЕКУЩАЯ ОЧЕРЕДЬ", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("ВЬЮЕР", color = NexusColors.NeonPink, fontFamily = CyberpunkFontFamily, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                val items = if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
                items(items.take(30), key = { "queue_${it.id}_${it.uri}" }) { item ->
                    val isCurrent = item.uri == state.currentTrack?.uri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) NexusColors.NeonPink.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onPlay(item) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) NexusColors.NeonPink else NexusColors.Cyan.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${item.name} • ${item.artist}",
                            color = if (isCurrent) NexusColors.NeonPink else NexusColors.White,
                            fontFamily = CyberpunkFontFamily,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = item.formattedDuration,
                            color = NexusColors.Cyan.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemRow(item: MediaItem, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isPlaying) NexusColors.NeonPink.copy(alpha = 0.22f) else NexusColors.GlassBlack),
        shape = RoundedCornerShape(14.dp),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.dp, NexusColors.NeonPink) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(
                    Brush.linearGradient(if (item.isVideo) listOf(NexusColors.Purple, NexusColors.BloodRed) else listOf(NexusColors.Purple, NexusColors.Cyan))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Default.VideoFile else if (isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
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
