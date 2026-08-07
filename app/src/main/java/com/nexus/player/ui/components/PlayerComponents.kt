package com.nexus.player.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nexus.player.NexusApplication
import com.nexus.player.data.model.MediaItem
import com.nexus.player.ui.state.MediaTab
import com.nexus.player.ui.state.PlayerUiState
import com.nexus.player.ui.state.RepeatMode
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors
import com.nexus.player.ui.viewmodel.MainViewModel
import com.nexus.player.utils.findActivity
import com.nexus.player.utils.formatMediaTime
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(state: PlayerUiState, viewModel: MainViewModel, onPlay: (MediaItem, List<MediaItem>?) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.selectedTab == MediaTab.AUDIO,
                onClick = { viewModel.selectTab(MediaTab.AUDIO) },
                label = { Text("🎵 АУДИО (${state.audioItems.size})", fontSize = 11.sp, fontFamily = CyberpunkFontFamily) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f), selectedLabelColor = NexusColors.NeonPink)
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = state.selectedTab == MediaTab.VIDEO,
                onClick = { viewModel.selectTab(MediaTab.VIDEO) },
                label = { Text("🎬 ВИДЕО (${state.videoItems.size})", fontSize = 11.sp, fontFamily = CyberpunkFontFamily) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.Purple.copy(alpha = 0.3f), selectedLabelColor = NexusColors.Purple)
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = state.selectedTab == MediaTab.PLAYLISTS,
                onClick = { viewModel.selectTab(MediaTab.PLAYLISTS) },
                label = { Text("📁 ПЛЕЙЛИСТЫ (${state.customPlaylists.size})", fontSize = 11.sp, fontFamily = CyberpunkFontFamily) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusColors.Cyan.copy(alpha = 0.3f), selectedLabelColor = NexusColors.Cyan)
            )
        }
        Spacer(Modifier.height(12.dp))
        
        if (state.selectedTab == MediaTab.PLAYLISTS) {
            PlaylistsTabView(state, viewModel, onPlay)
        } else {
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { "${it.id}_${it.isVideo}_${it.uri}" }) { item ->
                            MediaItemRow(
                                item = item, 
                                isPlaying = state.currentTrack?.uri == item.uri && state.isPlaying,
                                onClick = { onPlay(item, rawItems) },
                                onAddToPlaylist = { viewModel.openAddToPlaylistMenu(item.uri.toString()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTabView(
    state: PlayerUiState,
    viewModel: MainViewModel,
    onPlay: (MediaItem, List<MediaItem>?) -> Unit
) {
    val allMedia = remember(state.audioItems, state.videoItems) {
        state.audioItems + state.videoItems
    }

    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { viewModel.showCreatePlaylistDialog(true) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.NeonPink.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("СОЗДАТЬ КИБЕР-ПЛЕЙЛИСТ", fontFamily = CyberpunkFontFamily, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))

        if (state.customPlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("НЕТ СОЗДАННЫХ ПЛЕЙЛИСТОВ", color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.customPlaylists, key = { it.id }) { playlist ->
                    val playlistTracks = remember(playlist.trackUris, allMedia) {
                        allMedia.filter { playlist.trackUris.contains(it.uri.toString()) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = NexusColors.GlassBlack),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusColors.Cyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontWeight = FontWeight.Bold)
                                    Text("ТРЕКОВ: ${playlistTracks.size}", style = MaterialTheme.typography.bodySmall, color = NexusColors.White.copy(alpha = 0.6f), fontFamily = CyberpunkFontFamily)
                                }
                                Row {
                                    if (playlistTracks.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            onPlay(playlistTracks.first(), playlistTracks) 
                                        }) {
                                            Icon(Icons.Default.PlayCircleOutline, "Play Playlist", tint = NexusColors.NeonGreen, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                        Icon(Icons.Default.DeleteOutline, "Delete", tint = NexusColors.BloodRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerView(
    state: PlayerUiState,
    viewModel: MainViewModel,
    showControls: Boolean,
    onInteraction: () -> Unit,
    onToggleControls: () -> Unit,
    onPlay: (MediaItem, List<MediaItem>?) -> Unit,
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
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val currentState by rememberUpdatedState(state)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnInteraction by rememberUpdatedState(onInteraction)
    val currentOnToggleControls by rememberUpdatedState(onToggleControls)

    var dragMode by remember { mutableStateOf<String?>(null) }
    var seekStartPosition by remember { mutableLongStateOf(0L) }
    var seekTargetPosition by remember { mutableLongStateOf(0L) }
    var accumulatedSeekMillis by remember { mutableFloatStateOf(0f) }
    
    var brightnessAccumulator by remember { mutableFloatStateOf(0.5f) }
    var volumeAccumulator by remember { mutableFloatStateOf(0.5f) }

    var spectrumData by remember { mutableStateOf(FloatArray(64) { 0.1f }) }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            spectrumData = FloatArray(64) { (kotlin.random.Random.nextFloat() * 0.85f + 0.15f).coerceIn(0.05f, 1f) }
            delay(80)
        }
        if (!state.isPlaying) {
            spectrumData = FloatArray(64) { 0.05f }
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                currentOnInteraction()
                dragMode = null
                accumulatedSeekMillis = 0f
                seekStartPosition = currentState.currentPosition
                seekTargetPosition = currentState.currentPosition
                
                val act = context.findActivity()
                val bright = act?.window?.attributes?.screenBrightness ?: -1f
                brightnessAccumulator = if (bright < 0) 0.5f else bright
                volumeAccumulator = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            },
            onDragEnd = {
                if (dragMode == "SEEK" && currentState.duration > 0) {
                    currentOnSeek(seekTargetPosition)
                }
                dragMode = null
            },
            onDragCancel = {
                if (dragMode == "SEEK" && currentState.duration > 0) {
                    currentOnSeek(seekTargetPosition)
                }
                dragMode = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                currentOnInteraction()
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w > 0 && h > 0) {
                    if (dragMode == null) {
                        dragMode = when {
                            abs(dragAmount.x) > abs(dragAmount.y) -> "SEEK"
                            change.position.x < w / 2 -> "BRIGHTNESS"
                            else -> "VOLUME"
                        }
                    }
                    when (dragMode) {
                        "SEEK" -> {
                            if (currentState.duration > 0) {
                                accumulatedSeekMillis += (dragAmount.x / w) * currentState.duration.toFloat()
                                seekTargetPosition = (seekStartPosition + accumulatedSeekMillis.toLong()).coerceIn(0L, currentState.duration)
                                
                                val diffSeconds = (seekTargetPosition - seekStartPosition) / 1000L
                                val sign = if (diffSeconds >= 0) "+" else ""
                                val arrow = if (diffSeconds >= 0) "⏩" else "⏪"
                                val targetStr = formatMediaTime(seekTargetPosition)
                                val durationStr = formatMediaTime(currentState.duration)
                                
                                viewModel.showGestureIndicator("$arrow $targetStr / $durationStr ($sign${diffSeconds}с)")
                                viewModel.onPositionUpdated(seekTargetPosition, currentState.duration)
                            }
                        }
                        "BRIGHTNESS" -> {
                            brightnessAccumulator = (brightnessAccumulator - dragAmount.y / h).coerceIn(0.01f, 1f)
                            val act = context.findActivity()
                            act?.let { activity ->
                                val lp = activity.window.attributes
                                lp.screenBrightness = brightnessAccumulator
                                activity.window.attributes = lp
                            }
                            viewModel.showGestureIndicator("☀ ${(brightnessAccumulator * 100).toInt()}%")
                        }
                        "VOLUME" -> {
                            volumeAccumulator = (volumeAccumulator - (dragAmount.y / h) * maxVolume).coerceIn(0f, maxVolume.toFloat())
                            val newVol = volumeAccumulator.roundToInt()
                            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0) } catch (_: Exception) {}
                            val percent = if (maxVolume > 0) ((newVol.toFloat() / maxVolume) * 100).toInt() else 0
                            viewModel.showGestureIndicator("🔊 $percent%")
                        }
                    }
                }
            }
        )
    }

    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { currentOnToggleControls() },
            onDoubleTap = { offset ->
                currentOnInteraction()
                val w = size.width
                if (offset.x < w / 2) {
                    val newPos = (currentState.currentPosition - 10000L).coerceAtLeast(0L)
                    currentOnSeek(newPos)
                    viewModel.showGestureIndicator("⏪ -10с (${formatMediaTime(newPos)})")
                } else {
                    val newPos = (currentState.currentPosition + 10000L).coerceAtMost(if (currentState.duration > 0) currentState.duration else Long.MAX_VALUE)
                    currentOnSeek(newPos)
                    viewModel.showGestureIndicator("⏩ +10с (${formatMediaTime(newPos)})")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .then(if (state.isFullScreen) Modifier.background(Color.Black) else Modifier.verticalScroll(scrollState))
            .then(gestureModifier)
            .then(tapModifier),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.currentTrack?.isVideo == true) {
            Box(Modifier.fillMaxWidth().then(if (state.isFullScreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = (ctx.applicationContext as? NexusApplication)?.exoPlayer
                            useController = false
                        }
                    },
                    update = { view ->
                        view.player = (view.context.applicationContext as? NexusApplication)?.exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (state.isFullScreen) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls || !state.isPlaying,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    ) {
                        FullScreenControls(state, onTogglePlayPause, onNext, onPrevious, { viewModel.toggleFullScreen() }, onPiP, onSpeed)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            GlitchArtWork(Modifier.size(260.dp), state.currentTrack?.previewUri, state.isPlaying, false)
        }

        if (!state.isFullScreen) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls || !state.isPlaying,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
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
                        SpectrumVisualizer(Modifier.fillMaxWidth().height(80.dp), spectrumData, state.isPlaying)
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
                        Spacer(Modifier.height(12.dp))
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
    Box(Modifier.fillMaxWidth().padding(16.dp).background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(24.dp)).border(1.dp, NexusColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(12.dp)) {
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
fun QueuePanel(state: PlayerUiState, onPlay: (MediaItem, List<MediaItem>?) -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 16.dp).shadow(16.dp, RoundedCornerShape(20.dp)),
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
                val items = if (state.currentQueue.isNotEmpty()) state.currentQueue else if (state.selectedTab == MediaTab.AUDIO) state.audioItems else state.videoItems
                items(items.take(30), key = { "queue_${it.id}_${it.uri}" }) { item ->
                    val isCurrent = item.uri == state.currentTrack?.uri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) NexusColors.NeonPink.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onPlay(item, items) }
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
fun MediaItemRow(item: MediaItem, isPlaying: Boolean, onClick: () -> Unit, onAddToPlaylist: () -> Unit) {
    val context = LocalContext.current
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
                var isImageLoadFailed by remember { mutableStateOf(false) }

                if (!isImageLoadFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.previewUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { isImageLoadFailed = true }
                    )
                }

                if (isImageLoadFailed) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.VideoFile else if (isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = if (isPlaying) NexusColors.NeonPink else NexusColors.White, fontFamily = CyberpunkFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = NexusColors.White.copy(alpha = 0.6f), fontFamily = CyberpunkFontFamily)
            }
            Text(item.formattedDuration, style = MaterialTheme.typography.bodySmall, color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = NexusColors.NeonPink)
            }
        }
    }
}
