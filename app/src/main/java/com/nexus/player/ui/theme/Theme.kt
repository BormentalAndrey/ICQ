package com.nexus.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NexusColors.NeonPink,
    secondary = NexusColors.Cyan,
    tertiary = NexusColors.Purple,
    background = NexusColors.BlackBackground,
    surface = NexusColors.DarkGrey,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = NexusColors.White,
    onSurface = NexusColors.White,
)

@Composable
fun NexusPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
