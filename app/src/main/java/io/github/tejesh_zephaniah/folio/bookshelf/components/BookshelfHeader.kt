package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfViewMode
import io.github.tejesh_zephaniah.folio.bookshelf.SortOrder
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioFilterListIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioMoreDotsIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.GlassSearchSurface
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassExpandedMenu
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassMenuItem
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassMenuSectionHeader
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode

@Composable
fun BookshelfHeader(
    title: String = "Library",
    viewMode: BookshelfViewMode,
    sortOrder: SortOrder,
    appThemeMode: AppThemeMode,
    onViewModeChange: (BookshelfViewMode) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onAppThemeChange: (AppThemeMode) -> Unit,
    onImportBookClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isAppearanceMenuOpen by remember { mutableStateOf(false) }
    var isLayoutMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High-character Editorial Serif Title (Apple Books style)
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
            color = FolioTheme.extendedColors.textPrimary
        )

        // Top-right circular translucent controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Import / Add Book Button
            LibraryGlassButton("Add book", onImportBookClick) {
                io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioAddIcon(
                    tint = FolioTheme.extendedColors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Three-horizontal-lines menu (Layout & Sorting)
            Box {
                LibraryGlassButton("Layout and sorting", { isLayoutMenuOpen = true }) {
                    FolioFilterListIcon(
                        tint = FolioTheme.extendedColors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                GlassExpandedMenu(
                    expanded = isLayoutMenuOpen,
                    onDismissRequest = { isLayoutMenuOpen = false },
                    offset = IntOffset(x = 0, y = 48),
                    transformOrigin = TransformOrigin(0.85f, 0.05f)
                ) {
                    GlassMenuSectionHeader(title = "VIEW")

                    BookshelfViewMode.entries.forEach { mode ->
                        GlassMenuItem(
                            text = mode.displayName,
                            isSelected = mode == viewMode,
                            onClick = {
                                onViewModeChange(mode)
                                isLayoutMenuOpen = false
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = FolioTheme.extendedColors.glassBorderEnd
                    )

                    GlassMenuSectionHeader(title = "SORT BY")

                    SortOrder.entries.forEach { sort ->
                        GlassMenuItem(
                            text = sort.displayName,
                            isSelected = sort == sortOrder,
                            onClick = {
                                onSortOrderChange(sort)
                                isLayoutMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Library appearance menu
            Box {
                LibraryGlassButton("Appearance", { isAppearanceMenuOpen = true }) {
                    FolioMoreDotsIcon(
                        tint = FolioTheme.extendedColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                GlassExpandedMenu(
                    expanded = isAppearanceMenuOpen,
                    onDismissRequest = { isAppearanceMenuOpen = false },
                    offset = IntOffset(x = 0, y = 48),
                    transformOrigin = TransformOrigin(0.95f, 0.05f)
                ) {
                    GlassMenuSectionHeader(title = "APPEARANCE")

                    AppThemeMode.entries.forEach { mode ->
                        GlassMenuItem(
                            text = mode.displayName,
                            isSelected = mode == appThemeMode,
                            onClick = {
                                onAppThemeChange(mode)
                                isAppearanceMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGlassButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    GlassSearchSurface(
        modifier = Modifier.size(42.dp),
        corner = 21.dp
    ) {
        Box(
            modifier = Modifier.matchParentSize().clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { icon() }
    }
}
