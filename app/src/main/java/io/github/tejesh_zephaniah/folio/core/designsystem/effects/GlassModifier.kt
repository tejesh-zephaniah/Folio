package io.github.tejesh_zephaniah.folio.core.designsystem.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a Liquid Glass visual treatment:
 * - Ambient elevation shadow
 * - Translucent tint and edge reflection
 * - Light-catching 1dp gradient border simulating specular reflection
 */
fun Modifier.liquidGlass(
    shape: Shape,
    backgroundColor: Color,
    borderStartColor: Color,
    borderEndColor: Color,
    elevation: Dp = 2.dp,
    shadowColor: Color = Color(0x12000000)
): Modifier {
    val effectiveElevation = elevation.coerceAtMost(2.dp)
    var modifier: Modifier = this

    if (effectiveElevation > 0.dp) {
        modifier = modifier.shadow(
            elevation = effectiveElevation,
            shape = shape,
            ambientColor = shadowColor,
            spotColor = shadowColor
        )
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(borderStartColor, borderEndColor)
    )

    return modifier
        .clip(shape)
        .background(color = backgroundColor, shape = shape)
        .border(
            width = 1.dp,
            brush = borderBrush,
            shape = shape
        )
}
