package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfFilter
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioSearchIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.GlassSearchSurface
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

@Composable
fun SearchFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: BookshelfFilter,
    onFilterSelected: (BookshelfFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Clean Search Field
        GlassSearchSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolioSearchIcon(
                    tint = FolioTheme.extendedColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                val fieldStyle = TextStyle(
                    color = FolioTheme.extendedColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(FolioTheme.extendedColors.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search books, authors, genres...",
                                    style = fieldStyle.copy(color = FolioTheme.extendedColors.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                AnimatedVisibility(
                    visible = searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onSearchQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        FolioCloseIcon(
                            tint = FolioTheme.extendedColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookshelfFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = { onFilterSelected(filter) }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = filter.displayName,
                        color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.background
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
