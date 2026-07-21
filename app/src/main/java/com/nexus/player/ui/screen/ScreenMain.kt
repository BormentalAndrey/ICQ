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
import kotlin.random.Random

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

    fun startMediaScan() {
        scope.launch {
            isLoading = true
            isScanning = true
            try {
                val all = withContext(Dispatchers.IO) { mediaRepository.loadAllMedia() }
                audioItems = all.filter { !it.isVideo }.sortedBy { it.name.lowercase() }
                videoItems = all.filter { it.isVideo }.sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                isScanning = false
            }
        }
    }

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
                            currentTrack = (audioItems + videoItems).find { it.path == trackPath } ?: currentTrack
                        }
                    }
                    CyberPlayerService.ACTION_ERROR_OCCURRED -> {
                        val msg = intent.getStringExtra(CyberPlayerService.EXTRA_ERROR_MESSAGE) ?: "Ошибка"
                        val dp = intent.getFloatExtra(CyberPlayerService.EXTRA_DAMAGE_PERCENT, 0f)
                        playbackResult = if (dp > 0f) {
                            PlaybackResult.CorruptedButPlaying(damagePercent = dp, message = msg)
                        } else {
                            PlaybackResult.FatalError(
                                throwable = Exception(msg),
                                userMessage = msg,
                                canAttemptRecovery = true
                            )
                        }
                    }
                    CyberPlayerService.ACTION_RECOVERY_PROGRESS -> {
                        playbackResult = PlaybackResult.RecoveryInProgress(
                            intent.getFloatExtra(CyberPlayerService.EXTRA_RECOVERY_PROGRESS, 0f)
                        )
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
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            startMediaScan()
        } else {
            isLoading = false
            isScanning = false
        }
    }
    
    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(perms)
        } else {
            startMediaScan()
        }
    }
    
    fun startPlayback(item: MediaItem) {
        currentTrack = item
        playbackResult = PlaybackResult.Success
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CyberPlayerService::class.java).apply {
                    action = CyberPlayerService.ACTION_PLAY
                    putExtra(CyberPlayerService.EXTRA_FILE_PATH, item.path)
                }
            )
            isPlaying = true
        } catch (e: Exception) {
            playbackResult = PlaybackResult.FatalError(
                throwable = e,
                userMessage = "Не удалось запустить плеер",
                canAttemptRecovery = true
            )
        }
    }
    
    fun togglePlayPause() {
        if (currentTrack == null) {
            (if (selectedTab == MediaTab.AUDIO) audioItems else videoItems).firstOrNull()?.let { startPlayback(it) }
            return
        }
        context.startService(
            Intent(context, CyberPlayerService::class.java).apply {
                action = if (isPlaying) CyberPlayerService.ACTION_PAUSE else CyberPlayerService.ACTION_PLAY
            }
        )
        isPlaying = !isPlaying
    }
    
    fun playNext() {
        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.id == currentTrack?.id }
        val next = if (idx < 0 || idx >= items.size - 1) 0 else idx + 1
        startPlayback(items[next])
    }
    
    fun playPrevious() {
        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
        if (items.isEmpty()) return
        val idx = items.indexOfFirst { it.id == currentTrack?.id }
        val prev = if (idx <= 0) items.size - 1 else idx - 1
        startPlayback(items[prev])
    }
    
    fun refreshMedia() {
        if (!isScanning) startMediaScan()
    }
    
    fun applyEqualizerPreset(preset: String) {
        selectedPreset = preset
        context.startService(
            Intent(context, CyberPlayerService::class.java).apply {
                action = CyberPlayerService.ACTION_SET_EQUALIZER
                putExtra(CyberPlayerService.EXTRA_EQUALIZER_PRESET, preset)
            }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        ParticleBackground()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).systemBarsPadding()) {
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
                            "Refresh",
                            tint = if (isScanning) NexusColors.NeonPink else NexusColors.Cyan
                        )
                    }
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(
                            if (showPlaylist) Icons.Default.GraphicEq else Icons.Default.QueueMusic,
                            "Playlist",
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
                    Text(
                        "🎵 ${audioItems.size}",
                        color = NexusColors.Cyan,
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "🎬 ${videoItems.size}",
                        color = NexusColors.Purple,
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (showPlaylist) {
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
                    Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = showPlaylist,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    if (isLoading || isScanning) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = NexusColors.NeonPink,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "СКАНИРОВАНИЕ...",
                                    color = NexusColors.Cyan,
                                    fontFamily = CyberpunkFontFamily
                                )
                            }
                        }
                    } else {
                        val items = if (selectedTab == MediaTab.AUDIO) audioItems else videoItems
                        if (items.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (selectedTab == MediaTab.AUDIO) Icons.Default.MusicOff
                                        else Icons.Default.VideoLibrary,
                                        null,
                                        tint = NexusColors.Cyan.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        if (selectedTab == MediaTab.AUDIO) "НЕТ АУДИО" else "НЕТ ВИДЕО",
                                        color = NexusColors.Cyan.copy(alpha = 0.5f),
                                        fontFamily = CyberpunkFontFamily
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { refreshMedia() }) {
                                        Text(
                                            "ОБНОВИТЬ",
                                            color = NexusColors.NeonPink,
                                            fontFamily = CyberpunkFontFamily
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 140.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
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
                Column(
                    Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlitchArtWork(
                        Modifier.size(280.dp),
                        currentTrack?.albumArtUri,
                        isPlaying,
                        false
                    )
                    Spacer(Modifier.height(32.dp))
                    currentTrack?.let {
                        NeonText(it.name, fontSize = 22.sp, color = NexusColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${it.format.name} • ${it.formattedDuration}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.Purple.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                    } ?: run {
                        NeonText("NEXUS PLAYER", fontSize = 28.sp, color = NexusColors.Cyan)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Выберите трек",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.5f),
                            fontFamily = CyberpunkFontFamily
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🎵${audioItems.size} аудио • 🎬${videoItems.size} видео",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusColors.Cyan.copy(alpha = 0.5f),
                            fontFamily = CyberpunkFontFamily
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    SpectrumVisualizer(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        frequencyData = FloatArray(64) { Random.nextFloat() },
                        isPlaying = isPlaying
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "ЭКВАЛАЙЗЕР",
                        color = NexusColors.Cyan.copy(alpha = 0.7f),
                        fontFamily = CyberpunkFontFamily,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Flat", "Киберпространство", "Техно-драйв", "Акустика").forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { applyEqualizerPreset(preset) },
                                label = {
                                    Text(
                                        preset,
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
        GlassMorphicPanel(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            isPlaying,
            currentPosition,
            if (duration > 0) duration else (currentTrack?.duration ?: 0L),
            { togglePlayPause() },
            { playNext() },
            { playPrevious() }
        )
        AnimatedVisibility(
            playbackResult !is PlaybackResult.Success,
            Modifier.align(Alignment.TopCenter).padding(16.dp).statusBarsPadding()
        ) {
            when (val r = playbackResult) {
                is PlaybackResult.CorruptedButPlaying -> Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NexusColors.GoldenYellow.copy(alpha = 0.9f))
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            r.message,
                            color = Color.Black,
                            fontFamily = CyberpunkFontFamily,
                            fontSize = 14.sp
                        )
                    }
                }
                is PlaybackResult.FatalError -> Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NexusColors.BloodRed.copy(alpha = 0.9f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            r.userMessage,
                            color = Color.White,
                            fontFamily = CyberpunkFontFamily,
                            fontSize = 14.sp
                        )
                        if (r.canAttemptRecovery) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { playbackResult = PlaybackResult.Success; refreshMedia() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                Text(
                                    "ВОССТАНОВИТЬ",
                                    color = Color.Black,
                                    fontFamily = CyberpunkFontFamily,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                is PlaybackResult.RecoveryInProgress -> Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NexusColors.Cyan.copy(alpha = 0.9f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Восстановление ${(r.progress * 100).toInt()}%",
                            color = Color.Black,
                            fontFamily = CyberpunkFontFamily
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            { r.progress },
                            Modifier.fillMaxWidth(),
                            color = NexusColors.NeonPink
                        )
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
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) NexusColors.NeonPink.copy(alpha = 0.2f) else NexusColors.GlassBlack
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(
                    Brush.linearGradient(
                        if (item.isVideo) listOf(NexusColors.Purple, NexusColors.BloodRed)
                        else listOf(NexusColors.Purple, NexusColors.Cyan)
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isVideo) Icons.Default.VideoFile
                    else if (isPlaying) Icons.Default.Equalizer
                    else Icons.Default.MusicNote,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) NexusColors.NeonPink else NexusColors.White,
                    fontFamily = CyberpunkFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        item.format.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusColors.Cyan.copy(alpha = 0.7f),
                        fontFamily = CyberpunkFontFamily
                    )
                    if (item.artist != "Unknown Artist") {
                        Text(
                            " • ${item.artist}",
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
                    item.formattedDuration,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.Cyan,
                    fontFamily = CyberpunkFontFamily
                )
                Text(
                    if (item.isVideo) "VIDEO" else "AUDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusColors.Purple.copy(alpha = 0.6f),
                    fontFamily = CyberpunkFontFamily
                )
            }
        }
    }
}
