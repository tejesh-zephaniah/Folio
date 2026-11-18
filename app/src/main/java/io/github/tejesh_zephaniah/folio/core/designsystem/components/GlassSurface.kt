package io.github.tejesh_zephaniah.folio.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

/**
 * Clean, minimal and refined surface component.
 * Replaces the exaggerated Liquid Glass styling with calm, premium surfaces.
 * Preserves parameter signatures for seamless compatibility.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderStartColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    borderEndColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    elevation: Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val effectiveElevation = elevation.coerceAtMost(3.dp)
    val borderColor = if (borderStartColor != MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) {
        borderStartColor
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = effectiveElevation,
                shape = shape,
                ambientColor = FolioTheme.extendedColors.glassShadow,
                spotColor = FolioTheme.extendedColors.glassShadow
            )
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape),
        content = content
    )
}
