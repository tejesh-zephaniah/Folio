package io.github.tejesh_zephaniah.folio.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioBookmarkIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioFilterListIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioSearchIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassCircleButton
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

@Composable
fun ReaderTopBar(
    bookTitle: String,
    visible: Boolean,
    isBookmarked: Boolean,
    onCloseClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onTocClick: () -> Unit = {},
    onBookmarksClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close "✕" Button (Apple Books style)
            GlassCircleButton(
                onClick = onCloseClick,
                size = 38.dp
            ) {
                FolioCloseIcon(
                    tint = FolioTheme.extendedColors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Book Title
            Text(
                text = bookTitle,
                color = FolioTheme.extendedColors.textPrimary.copy(alpha = 0.85f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            // Right Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Table of contents
                GlassCircleButton(
                    onClick = onTocClick,
                    size = 36.dp
                ) {
                    FolioFilterListIcon(
                        tint = FolioTheme.extendedColors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Search in publication
                GlassCircleButton(
                    onClick = onSearchClick,
                    size = 36.dp
                ) {
                    FolioSearchIcon(
                        tint = FolioTheme.extendedColors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bookmarks list / toggle
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = onBookmarksClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FolioBookmarkIcon(
                        tint = if (isBookmarked) FolioTheme.extendedColors.accent else FolioTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(18.dp),
                        isBookmarked = isBookmarked
                    )
                }
            }
        }
    }
}
