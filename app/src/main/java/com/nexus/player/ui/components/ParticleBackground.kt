package com.nexus.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nexus.player.ui.theme.NexusColors
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    val speedX: Float,
    val speedY: Float,
    val size: Float,
    val alpha: Float,
    val color: Color,
    var life: Float
)

@Composable
fun ParticleBackground(modifier: Modifier = Modifier) {
    val particles = remember {
        List(100) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.3f,
                speedY = (Random.nextFloat() - 0.5f) * 0.3f,
                size = Random.nextFloat() * 4f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.1f,
                color = when (Random.nextInt(4)) {
                    0 -> NexusColors.Cyan
                    1 -> NexusColors.NeonPink
                    2 -> NexusColors.Purple
                    else -> NexusColors.ElectricBlue
                },
                life = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val progress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_progress"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            // Update particle position
            var x = (particle.x + progress.value * particle.speedX) % 1.05f
            var y = (particle.y + progress.value * particle.speedY) % 1.05f
            
            if (x < -0.05f) x += 1.1f
            if (y < -0.05f) y += 1.1f
            
            // Fade based on life
            val lifePhase = (progress.value + particle.life) % 1f
            val alpha = when {
                lifePhase < 0.2f -> lifePhase / 0.2f
                lifePhase > 0.8f -> (1f - lifePhase) / 0.2f
                else -> 1f
            } * particle.alpha
            
            // Draw particle with glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        particle.color.copy(alpha = alpha),
                        particle.color.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    )
                ),
                radius = particle.size * 3f,
                center = Offset(x * size.width, y * size.height)
            )
            
            // Core
            drawCircle(
                color = particle.color.copy(alpha = alpha * 1.5f),
                radius = particle.size,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}
