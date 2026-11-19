package io.github.tejesh_zephaniah.folio.core.designsystem.effects

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

internal class GlassSource(val layer: GraphicsLayer) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
}

@Composable
internal fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

internal val LocalSearchGlassSource = staticCompositionLocalOf<GlassSource?> { null }

private data class GlassEffectKey(
    val size: IntSize,
    val density: Float,
    val blurRadius: Float,
    val dark: Boolean,
    val lens: Boolean,
    val cornerRadius: Float
)

private val glassEffects = LruCache<GlassEffectKey, androidx.compose.ui.graphics.RenderEffect>(24)

internal fun Modifier.captureGlassSource(source: GlassSource): Modifier {
    if (Build.VERSION.SDK_INT < 31) return this
    return onGloballyPositioned { source.coordinates = it }.drawWithContent {
        source.layer.record { this@drawWithContent.drawContent() }
        drawLayer(source.layer)
    }
}

@Composable
internal fun rememberGlassRim(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    return remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.34f else 0.86f),
            0.50f to Color.White.copy(alpha = if (dark) 0.09f else 0.34f),
            1f to Color.Black.copy(alpha = if (dark) 0.60f else 0.16f)
        )
    }
}

@Composable
internal fun GlassSearchSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val source = LocalSearchGlassSource.current
    val rim = rememberGlassRim()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier.onGloballyPositioned { coordinates = it }) {
        GlassBackdrop(
            source = source,
            targetCoordinates = { coordinates },
            cornerRadius = corner,
            modifier = Modifier.matchParentSize().shadow(1.dp, shape).clip(shape).border(1.dp, rim, shape)
        )
        content()
    }
}

@Composable
internal fun GlassBackdrop(
    source: GlassSource?,
    targetCoordinates: () -> LayoutCoordinates?,
    modifier: Modifier = Modifier,
    sampleOffset: () -> Offset = { Offset.Zero },
    isLens: Boolean = false,
    cornerRadius: Dp = Dp.Unspecified
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.45f
    val crop = rememberGraphicsLayer()
    val fallback = if (isLens || cornerRadius != Dp.Unspecified) colors.surfaceVariant else colors.surface

    Box(modifier.drawWithCache {
        val downsample = if (Build.VERSION.SDK_INT >= 33) 0.4f else 0.5f
        val cropSize = IntSize(ceil(size.width * downsample).toInt(), ceil(size.height * downsample).toInt())
        if (source != null && Build.VERSION.SDK_INT >= 31 && cropSize.width > 0 && cropSize.height > 0) {
            val sampleDensity = density * downsample
            val blurRadius = (if (isLens) 4f else 12f) * sampleDensity
            val radius = if (cornerRadius == Dp.Unspecified) cropSize.height * 0.5f
                else cornerRadius.toPx() * downsample
            val key = GlassEffectKey(cropSize, sampleDensity, blurRadius, dark, isLens, radius)
            crop.renderEffect = glassEffects.get(key) ?: run {
                val effect = if (Build.VERSION.SDK_INT >= 33) {
                    glassOptics(cropSize, sampleDensity, blurRadius, dark, isLens, radius)
                } else {
                    contrastBlur(blurRadius, dark)
                }
                effect.asComposeRenderEffect().also { glassEffects.put(key, it) }
            }
            crop.clip = true
        }
        onDrawBehind {
            val sourceCoordinates = source?.coordinates
            val target = targetCoordinates()
            if (Build.VERSION.SDK_INT >= 31 && source != null && sourceCoordinates?.isAttached == true &&
                target?.isAttached == true && cropSize.width > 0 && cropSize.height > 0
            ) {
                val origin = sourceCoordinates.localPositionOf(target, sampleOffset())
                crop.record(size = cropSize) {
                    scale(downsample, pivot = Offset.Zero) {
                        translate(-origin.x, -origin.y) { drawLayer(source.layer) }
                    }
                }
                scale(1f / downsample, pivot = Offset.Zero) { drawLayer(crop) }
            } else {
                drawRect(fallback)
            }
        }
    })
}

@RequiresApi(31)
private fun contrastBlur(radius: Float, dark: Boolean): RenderEffect {
    val offset = if (dark) 0f else 168f
    val matrix = ColorMatrix(floatArrayOf(
        0.34f, 0f, 0f, 0f, offset,
        0f, 0.34f, 0f, 0f, offset,
        0f, 0f, 0.34f, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    ))
    return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), frost(radius))
}

@RequiresApi(31)
private fun frost(radius: Float) = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)

@RequiresApi(33)
private fun glassOptics(
    size: IntSize,
    density: Float,
    blurRadius: Float,
    dark: Boolean,
    isLens: Boolean,
    radius: Float
): RenderEffect {
    val shader = RuntimeShader(
        """
        uniform shader backdrop;
        uniform float2 bounds;
        uniform float density;
        uniform float dark;
        uniform float lens;
        uniform float radius;
        uniform float frostRadius;

        half4 sampleAt(float2 pixel) {
            return backdrop.eval(clamp(pixel, float2(0.5), bounds - 0.5));
        }

        half4 main(float2 pixel) {
            float2 fromCenter = pixel - bounds * 0.5;
            float2 inner = abs(fromCenter) - (bounds * 0.5 - radius);
            float2 rim = sign(fromCenter) * max(inner, float2(0.0));
            float distanceToRim = radius - length(rim) - min(max(inner.x, inner.y), 0.0);
            float bevel = 1.0 - smoothstep(0.0, 9.0 * density, distanceToRim);
            float2 opticalOffset = rim / max(length(rim), 1.0) * bevel * bevel * (4.0 * density);

            float2 scaledLens = fromCenter / (bounds * 0.5);
            float focus = 1.0 - smoothstep(0.54, 1.08, length(scaledLens));
            opticalOffset -= fromCenter * focus * lens * 0.28;

            // Filter the refracted sample in one pass over the reduced-resolution crop.
            float2 point = pixel + opticalOffset;
            float x = frostRadius * 0.65;
            float y = x * 0.75;
            half4 frosted = sampleAt(point) * 0.25;
            frosted += (sampleAt(point + float2(x, 0.0)) + sampleAt(point - float2(x, 0.0))
                      + sampleAt(point + float2(0.0, y)) + sampleAt(point - float2(0.0, y))) * 0.125;
            frosted += (sampleAt(point + float2(x, y)) + sampleAt(point + float2(-x, y))
                      + sampleAt(point + float2(x, -y)) + sampleAt(point - float2(x, y))) * 0.0625;
            float brightness = dot(float3(frosted.rgb), float3(0.2126, 0.7152, 0.0722));

            // Compress only the brightness range that competes with the theme's label ink.
            float level = mix(1.0 - brightness, brightness, dark);
            float excess = max(level - 0.20, 0.0);
            float compressed = min(level, 0.20) + 0.14 * excess / (excess + 0.14);
            float correction = (level - compressed) / max(level, 0.001);
            frosted.rgb = mix(frosted.rgb, half3(1.0 - dark) * frosted.a, half(correction));
            return frosted;
        }
        """.trimIndent()
    ).apply {
        setFloatUniform("bounds", size.width.toFloat(), size.height.toFloat())
        setFloatUniform("density", density)
        setFloatUniform("dark", if (dark) 1f else 0f)
        setFloatUniform("lens", if (isLens) 1f else 0f)
        setFloatUniform("radius", radius)
        setFloatUniform("frostRadius", blurRadius)
    }
    return RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
}
