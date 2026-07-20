package com.nexus.player.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.player.ui.theme.CyberpunkFontFamily
import com.nexus.player.ui.theme.NexusColors

@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NexusColors.NeonPink,
    fontSize: TextUnit = 24.sp,
    glowRadius: Float = 10f,
    fontWeight: FontWeight = FontWeight.Bold
) {
    Text(
        text = text,
        modifier = modifier
            .padding(4.dp)
            .drawBehind {
                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    radius = size.maxDimension * 0.7f,
                    center = Offset(size.width / 2, size.height / 2)
                )
            },
        style = TextStyle(
            fontFamily = CyberpunkFontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            shadow = Shadow(
                color = color.copy(alpha = 0.8f),
                blurRadius = glowRadius,
                offset = Offset(0f, 0f)
            )
        )
    )
}

@Composable
fun NeonGradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    Text(
        text = text,
        modifier = modifier.padding(4.dp),
        style = TextStyle(
            fontFamily = CyberpunkFontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            brush = Brush.linearGradient(
                colors = listOf(
                    NexusColors.NeonPink,
                    NexusColors.Cyan,
                    NexusColors.Purple
                )
            ),
            shadow = Shadow(
                color = NexusColors.NeonPink.copy(alpha = 0.5f),
                blurRadius = 15f,
                offset = Offset(0f, 0f)
            )
        )
    )
}
