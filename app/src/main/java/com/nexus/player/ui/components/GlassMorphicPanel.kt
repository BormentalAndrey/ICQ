package com.nexus.player.ui.components

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
    onFullScreen: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onPiP: () -> Unit = {},
    onQueue: () -> Unit = {}
) {
    var sliderValue by remember(currentPosition, duration) {
        mutableFloatStateOf(if (duration > 0) currentPosition.toFloat() / duration else 0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(NexusColors.GlassWhite, NexusColors.GlassBlack, NexusColors.GlassWhite)
                    )
                )
                .blur(radius = 10.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.1f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NexusColors.Cyan.copy(alpha = 0.3f), NexusColors.NeonPink.copy(alpha = 0.3f),
                            NexusColors.Purple.copy(alpha = 0.3f), NexusColors.Cyan.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(2.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { fraction ->
                        sliderValue = fraction
                        onSeek((fraction * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = NexusColors.NeonPink,
                        activeTrackColor = NexusColors.NeonPink,
                        inactiveTrackColor = NexusColors.MediumGrey
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Queue
                    SmallButton(icon = Icons.Default.List, onClick = onQueue)

                    // Previous
                    SmallButton(icon = Icons.Default.SkipPrevious, onClick = onPreviousClick)

                    // Play/Pause
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(10.dp, CircleShape, ambientColor = NexusColors.NeonPink, spotColor = NexusColors.NeonPink)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(NexusColors.NeonPink, NexusColors.Purple)))
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next
                    SmallButton(icon = Icons.Default.SkipNext, onClick = onNextClick)

                    // Fullscreen / PiP
                    SmallButton(icon = Icons.Default.Fullscreen, onClick = onFullScreen)
                }
            }
        }
    }
}

@Composable
private fun SmallButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, null, tint = NexusColors.Cyan, modifier = Modifier.size(22.dp))
    }
}
