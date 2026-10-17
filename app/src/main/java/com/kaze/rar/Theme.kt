package com.kaze.rar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object KazeColors {
    val Bg = Color(0xFF0A0E1F)
    val Surface = Color(0xFF131A33)
    val SurfaceHigh = Color(0xFF1B2447)
    val Primary = Color(0xFF7C5CFF)
    val Secondary = Color(0xFF22D3EE)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFFB7185)
    val TextHi = Color(0xFFF1F3FF)
    val TextLo = Color(0xFF9AA3C7)
}

val KazeBrush = Brush.linearGradient(listOf(KazeColors.Primary, KazeColors.Secondary))
val SuccessBrush = Brush.linearGradient(listOf(KazeColors.Success, KazeColors.Secondary))
val DangerBrush = Brush.linearGradient(listOf(KazeColors.Danger, Color(0xFFFB923C)))

@Composable
fun KazeTheme(content: @Composable () -> Unit) {
    // The app is intentionally always dark: the palette is tuned for it.
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KazeColors.Primary,
            onPrimary = Color.White,
            secondary = KazeColors.Secondary,
            background = KazeColors.Bg,
            onBackground = KazeColors.TextHi,
            surface = KazeColors.Surface,
            onSurface = KazeColors.TextHi,
            surfaceVariant = KazeColors.SurfaceHigh,
            onSurfaceVariant = KazeColors.TextLo,
            error = KazeColors.Danger
        ),
        typography = Typography(),
        content = content
    )
}
