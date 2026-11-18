package io.github.tejesh_zephaniah.folio.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

internal fun Modifier.browsingBackdrop(theme: AppThemeMode, background: Color): Modifier =
    when (theme) {
        AppThemeMode.BLUSH_BLOOM -> blushBackdrop(background)
        AppThemeMode.WALNUT_LIBRARY -> walnutBackdrop(background)
        AppThemeMode.COPPER_HORIZON -> copperBackdrop(background)
        else -> background(background)
    }

private fun Modifier.blushBackdrop(background: Color) = drawWithCache {
    val satin = Brush.linearGradient(
        0f to Color(0xFFF3AFCB),
        0.34f to Color(0xFFFFF0F7),
        0.59f to background,
        1f to Color(0xFFEDAAC7),
        end = Offset(size.width * 0.92f, size.height * 0.78f)
    )
    val shine = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.73f), Color.Transparent),
        center = Offset(size.width * 0.27f, size.height * 0.10f),
        radius = size.maxDimension * 0.48f
    )
    val blossoms = listOf(
        Offset(size.width * 0.89f, size.height * 0.31f) to 8f * density,
        Offset(size.width * 0.95f, size.height * 0.34f) to 4f * density,
        Offset(size.width * 0.08f, size.height * 0.83f) to 6f * density
    )
    onDrawBehind {
        drawRect(satin)
        drawRect(shine)
        for ((center, radius) in blossoms) {
            repeat(5) { petal ->
                rotate(petal * 72f, center) {
                    val tip = center - Offset(radius * 0.42f, radius * 1.4f)
                    val petalSize = Size(radius * 0.84f, radius * 1.18f)
                    drawOval(Color.White.copy(alpha = 0.56f), tip, petalSize)
                    drawOval(Color(0x24AD4778), tip, petalSize, style = Stroke(0.6f * density))
                }
            }
            drawCircle(Color(0x5096466A), radius * 0.21f, center)
        }
    }
}

private fun Modifier.walnutBackdrop(background: Color) = drawWithCache {
    val wood = Brush.horizontalGradient(
        0f to Color(0xFFC39674),
        0.16f to Color(0xFFDBB596),
        0.43f to background,
        0.73f to Color(0xFFD8AE8D),
        1f to Color(0xFFBB8968)
    )
    val light = Brush.radialGradient(
        listOf(Color(0x70FFF1D9), Color.Transparent),
        center = Offset(size.width * 0.38f, size.height * 0.21f),
        radius = size.maxDimension * 0.68f
    )
    val random = Random(49)
    // Grain bands tighten at different heights, tracing the cathedrals of flat-sawn walnut.
    val grain = List(134) { index ->
        val across = index / 133f * 1.36f - 0.18f + random.nextFloat() * 0.012f
        val phase = random.nextFloat() * 0.7f
        val knot = 0.47f + across * 0.12f
        val path = Path()
        repeat(41) { point ->
            val y = point / 40f
            val narrowing = exp(-((y - knot) * 4.2f) * ((y - knot) * 4.2f))
            val drift = sin(y * 5.2f + across * 4.4f) * 0.053f +
                sin(y * 16f + across * 11f + phase) * 0.009f
            val x = across - (across - 0.52f) * narrowing * 0.33f + drift
            if (point == 0) path.moveTo(x * size.width, 0f)
            else path.lineTo(x * size.width, y * size.height)
        }
        val color = if (index % 4 == 0) Color(0xFFFFE6CC) else Color(0xFF613820)
        val opacity = if (index % 11 == 0) 0.25f else 0.06f + random.nextFloat() * 0.11f
        Triple(path, color.copy(alpha = opacity), (0.38f + random.nextFloat() * 0.9f) * density)
    }
    onDrawBehind {
        drawRect(wood)
        for ((path, color, width) in grain) drawPath(path, color, style = Stroke(width))
        drawRect(light)
    }
}

private fun Modifier.copperBackdrop(background: Color) = drawWithCache {
    val alloy = Brush.linearGradient(
        0f to Color(0xFFC0CED2),
        0.32f to background,
        0.54f to Color(0xFFF4EFEB),
        0.83f to Color(0xFFE3C3B2),
        1f to Color(0xFFD2ADA0),
        end = Offset(size.width * 0.90f, size.height)
    )
    val copper = Brush.linearGradient(
        0f to Color(0x58AC6F53),
        0.43f to Color(0xB0FFFAEB),
        0.68f to Color(0x70D79775),
        1f to Color(0x7CB17C67),
        start = Offset(0f, size.height * 0.56f),
        end = Offset(size.width, size.height)
    )
    val horizon = Offset(size.width * 0.91f, size.height * 0.83f)
    onDrawBehind {
        drawRect(alloy)
        // Offset rims give the engraved arcs a lit edge and a recessed side.
        rotate(-27f, pivot = horizon) {
            repeat(3) { ring ->
                val rimSize = Size(size.width * (1.66f - ring * 0.17f), size.height * (0.37f - ring * 0.05f))
                val rimOrigin = horizon - Offset(rimSize.width / 2f, rimSize.height / 2f)
                drawOval(Color(0x3F6A493E), rimOrigin + Offset(0f, density * 2f), rimSize, style = Stroke(3f * density))
                drawOval(copper, rimOrigin, rimSize, style = Stroke(3f * density))
                drawOval(Color.White.copy(alpha = 0.44f), rimOrigin - Offset(0f, density), rimSize, style = Stroke(0.7f * density))
            }
        }
    }
}
