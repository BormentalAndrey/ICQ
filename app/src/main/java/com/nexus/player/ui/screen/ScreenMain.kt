package com.nexus.player.ui.screen

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nexus.player.data.model.MediaItem
import com.nexus.player.data.model.PlaybackResult
import com.nexus.player.di.AppModule
import com.nexus.player.player.service.CyberPlayerService
import com.nexus.player.ui.components.*
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

enum class MediaTab { AUDIO, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain() {
    val context = LocalContext.current
    val mediaRepository = remember { AppModule.provideMediaRepository() }
    val scope = rememberCoroutineScope()
    
    var audioItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var videoItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<MediaItem?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackResult by remember { mutableStateOf<PlaybackResult>(PlaybackResult.Success) }
    var showPlaylist by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(MediaTab.AUDIO) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    var isLoading by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED -> {
                        isPlaying = intent.getBooleanExtra(CyberPlayerService.EXTRA_IS_PLAYING, false)
                        currentPosition = intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L)
                        duration = intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L)
                    }
                    CyberPlayerService.ACTION_POSITION_UPDATED -> {
                        currentPosition = intent.getLongExtra(CyberPlayerService.EXTRA_CURRENT_POSITION, 0L)
                        duration = intent.getLongExtra(CyberPlayerService.EXTRA_DURATION, 0L)
                    }
                    CyberPlayerService.ACTION_TRACK_CHANGED -> {
                        val trackPath = intent.getStringExtra(CyberPlayerService.EXTRA_FILE_PATH)
                        if (trackPath != null) {
                            val allItems = audioItems + videoItems
                            currentTrack = allItems.find { it.path == trackPath } ?: currentTrack
                        }
                    }
                    CyberPlayerService.ACTION_ERROR_OCCURRED -> {
                        val errorMessage = intent.getStringExtra(CyberPlayerService.EXTRA_ERROR_MESSAGE) ?: "Ошибка воспроизведения"
                        val damagePercent = intent.getFloatExtra(CyberPlayerService.EXTRA_DAMAGE_PERCENT, 0f)
                        if (damagePercent > 0f) {
                            playbackResult = PlaybackResult.CorruptedButPlaying(
                                damagePercent = damagePercent,
                                message = errorMessage
                            )
                        } else {
                            playbackResult = PlaybackResult.FatalError(
                                throwable = Exception(errorMessage),
                                userMessage = errorMessage,
                                canAttemptRecovery = true
                            )
                        }
                    }
                    CyberPlayerService.ACTION_RECOVERY_PROGRESS -> {
                        val progress = intent.getFloatExtra(CyberPlayerService.EXTRA_RECOVERY_PROGRESS, 0f)
                        playbackResult = PlaybackResult.RecoveryInProgress(progress)
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(CyberPlayerService.ACTION_PLAYBACK_STATE_CHANGED)
            addAction(CyberPlayerService.ACTION_POSITION_UPDATED)
            addAction(CyberPlayerService.ACTION_TRACK_CHANGED)
            addAction(CyberPlayerService.ACTION_ERROR_OCCURRED)
            addAction(CyberPlayerService.ACTION_RECOVERY_PROGRESS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Игнорируем исключение, если ресивер уже был отрегистрирован системой
            }
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            scope.launch {
                scanMedia(
                    repository = mediaRepository,
                    onAudioItems = { audioItems = it },
                    onVideoItems = { videoItems = it },
                    onLoading = { isLoading = it },
                    onScanning = { isScanning = it }
                )
            }
        } else {
            isLoading = false
            isScanning = false
        }
    }
    
    fun startMediaScan() {
        scope.launch {
            scanMedia(
                repository = mediaRepository,
                onAudioItems = { audioItems = it },
                onVideoItems = { videoItems = it },
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
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsPermission) {
            permissionLauncher.launch(permissions)
        } else {
            startMediaScan()
        }
    }
    
    fun startPlayback(item: MediaItem) {
        currentTrack = item
        playbackResult = PlaybackResult.Success
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_PLAY
            putExtra(CyberPlayerService.EXTRA_FILE_PATH, item.path)
        }
        
        try {
            ContextCompat.startForegroundService(context, intent)
            isPlaying = true
        } catch (e: Exception) {
            playbackResult = PlaybackResult.FatalError(
                throwable = e,
                userMessage = "Не удалось запустить службу плеера: ${e.localizedMessage}",
                canAttemptRecovery = true
            )
        }
    }
    
    fun togglePlayPause() {
        if (currentTrack == null) {
            val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
            if (items.isNotEmpty()) {
                startPlayback(items.first())
            }
            return
        }
        
        val intent = Intent(context, CyberPlayerService::class.java).apply {
            action = if (isPlaying) CyberPlayerService.ACTION_PAUSE else CyberPlayerService.ACTION_PLAY
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            isPlaying = !isPlaying
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun playNext() {
        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
        if (items.isEmpty()) return
        
        val currentIndex = items.indexOfFirst { it.id == currentTrack?.id }
        val nextIndex = if (currentIndex < 0 || currentIndex >= items.size - 1) 0 else currentIndex + 1
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
        if (!isScanning) {
            startMediaScan()
        }
    }
    
    fun applyEqualizerPreset(preset: String) {
        selectedPreset = preset
        val eqIntent = Intent(context, CyberPlayerService::class.java).apply {
            action = CyberPlayerService.ACTION_SET_EQUALIZER
            putExtra(CyberPlayerService.EXTRA_EQUALIZER_PRESET, preset)
        }
        try {
            context.startService(eqIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ParticleBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonGradientText(text = "NEXUS", fontSize = 32.sp)
                
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
            
            if (!showPlaylist) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("🎵 ${audioItems.size}", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("🎬 ${videoItems.size}", color = NexusColors.Purple, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (showPlaylist) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = selectedTab == MediaTab.AUDIO,
                        onClick = { selectedTab = MediaTab.AUDIO },
                        label = { Text("🎵 АУДИО (${audioItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusColors.NeonPink.copy(alpha = 0.3f),
                            selectedLabelColor = NexusColors.NeonPink
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedTab == MediaTab.VIDEO,
                        onClick = { selectedTab = MediaTab.VIDEO },
                        label = { Text("🎬 ВИДЕО (${videoItems.size})", fontSize = 12.sp, fontFamily = CyberpunkFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusColors.Purple.copy(alpha = 0.3f),
                            selectedLabelColor = NexusColors.Purple
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AnimatedVisibility(
                    visible = showPlaylist,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    if (isLoading || isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NexusColors.NeonPink, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("СКАНИРОВАНИЕ МЕДИА...", color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily, fontSize = 16.sp)
                            }
                        }
                    } else {
                        val items = when (selectedTab) {
                            MediaTab.AUDIO -> audioItems
                            MediaTab.VIDEO -> videoItems
                        }
                        
                        if (items.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (selectedTab == MediaTab.AUDIO) Icons.Default.MusicOff else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = NexusColors.Cyan.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        if (selectedTab == MediaTab.AUDIO) "НЕТ АУДИО ФАЙЛОВ" else "НЕТ ВИДЕО ФАЙЛОВ",
                                        color = NexusColors.Cyan.copy(alpha = 0.5f),
                                        fontFamily = CyberpunkFontFamily,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { refreshMedia() }) {
                                        Text("ОБНОВИТЬ", color = NexusColors.NeonPink, fontFamily = CyberpunkFontFamily)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 140.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    val isCurrentPlaying = currentTrack?.id == item.id && isPlaying
                                    val onClickAction = remember(item) { { startPlayback(item) } }
                                    MediaItemRow(
                                        item = item,
                                        isPlaying = isCurrentPlaying,
                                        onClick = onClickAction
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().weight(1f),
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
                    
                    val track = currentTrack
                    if (track != null) {
                        NeonText(text = track.name, fontSize = 22.sp, color = NexusColors.Cyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = NexusColors.White.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${track.format.name} • ${track.formattedDuration}", style = MaterialTheme.typography.bodySmall, color = NexusColors.Purple.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily)
                    } else {
                        NeonText(text = "NEXUS PLAYER", fontSize = 28.sp, color = NexusColors.Cyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Выберите трек из плейлиста", style = MaterialTheme.typography.bodyLarge, color = NexusColors.White.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Найдено: 🎵${audioItems.size} аудио • 🎬${videoItems.size} видео", style = MaterialTheme.typography.bodySmall, color = NexusColors.Cyan.copy(alpha = 0.5f), fontFamily = CyberpunkFontFamily)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SpectrumVisualizer(modifier = Modifier.fillMaxWidth().height(120.dp), isPlaying = isPlaying)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("ЭКВАЛАЙЗЕР", color = NexusColors.Cyan.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val presets = listOf("Flat", "Киберпространство", "Техно-драйв", "Акустика")
                        presets.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { applyEqualizerPreset(preset) },
                                label = { Text(preset, fontSize = 10.sp, fontFamily = CyberpunkFontFamily) },
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
        
        GlassMorphicPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = if (duration > 0) duration else (currentTrack?.duration ?: 0L),
            onPlayPauseClick = { togglePlayPause() },
            onNextClick = { playNext() },
            onPreviousClick = { playPrevious() }
        )
        
        AnimatedVisibility(
            visible = playbackResult !is PlaybackResult.Success,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            when (val result = playbackResult) {
                is PlaybackResult.CorruptedButPlaying -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NexusColors.GoldenYellow.copy(alpha = 0.9f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(result.message, color = Color.Black, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                        }
                    }
                }
                is PlaybackResult.FatalError -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NexusColors.BloodRed.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(result.userMessage, color = Color.White, fontFamily = CyberpunkFontFamily, fontSize = 14.sp)
                            if (result.canAttemptRecovery) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { playbackResult = PlaybackResult.Success; refreshMedia() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                                ) {
                                    Text("ПОПЫТАТЬСЯ ВОССТАНОВИТЬ", color = Color.Black, fontFamily = CyberpunkFontFamily, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                is PlaybackResult.RecoveryInProgress -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NexusColors.Cyan.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Восстановление... ${(result.progress * 100).toInt()}%", color = Color.Black, fontFamily = CyberpunkFontFamily)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { result.progress }, modifier = Modifier.fillMaxWidth(), color = NexusColors.NeonPink)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MediaItemRow(item: MediaItem, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) NexusColors.NeonPink.copy(alpha = 0.2f) else NexusColors.GlassBlack
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (item.isVideo) listOf(NexusColors.Purple, NexusColors.BloodRed)
                            else listOf(NexusColors.Purple, NexusColors.Cyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isVideo -> Icons.Default.VideoFile
                        isPlaying -> Icons.Default.Equalizer
                        else -> Icons.Default.MusicNote
                    },
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)
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
                    Text(item.format.name, style = MaterialTheme.typography.bodySmall, color = NexusColors.Cyan.copy(alpha = 0.7f), fontFamily = CyberpunkFontFamily)
                    if (item.artist != "Unknown Artist") {
                        Text(" • ${item.artist}", style = MaterialTheme.typography.bodySmall, color = NexusColors.White.copy(alpha = 0.6f), fontFamily = CyberpunkFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(item.formattedDuration, style = MaterialTheme.typography.bodySmall, color = NexusColors.Cyan, fontFamily = CyberpunkFontFamily)
                Text(if (item.isVideo) "VIDEO" else "AUDIO", style = MaterialTheme.typography.labelSmall, color = NexusColors.Purple.copy(alpha = 0.6f), fontFamily = CyberpunkFontFamily)
            }
        }
    }
}

private suspend fun scanMedia(
    repository: com.nexus.player.data.repository.MediaRepository,
    onAudioItems: (List<MediaItem>) -> Unit,
    onVideoItems: (List<MediaItem>) -> Unit,
    onLoading: (Boolean) -> Unit,
    onScanning: (Boolean) -> Unit
) = withContext(Dispatchers.Main) {
    onLoading(true)
    onScanning(true)
    
    val audioList = CopyOnWriteArrayList<MediaItem>()
    val videoList = CopyOnWriteArrayList<MediaItem>()
    
    try {
        withContext(Dispatchers.IO) {
            repository.scanAllMedia().collect { item ->
                if (item.isVideo) {
                    videoList.add(item)
                } else {
                    audioList.add(item)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        onAudioItems(audioList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        onVideoItems(videoList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        onLoading(false)
        onScanning(false)
    }
}
