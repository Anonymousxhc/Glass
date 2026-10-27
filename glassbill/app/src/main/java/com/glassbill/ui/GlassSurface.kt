package com.glassbill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Original GlassBill chrome backed by the Apache-licensed Backdrop primitives.
 * Flat mode is retained for accessibility and devices where effects are unsuitable.
 */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop,
    quality: GlassQuality,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tint = when (quality) {
        GlassQuality.Full -> Color.White.copy(alpha = 0.24f)
        GlassQuality.BlurOnly -> Color.White.copy(alpha = 0.44f)
        GlassQuality.Flat -> Color(0xFFF7FAF8)
    }
    val surfaceModifier = when (quality) {
        GlassQuality.Flat -> Modifier.clip(shape).background(tint)
        GlassQuality.BlurOnly -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(8.dp.toPx())
                vibrancy()
            },
            highlight = { Highlight.Default },
            shadow = { Shadow.Default },
            innerShadow = { InnerShadow.Default },
            onDrawSurface = { drawRect(tint) },
        )
        GlassQuality.Full -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(8.dp.toPx())
                vibrancy()
                lens(24.dp.toPx(), 24.dp.toPx(), depthEffect = true)
            },
            highlight = { Highlight.Default },
            shadow = { Shadow.Default },
            innerShadow = { InnerShadow.Default },
            onDrawSurface = { drawRect(tint) },
        )
    }
    Box(modifier = modifier.then(surfaceModifier), content = { content() })
}
