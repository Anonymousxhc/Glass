package com.glassbill.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

enum class GlassQuality {
    Full,
    BlurOnly,
    Flat,
}

val LocalGlassQuality = compositionLocalOf { GlassQuality.Full }

private val LightPalette = lightColorScheme(
    primary = Color(0xFF146C56),
    secondary = Color(0xFF2D5D8C),
    background = Color(0xFFF4F8F6),
    surface = Color(0xFFFBFDFC),
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF79D2B4),
    secondary = Color(0xFFB1CCF2),
    background = Color(0xFF101514),
    surface = Color(0xFF171D1B),
)

@Composable
fun GlassBillTheme(
    quality: GlassQuality,
    useDarkPalette: Boolean = false,
    content: @Composable () -> Unit,
) {
    val materialColors = if (useDarkPalette) DarkPalette else LightPalette
    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(colors = if (useDarkPalette) miuixDarkColorScheme() else miuixLightColorScheme()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassQuality provides quality) {
                content()
            }
        }
    }
}
