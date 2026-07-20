package com.nexus.player.ui.screen

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMain() {
    val context = LocalContext.current
    val mediaRepository = remember { AppModule.provideMediaRepository() }
    
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<MediaItem?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var playbackResult by remember { mutableStateOf<PlaybackResult>(PlaybackResult.Success) }
    var showPlaylist by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Permissions granted, loading handled by LaunchedEffect below
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
            mediaRepository.scanAllMedia().collect { item ->
                mediaItems = mediaItems + item
            }
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
                NeonGradientText(
                    text = "NEXUS",
                    fontSize = 32.sp
                )
                
                Row {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = NexusColors.Cyan
                        )
                    }
                    IconButton(onClick = { showPlaylist = !showPlaylist }) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            tint = NexusColors.Cyan
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NexusColors.Cyan
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (showPlaylist) {
                AnimatedVisibility(
                    visible = showPlaylist,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 140.dp)
                    ) {
                        items(mediaItems) { item ->
                            MediaItemRow(
                                item = item,
                                isPlaying = currentTrack?.id == item.id && isPlaying,
                                onClick = { startPlayback(item) }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlitchArtWork(
                        modifier = Modifier.size(300.dp),
                        albumArtUri = currentTrack?.albumArtUri,
                        isPlaying = isPlaying,
                        onTrackChange = false
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    currentTrack?.let { track ->
                        NeonText(
                            text = track.name,
                            fontSize = 24.sp,
                            color = NexusColors.Cyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                    } ?: run {
                        NeonText(
                            text = "КИБЕРПАНК ПЛЕЕР",
                            fontSize = 24.sp,
                            color = NexusColors.Cyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Готов к воспроизведению",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NexusColors.White.copy(alpha = 0.7f),
                            fontFamily = CyberpunkFontFamily
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SpectrumVisualizer(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        isPlaying = isPlaying
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
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
        
        GlassMorphicPanel(
            modifier = Modifier.align(Alignment.BottomCenter),
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = currentTrack?.duration ?: 0,
            onPlayPauseClick = { togglePlayPause() },
            onNextClick = { },
            onPreviousClick = { }
        )
        
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
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.message,
                                color = Color.Black,
                                fontFamily = CyberpunkFontFamily
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
                                fontFamily = CyberpunkFontFamily
                            )
                            if (result.canAttemptRecovery) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "ПОПЫТАТЬСЯ ВОССТАНОВИТЬ",
                                        color = Color.Black,
                                        fontFamily = CyberpunkFontFamily
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NexusColors.Purple, NexusColors.Cyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
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
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusColors.White.copy(alpha = 0.6f),
                    fontFamily = CyberpunkFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = formatDuration(item.duration),
                style = MaterialTheme.typography.bodySmall,
                color = NexusColors.Cyan,
                fontFamily = CyberpunkFontFamily
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
