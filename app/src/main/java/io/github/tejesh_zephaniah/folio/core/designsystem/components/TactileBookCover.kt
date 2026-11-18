package io.github.tejesh_zephaniah.folio.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.CoverPalette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val coverDecodeDispatcher = Dispatchers.IO.limitedParallelism(2)
private val coverBitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

enum class CoverStyle {
    BoldTypography,
    MinimalistFramed,
    EditorialModern,
    VintageClassic,
    GeometricAbstract
}

@Composable
fun TactileBookCover(
    title: String,
    author: String,
    palette: CoverPalette,
    style: CoverStyle,
    modifier: Modifier = Modifier,
    coverPath: String? = null,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    libraryFinish: Boolean = false
) {
    val cornerRadius = if (libraryFinish) 8.dp else 6.dp
    val shape = RoundedCornerShape(
        topStart = 2.dp, // Spine side is slightly flatter
        bottomStart = 2.dp,
        topEnd = cornerRadius,
        bottomEnd = cornerRadius
    )

    val interactionSource = remember { MutableInteractionSource() }

    var coverBitmap by remember(coverPath) { mutableStateOf(coverPath?.let(coverBitmaps::get)) }
    LaunchedEffect(coverPath) {
        if (!coverPath.isNullOrBlank() && coverBitmap == null) {
            coverBitmap = withContext(coverDecodeDispatcher) {
                val cached = coverBitmaps.get(coverPath)
                if (cached != null || !File(coverPath).isFile) cached
                else BitmapFactory.decodeFile(coverPath)?.also { coverBitmaps.put(coverPath, it) }
            }
        }
    }
    val imageBitmap = remember(coverBitmap) { coverBitmap?.asImageBitmap() }

    Box(
        modifier = modifier
            .aspectRatio(1f / 1.55f)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000)
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = if (onLongClick != null) "Book options" else null
                    )
                } else Modifier
            )
    ) {
        if (imageBitmap != null) {
            // Real Embedded Artwork (maintains aspect ratio, crops cleanly, no artificial text overlays)
            Image(
                bitmap = imageBitmap,
                contentDescription = "$title cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Base Cover Background Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(palette.primary, palette.secondary)
                        )
                    )
            )

            // Decorative Style Overlays
            when (style) {
                CoverStyle.MinimalistFramed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                }
                CoverStyle.GeometricAbstract -> {
                    GeometricCoverPattern(accentColor = palette.accent.copy(alpha = 0.25f))
                }
                CoverStyle.VintageClassic -> {
                    VintageCoverPattern(accentColor = palette.accent.copy(alpha = 0.35f))
                }
                CoverStyle.BoldTypography, CoverStyle.EditorialModern -> {
                    // Clean gradient background
                }
            }

            // Book Content (Title & Author)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Accent or Series mark
                Text(
                    text = "FOLIO EDITION",
                    color = palette.accent.copy(alpha = 0.75f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                // Book Title in Center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val titleFontSize = when {
                        title.length > 22 -> 15.sp
                        title.length > 10 -> 17.sp
                        else -> 21.sp
                    }
                    val titleLineHeight = when {
                        title.length > 22 -> 19.sp
                        title.length > 10 -> 22.sp
                        else -> 26.sp
                    }
                    Text(
                        text = title,
                        color = palette.textColor,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtle divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(1.dp)
                            .background(palette.accent.copy(alpha = 0.6f))
                    )
                }

                // Author at the bottom
                Text(
                    text = author.uppercase(),
                    color = palette.textColor.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Spine Crease & Book Depth Shading Layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Spine gutter shadow (dark blend on left edge)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color(0x66000000),
                    0.03f to Color(0x33000000),
                    0.08f to Color(0x15000000),
                    0.14f to Color.Transparent,
                    endX = w * 0.2f
                ),
                topLeft = Offset.Zero,
                size = Size(w * 0.2f, h)
            )

            // 2. Spine hinge highlight line
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(w * 0.05f, 0f),
                end = Offset(w * 0.05f, h),
                strokeWidth = 1.dp.toPx()
            )

            if (libraryFinish) {
                val hingeWidth = minOf(9.dp.toPx(), w * 0.14f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color(0x55000000),
                        0.22f to Color(0x45FFFFFF),
                        0.49f to Color(0x0AFFFFFF),
                        0.75f to Color(0x33000000),
                        1f to Color.Transparent,
                        endX = hingeWidth
                    ),
                    size = Size(hingeWidth, h)
                )
            }

            // 3. Right edge paper stack illusion (subtle light rim)
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(w - 1.dp.toPx(), 0f),
                end = Offset(w - 1.dp.toPx(), h),
                strokeWidth = 1.dp.toPx()
            )

            // 4. Top specular highlight
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.2f),
                    0.06f to Color.Transparent,
                    endY = h * 0.06f
                ),
                topLeft = Offset.Zero,
                size = Size(w, h * 0.06f)
            )
        }
    }
}

@Composable
private fun GeometricCoverPattern(accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Concentric circles in upper quadrant
        drawCircle(
            color = accentColor,
            radius = w * 0.45f,
            center = Offset(w * 0.7f, h * 0.35f)
        )
        drawCircle(
            color = accentColor.copy(alpha = accentColor.alpha * 0.6f),
            radius = w * 0.65f,
            center = Offset(w * 0.7f, h * 0.35f)
        )
    }
}

@Composable
private fun VintageCoverPattern(accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Corner decorative lines
        val padding = 16.dp.toPx()
        val cornerLen = 24.dp.toPx()

        // Top-left
        drawLine(accentColor, Offset(padding, padding), Offset(padding + cornerLen, padding), 2.dp.toPx())
        drawLine(accentColor, Offset(padding, padding), Offset(padding, padding + cornerLen), 2.dp.toPx())

        // Top-right
        drawLine(accentColor, Offset(w - padding, padding), Offset(w - padding - cornerLen, padding), 2.dp.toPx())
        drawLine(accentColor, Offset(w - padding, padding), Offset(w - padding, padding + cornerLen), 2.dp.toPx())

        // Bottom-left
        drawLine(accentColor, Offset(padding, h - padding), Offset(padding + cornerLen, h - padding), 2.dp.toPx())
        drawLine(accentColor, Offset(padding, h - padding), Offset(padding, h - padding - cornerLen), 2.dp.toPx())

        // Bottom-right
        drawLine(accentColor, Offset(w - padding, h - padding), Offset(w - padding - cornerLen, h - padding), 2.dp.toPx())
        drawLine(accentColor, Offset(w - padding, h - padding), Offset(w - padding, h - padding - cornerLen), 2.dp.toPx())
    }
}
