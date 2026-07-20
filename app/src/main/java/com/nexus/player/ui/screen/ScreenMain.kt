package com.nexus.player.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.player.data.model.MediaFormat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.di.AppModule
import com.nexus.player.player.service.CyberPlayerService
import com.nexus.player.ui.components.*
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors
import kotlinx.coroutines.*

enum class MediaTab { AUDIO, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain() {
    val context = LocalContext.current
    val mediaRepository = remember { AppModule.provideMediaRepository() }
    val scope = rememberCoroutineScope()
    
    var allMediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var audioItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var videoItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<MediaItem?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var playbackResult by remember { mutableStateOf<PlaybackResult>(PlaybackResult.Success) }
    var showPlaylist by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(MediaTab.AUDIO) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    var isLoading by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            scanMedia(scope, mediaRepository,
                onAudioItems = { audioItems = it },
                onVideoItems = { videoItems = it },
                onAllItems = { allMediaItems = it },
                onLoading = { isLoading = it },
                onScanning = { isScanning = it }
            )
        }
    }
    
    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsPermission) {
            permissionLauncher.launch(permissions)
        } else {
            scanMedia(scope, mediaRepository,
                onAudioItems = { audioItems = it },
                onVideoItems = { videoItems = it },
                onAllItems = { allMediaItems = it },
                onLoading = { isLoading = it },
                onScanning = { isScanning = it }
            )
        }
    }
    
    fun startPlayback(item: MediaItem) {
        currentTrack = item
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_PLAY
            putExtra(CyberPlayerService.EXTRA_FILE_PATH, item.path)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        isPlaying = true
    }
    
    fun togglePlayPause() {
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = if (isPlaying) CyberPlayerService.ACTION_PAUSE else CyberPlayerService.ACTION_PLAY
        }
        context.startService(intent)
        isPlaying = !isPlaying
    }
    
    fun playNext() {
        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
        if (items.isEmpty()) return
        
        val currentIndex = items.indexOfFirst { it.id == currentTrack?.id }
        val nextIndex = (currentIndex + 1) % items.size
        startPlayback(items[nextIndex])
    }
    
    fun playPrevious() {
        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
        if (items.isEmpty()) return
        
        val currentIndex = items.indexOfFirst { it.id == currentTrack?.id }
        val prevIndex = if (currentIndex <= 0) items.size - 1 else currentIndex - 1
        startPlayback(items[prevIndex])
    }
    
    fun refreshMedia() {
        scanMedia(scope, mediaRepository,
            onAudioItems = { audioItems = it },
            onVideoItems = { videoItems = it },
            onAllItems = { allMediaItems = it },
            onLoading = { isLoading = it },
            onScanning = { isScanning = it }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ParticleBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .systemBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonGradientText(
                    text = "NEXUS",
                    fontSize = 32.sp
                )
                
                Row {
                    IconButton(onClick = { refreshMedia() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isScanning) NexusColors.NeonPink else NexusColors.Cyan
                        )
                    }
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(
                            if (showPlaylist) Icons.Default.GraphicEq else Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            tint = if (showPlaylist) NexusColors.NeonPink else NexusColors.Cyan
                        )
                    }
                }
            }
            
            // Media counts
            if (!showPlaylist) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎵 ${audioItems.size}",
                        color = NexusColors.Cyan,
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "🎬 ${videoItems.size}",
                        color = NexusColors.Purple,
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (showPlaylist) {
                // Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = selectedTab == MediaTab.AUDIO,
                        onClick = { selectedTab = MediaTab.AUDIO },
                        label = {
                            Text(
                                "🎵 АУДИО (${audioItems.size})",
                                fontSize = 12.sp,
                                fontFamily = CyberpunkFontFamily
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f),
                            selectedLabelColor = NexusColors.NeonPink
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedTab == MediaTab.VIDEO,
                        onClick = { selectedTab = MediaTab.VIDEO },
                        label = {
                            Text(
                                "🎬 ВИДЕО (${videoItems.size})",
                                fontSize = 12.sp,
                                fontFamily = CyberpunkFontFamily
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusColors.Purple.copy(alpha = 0.3f),
                            selectedLabelColor = NexusColors.Purple
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Playlist content
                AnimatedVisibility(
                    visible = showPlaylist,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = NexusColors.NeonPink,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "СКАНИРОВАНИЕ МЕДИА...",
                                    color = NexusColors.Cyan,
                                    fontFamily = CyberpunkFontFamily,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        val items = when (selectedTab) {
                            MediaTab.AUDIO -> audioItems
                            MediaTab.VIDEO -> videoItems
                        }
                        
                        if (items.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (selectedTab == MediaTab.AUDIO) Icons.Default.MusicOff else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = NexusColors.Cyan.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (selectedTab == MediaTab.AUDIO) "НЕТ АУДИО ФАЙЛОВ" else "НЕТ ВИДЕО ФАЙЛОВ",
                                        color = NexusColors.Cyan.copy(alpha = 0.5f),
                                        fontFamily = CyberpunkFontFamily,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 140.dp)
                            ) {
                                items(items) { item ->
                                    MediaItemRow(
                                        item = item,
                                        isPlaying = currentTrack?.id == item.id && isPlaying,
                                        onClick = { startPlayback(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Now Playing View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlitchArtWork(
                        modifier = Modifier.size(280.dp),
                        albumArtUri = currentTrack?.albumArtUri,
                        isPlaying = isPlaying,
                        onTrackChange = false
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    if (currentTrack != null) {
                        NeonText(
                            text = currentTrack!!.name,
                            fontSize = 22.sp,
                            color = NexusColors.Cyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentTrack!!.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentTrack!!.format.name} • ${formatDuration(currentTrack!!.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.Purple.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                    } else {
                        NeonText(
                            text = "NEXUS PLAYER",
                            fontSize = 28.sp,
                            color = NexusColors.Cyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Выберите трек из плейлиста",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.5f),
                            fontFamily = CyberpunkFontFamily
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SpectrumVisualizer(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        isPlaying = isPlaying
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Equalizer presets
                    Text(
                        text = "ЭКВАЛАЙЗЕР",
                        color = NexusColors.Cyan.copy(alpha = 0.7f),
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val presets = listOf("Flat", "Киберпространство", "Техно-драйв", "Акустика")
                        presets.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = {
                                    Text(
                                        text = preset,
                                        fontSize = 10.sp,
                                        fontFamily = CyberpunkFontFamily
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f),
                                    selectedLabelColor = NexusColors.NeonPink
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Bottom player controls
        GlassMorphicPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = currentTrack?.duration ?: 0,
            onPlayPauseClick = { togglePlayPause() },
            onNextClick = { playNext() },
            onPreviousClick = { playPrevious() }
        )
        
        // Error overlay
        AnimatedVisibility(
            visible = playbackResult !is PlaybackResult.Success,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
        ) {
            when (val result = playbackResult) {
                is PlaybackResult.CorruptedButPlaying -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NexusColors.GoldenYellow.copy(alpha = 0.9f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.message,
                                color = Color.Black,
                                fontFamily = CyberpunkFontFamily,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                is PlaybackResult.FatalError -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NexusColors.BloodRed.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = result.userMessage,
                                color = Color.White,
                                fontFamily = CyberpunkFontFamily,
                                fontSize = 14.sp
                            )
                            if (result.canAttemptRecovery) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { refreshMedia() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                                ) {
                                    Text(
                                        text = "ПОПЫТАТЬСЯ ВОССТАНОВИТЬ",
                                        color = Color.Black,
                                        fontFamily = CyberpunkFontFamily,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MediaItemRow(
    item: MediaItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) 
                NexusColors.NeonPink.copy(alpha = 0.2f) 
            else 
                NexusColors.GlassBlack
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on media type
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (item.mimeType.startsWith("video/"))
                                listOf(NexusColors.Purple, NexusColors.BloodRed)
                            else
                                listOf(NexusColors.Purple, NexusColors.Cyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.mimeType.startsWith("video/") -> Icons.Default.VideoFile
                        isPlaying -> Icons.Default.Equalizer
                        else -> Icons.Default.MusicNote
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) NexusColors.NeonPink else NexusColors.White,
                    fontFamily = CyberpunkFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        text = item.format.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.Cyan.copy(alpha = 0.7f),
                        fontFamily = CyberpunkFontFamily
                    )
                    if (item.artist != "Unknown Artist") {
                        Text(
                            text = " • ${item.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.White.copy(alpha = 0.6f),
                            fontFamily = CyberpunkFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(item.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.Cyan,
                    fontFamily = CyberpunkFontFamily
                )
                Text(
                    text = if (item.mimeType.startsWith("video/")) "VIDEO" else "AUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.Purple.copy(alpha = 0.6f),
                    fontFamily = CyberpunkFontFamily
                )
            }
        }
    }
}

private fun scanMedia(
    scope: CoroutineScope,
    repository: com.nexus.player.data.repository.MediaRepository,
    onAudioItems: (List<MediaItem>) -> Unit,
    onVideoItems: (List<MediaItem>) -> Unit,
    onAllItems: (List<MediaItem>) -> Unit,
    onLoading: (Boolean) -> Unit,
    onScanning: (Boolean) -> Unit
) {
    onLoading(true)
    onScanning(true)
    
    val allItems = mutableListOf<MediaItem>()
    val audioList = mutableListOf<MediaItem>()
    val videoList = mutableListOf<MediaItem>()
    
    scope.launch(Dispatchers.IO) {
        try {
            repository.scanAllMedia().collect { item ->
                allItems.add(item)
                if (item.mimeType.startsWith("video/")) {
                    videoList.add(item)
                } else {
                    audioList.add(item)
                }
            }
        } catch (e: Exception) {
            // Handle scan error
        } finally {
            withContext(Dispatchers.Main) {
                onAudioItems(audioList.sortedBy { it.name.lowercase() })
                onVideoItems(videoList.sortedBy { it.name.lowercase() })
                onAllItems(allItems)
                onLoading(false)
                onScanning(false)
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
