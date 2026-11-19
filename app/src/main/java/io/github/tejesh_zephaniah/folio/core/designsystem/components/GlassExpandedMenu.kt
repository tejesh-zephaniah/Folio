package io.github.tejesh_zephaniah.folio.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

/**
 * A bespoke Liquid Glass Popup Menu that anchors to and naturally expands
 * directly from its triggering button using physics-based spring animation.
 */
@Composable
fun GlassExpandedMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset(0, 0),
    transformOrigin: TransformOrigin = TransformOrigin(0.92f, 0.05f),
    content: @Composable ColumnScope.() -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (visibility.currentState || visibility.targetState) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = offset,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = scaleIn(
                    transformOrigin = transformOrigin,
                    initialScale = 0.96f,
                    animationSpec = tween(110)
                ) + fadeIn(animationSpec = tween(95)),
                exit = scaleOut(
                    transformOrigin = transformOrigin,
                    targetScale = 0.97f,
                    animationSpec = tween(85)
                ) + fadeOut(animationSpec = tween(85))
            ) {
                GlassSurface(
                    modifier = modifier
                        .width(220.dp)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
fun GlassMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) FolioTheme.extendedColors.accent.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) FolioTheme.extendedColors.accent else FolioTheme.extendedColors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            FolioCheckmarkIcon(
                tint = FolioTheme.extendedColors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun GlassMenuSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = FolioTheme.extendedColors.textSecondary,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
