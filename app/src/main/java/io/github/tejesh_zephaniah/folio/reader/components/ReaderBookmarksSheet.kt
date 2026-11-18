package io.github.tejesh_zephaniah.folio.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioAddIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioBookmarkIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.StoredBookmark
import io.github.tejesh_zephaniah.folio.core.storage.ReadiumManager
import io.github.tejesh_zephaniah.folio.data.model.bookPageFromLocator
import io.github.tejesh_zephaniah.folio.data.model.bookmarkPageTitle
import org.readium.r2.shared.publication.Locator

private val savedPageTitle = Regex("""Page (\d+) \(\d+%\)""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBookmarksSheet(
    visible: Boolean,
    bookmarks: List<StoredBookmark>,
    totalPages: Int,
    renderedPageOf: ((Locator) -> Int?)? = null,
    onDismiss: () -> Unit,
    onAddCurrentBookmark: () -> Unit,
    onBookmarkClick: (StoredBookmark) -> Unit,
    onDeleteBookmark: (StoredBookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bookmarks",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = FolioTheme.extendedColors.textPrimary
                )

                Button(
                    onClick = onAddCurrentBookmark,
                    colors = ButtonDefaults.buttonColors(containerColor = FolioTheme.extendedColors.accent),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    FolioAddIcon(tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Current", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FolioTheme.extendedColors.glassBorderEnd)
            Spacer(modifier = Modifier.height(10.dp))

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bookmarks saved for this book yet.",
                        fontSize = 14.sp,
                        color = FolioTheme.extendedColors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bm ->
                        val title = remember(bm, totalPages, renderedPageOf) {
                            val locator = ReadiumManager.parseLocatorJson(bm.locatorJson)
                            val locations = locator?.locations
                            val savedPage = savedPageTitle.matchEntire(bm.title)?.groupValues?.get(1)?.toIntOrNull()
                            val page = if (renderedPageOf != null) locator?.let(renderedPageOf) else {
                                bookPageFromLocator(locations?.position, locations?.totalProgression, totalPages)
                                    ?: savedPage?.takeIf { it in 1..totalPages }
                            }
                            page?.let { bookmarkPageTitle(it, totalPages) }
                                ?: bm.title.takeUnless { it.isBlank() || savedPage != null }
                                ?: "Page bookmark"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onBookmarkClick(bm)
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                FolioBookmarkIcon(
                                    tint = FolioTheme.extendedColors.accent,
                                    modifier = Modifier.size(20.dp),
                                    isBookmarked = true
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FolioTheme.extendedColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (bm.chapter.isNotBlank()) {
                                        Text(
                                            text = bm.chapter,
                                            fontSize = 12.5.sp,
                                            color = FolioTheme.extendedColors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (bm.excerpt.isNotBlank()) {
                                        Text(
                                            text = "\"${bm.excerpt}\"",
                                            fontSize = 12.sp,
                                            color = FolioTheme.extendedColors.textTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Delete bookmark button
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onDeleteBookmark(bm) }
                                    .padding(8.dp),
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
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
