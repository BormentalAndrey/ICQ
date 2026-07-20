package com.nexus.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexus.player.ui.theme.NexusColors

@Composable
fun GlassMorphicPanel(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    currentPosition: Long = 0,
    duration: Long = 0,
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Blurred background layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NexusColors.GlassWhite,
                            NexusColors.GlassBlack,
                            NexusColors.GlassWhite
                        )
                    )
                )
                .blur(radius = 10.dp)
        )
        
        // Glass border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                )
        )
        
        // Neon border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NexusColors.Cyan.copy(alpha = 0.3f),
                            NexusColors.NeonPink.copy(alpha = 0.3f),
                            NexusColors.Purple.copy(alpha = 0.3f),
                            NexusColors.Cyan.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(2.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            // Controls content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Progress bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { /* Implement seek */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NexusColors.NeonPink,
                        activeTrackColor = NexusColors.NeonPink,
                        inactiveTrackColor = NexusColors.MediumGrey
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(
                        icon = Icons.Default.Shuffle,
                        onClick = onShuffleClick,
                        size = 36.dp
                    )
                    
                    ControlButton(
                        icon = Icons.Default.SkipPrevious,
                        onClick = onPreviousClick,
                        size = 48.dp
                    )
                    
                    // Play/Pause button with neon glow
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                ambientColor = NexusColors.NeonPink,
                                spotColor = NexusColors.NeonPink
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        NexusColors.NeonPink,
                                        NexusColors.Purple
                                    )
                                )
                            )
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    ControlButton(
                        icon = Icons.Default.SkipNext,
                        onClick = onNextClick,
                        size = 48.dp
                    )
                    
                    ControlButton(
                        icon = Icons.Default.Repeat,
                        onClick = onRepeatClick,
                        size = 36.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NexusColors.Cyan,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}
