package com.nexus.player.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import com.nexus.player.ui.theme.NexusColors
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs

@Composable
fun GlitchArtWork(
    modifier: Modifier = Modifier,
    albumArtUri: android.net.Uri? = null,
    isPlaying: Boolean = false,
    onTrackChange: Boolean = false
) {
    var glitchOffset by remember { mutableStateOf(0f) }
    var isGlitching by remember { mutableStateOf(false) }
    var horizontalSliceOffset by remember { mutableStateOf(0f) }
    var colorShift by remember { mutableStateOf(0f) }
    
    val shake = remember { Animatable(0f) }
    val colorAnim = remember { Animatable(0f) }
    val scanlineOffset = remember { Animatable(0f) }
    
    // Continuous scanline animation when playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                scanlineOffset.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(2000, easing = LinearEasing)
                )
                scanlineOffset.snapTo(0f)
            }
        }
    }
    
    // Glitch effect on track change
    LaunchedEffect(onTrackChange) {
        if (onTrackChange) {
            isGlitching = true
            
            repeat(5) {
                glitchOffset = Random.nextFloat() * 30f - 15f
                horizontalSliceOffset = Random.nextFloat() * 10f - 5f
                colorShift = Random.nextFloat() * 20f - 10f
                
                shake.snapTo(glitchOffset)
                colorAnim.snapTo(colorShift)
                
                delay(50)
                
                shake.animateTo(0f, animationSpec = spring(Spring.DampingRatioLowBouncy))
                colorAnim.animateTo(0f, animationSpec = spring(Spring.DampingRatioLowBouncy))
                
                delay(Random.nextLong(50, 150))
            }
            
            isGlitching = false
            glitchOffset = 0f
            horizontalSliceOffset = 0f
            colorShift = 0f
        }
    }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Main album art with glitch effects
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Draw background
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        NexusColors.Purple.copy(alpha = 0.8f),
                        NexusColors.Cyan.copy(alpha = 0.8f)
                    )
                ),
                size = size
            )
            
            // Draw placeholder album art (circle with neon border)
            val center = Offset(canvasWidth / 2, canvasHeight / 2)
            val radius = minOf(canvasWidth, canvasHeight) * 0.35f
            
            // Glitch effect layers
            if (isGlitching || abs(glitchOffset) > 0.1f) {
                // Red channel offset
                drawCircle(
                    color = Color.Red.copy(alpha = 0.5f),
                    radius = radius,
                    center = Offset(center.x + glitchOffset * 3, center.y)
                )
                
                // Blue channel offset
                drawCircle(
                    color = Color.Blue.copy(alpha = 0.5f),
                    radius = radius,
                    center = Offset(center.x - glitchOffset * 3, center.y)
                )
                
                // Horizontal slice displacement
                val sliceY = center.y + horizontalSliceOffset * canvasHeight
                val sliceHeight = 20f
                
                clipRect(
                    top = sliceY,
                    left = 0f,
                    right = canvasWidth,
                    bottom = sliceY + sliceHeight
                ) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = radius,
                        center = Offset(center.x + glitchOffset * 10, sliceY + sliceHeight / 2)
                    )
                }
            }
            
            // Main circle with gradient
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        NexusColors.NeonPink,
                        NexusColors.Purple,
                        NexusColors.Cyan,
                        NexusColors.NeonPink
                    )
                ),
                radius = radius,
                center = center
            )
            
            // Neon ring
            drawCircle(
                color = NexusColors.Cyan.copy(alpha = 0.8f),
                radius = radius + 4f,
                center = center,
                style = Stroke(width = 2f)
            )
            
            drawCircle(
                color = NexusColors.NeonPink.copy(alpha = 0.8f),
                radius = radius - 4f,
                center = center,
                style = Stroke(width = 2f)
            )
            
            // Inner decorative elements
            for (i in 0 until 8) {
                val angle = (i * 45f + (colorShift * 2)) * (Math.PI / 180f).toFloat()
                val innerX = center.x + cos(angle) * radius * 0.7f
                val innerY = center.y + sin(angle) * radius * 0.7f
                
                drawCircle(
                    color = NexusColors.White.copy(alpha = 0.5f),
                    radius = 4f,
                    center = Offset(innerX, innerY)
                )
            }
            
            // Scanline effect
            val scanlineY = scanlineOffset.value * canvasHeight
            drawLine(
                color = NexusColors.White.copy(alpha = 0.1f),
                start = Offset(0f, scanlineY),
                end = Offset(canvasWidth, scanlineY),
                strokeWidth = 2f
            )
            
            // Grid overlay
            val gridSpacing = 20f
            for (i in 1 until (canvasWidth / gridSpacing).toInt()) {
                drawLine(
                    color = NexusColors.White.copy(alpha = 0.03f),
                    start = Offset(i * gridSpacing, 0f),
                    end = Offset(i * gridSpacing, canvasHeight),
                    strokeWidth = 0.5f
                )
            }
            for (i in 1 until (canvasHeight / gridSpacing).toInt()) {
                drawLine(
                    color = NexusColors.White.copy(alpha = 0.03f),
                    start = Offset(0f, i * gridSpacing),
                    end = Offset(canvasWidth, i * gridSpacing),
                    strokeWidth = 0.5f
                )
            }
        }
        
        // Play/Pause indicator overlay
        if (isPlaying) {
            Canvas(modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
            ) {
                val indicatorSize = size.minDimension * 0.6f
                val indicatorOffset = (size.minDimension - indicatorSize) / 2
                
                // Equalizer bars animation
                val barCount = 5
                val barWidth = indicatorSize / (barCount * 2f)
                val maxBarHeight = indicatorSize * 0.8f
                
                for (i in 0 until barCount) {
                    val heightFraction = (sin(System.currentTimeMillis() * 0.005 + i * 0.5) + 1) / 2
                    val barHeight = maxBarHeight * heightFraction.toFloat() + 4f
                    
                    val x = indicatorOffset + (i * 2 + 1) * barWidth
                    val y = indicatorOffset + (indicatorSize - barHeight) / 2
                    
                    drawRoundRect(
                        color = NexusColors.NeonPink,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}
