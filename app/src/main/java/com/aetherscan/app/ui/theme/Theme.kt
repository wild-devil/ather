package com.aetherscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonCyan = Color(0xFF7DD3FC)
val NeonGreen = Color(0xFF34D399)
val NeonPink = Color(0xFFF472B6)
val NeonPurple = Color(0xFFA78BFA)
val BgDeep = Color(0xFF070B18)
val SurfaceGlass = Color(0x99101831)
val CardGlass = Color(0x73121C38)
val BorderGlow = Color(0x2E8DA2FF)
val TextPrimary = Color(0xFFE0F2FE)
val TextDim = Color(0xFF94A3B8)

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonGreen,
    tertiary = NeonPink,
    background = BgDeep,
    surface = SurfaceGlass,
    onPrimary = BgDeep,
    onSecondary = BgDeep,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderGlow
)

@Composable
fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
