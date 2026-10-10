package com.abletonremote.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AbletonDark = Color(0xFF1E1E1E)
val AbletonSurface = Color(0xFF2D2D2D)
val AbletonCard = Color(0xFF3A3A3A)
val AbletonAccent = Color(0xFFFF764D)
val AbletonGreen = Color(0xFF00D17A)
val AbletonYellow = Color(0xFFDBDC00)
val AbletonBlue = Color(0xFF00B0F0)
val AbletonRed = Color(0xFFFF3636)
val AbletonTextPrimary = Color(0xFFE0E0E0)
val AbletonTextSecondary = Color(0xFF999999)

private val DarkColorScheme = darkColorScheme(
    primary = AbletonAccent,
    onPrimary = Color.White,
    secondary = AbletonBlue,
    background = AbletonDark,
    surface = AbletonSurface,
    surfaceVariant = AbletonCard,
    onBackground = AbletonTextPrimary,
    onSurface = AbletonTextPrimary,
    onSurfaceVariant = AbletonTextSecondary,
    error = AbletonRed
)

@Composable
fun AbletonRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
