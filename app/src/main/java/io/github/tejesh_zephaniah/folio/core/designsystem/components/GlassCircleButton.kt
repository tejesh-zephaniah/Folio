package io.github.tejesh_zephaniah.folio.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

/**
 * Clean circular button surface with subtle elevation and refined tactile response.
 * Replaces the exaggerated shiny glass circle styling.
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonPressScale"
    )

    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 0.dp else 1.5.dp,
                shape = CircleShape,
                ambientColor = FolioTheme.extendedColors.glassShadow,
                spotColor = FolioTheme.extendedColors.glassShadow
            )
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
