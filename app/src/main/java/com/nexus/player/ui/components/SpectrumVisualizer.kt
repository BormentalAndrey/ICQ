package com.nexus.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nexus.player.ui.theme.NexusColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SpectrumVisualizer(
    modifier: Modifier = Modifier,
    frequencyData: FloatArray = FloatArray(64) { Random.nextFloat() },
    isPlaying: Boolean = false
) {
    val animatedData = remember { mutableStateListOf<Float>().apply { addAll(frequencyData.toList()) } }
    val animProgress = remember { Animatable(0f) }
    
    // Animate spectrum changes
    LaunchedEffect(frequencyData) {
        if (frequencyData.size == animatedData.size) {
            animatedData.forEachIndexed { index, _ ->
                animatedData[index] = frequencyData[index]
            }
        }
    }
    
    // Continuous animation for playing state
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    
    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barCount = animatedData.size
        val barWidth = canvasWidth / barCount
        val maxBarHeight = canvasHeight * 0.8f
        
        // Draw circular spectrum
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        val radius = minOf(canvasWidth, canvasHeight) * 0.35f
        
        for (i in 0 until barCount) {
            val angle = (i.toFloat() / barCount) * 2 * PI.toFloat()
            val magnitude = animatedData[i].coerceIn(0f, 1f)
            
            val barHeight = magnitude * maxBarHeight
            val innerRadius = radius
            val outerRadius = radius + barHeight
            
            val startX = centerX + cos(angle) * innerRadius
            val startY = centerY + sin(angle) * innerRadius
            val endX = centerX + cos(angle) * outerRadius
            val endY = centerY + sin(angle) * outerRadius
            
            val color = when {
                magnitude > 0.7f -> NexusColors.NeonPink
                magnitude > 0.4f -> NexusColors.Cyan
                else -> NexusColors.Purple
            }
            
            drawLine(
                color = color.copy(alpha = 0.8f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = (barWidth * 0.6f).coerceAtMost(6f)
            )
        }
        
        // Center circle
        drawCircle(
            color = NexusColors.BlackBackground.copy(alpha = 0.8f),
            radius = radius * 0.8f,
            center = Offset(centerX, centerY)
        )
        
        // Neon ring around center
        drawCircle(
            color = NexusColors.Cyan.copy(alpha = 0.5f),
            radius = radius * 0.82f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f)
        )
    }
}
