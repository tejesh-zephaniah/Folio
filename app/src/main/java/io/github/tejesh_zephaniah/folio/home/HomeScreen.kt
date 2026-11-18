package io.github.tejesh_zephaniah.folio.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioLibraryIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassSurface
import io.github.tejesh_zephaniah.folio.core.designsystem.components.TactileBookCover
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book

@Composable
fun HomeScreen(
    continueReadingBook: Book?,
    recentlyReadBooks: List<Book>,
    onBookOpen: (Book) -> Unit,
    onBookOptions: (Book) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp) // Clear bottom pill bar
        ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.displayLarge,
                    color = FolioTheme.extendedColors.textPrimary
                )
            }

            if (continueReadingBook != null) {
                // Continue Reading Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CONTINUE READING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = FolioTheme.extendedColors.textSecondary
                    )

                    // Hero Card
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onClick = { onBookOpen(continueReadingBook) },
                                onLongClick = { onBookOptions(continueReadingBook) },
                                onLongClickLabel = "Book options"
                            ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tactile Cover
                            TactileBookCover(
                                title = continueReadingBook.title,
                                author = continueReadingBook.author,
                                palette = continueReadingBook.palette,
                                style = continueReadingBook.coverStyle,
                                coverPath = continueReadingBook.coverPath,
                                modifier = Modifier.width(96.dp),
                                elevation = 8.dp
                            )

                            Spacer(modifier = Modifier.width(18.dp))

                            // Details & Progress
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = continueReadingBook.title,
                                    fontSize = 19.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = FolioTheme.extendedColors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = continueReadingBook.author,
                                    fontSize = 14.sp,
                                    color = FolioTheme.extendedColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Progress row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${continueReadingBook.progressPercentage}% done",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FolioTheme.extendedColors.accent
                                    )

                                    Text(
                                        text = "Page ${continueReadingBook.currentPage} of ${continueReadingBook.totalPages}",
                                        fontSize = 12.sp,
                                        color = FolioTheme.extendedColors.textTertiary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(FolioTheme.extendedColors.textTertiary.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((continueReadingBook.progressPercentage / 100f).coerceIn(0.05f, 1f))
                                            .height(5.dp)
                                            .clip(CircleShape)
                                            .background(FolioTheme.extendedColors.accent)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Resume Action Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(FolioTheme.extendedColors.textPrimary)
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Resume",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FolioTheme.extendedColors.glassBackground
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Recently Read Section
                if (recentlyReadBooks.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "RECENTLY READ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = FolioTheme.extendedColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = recentlyReadBooks,
                                key = { it.id }
                            ) { book ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .combinedClickable(
                                            onClick = { onBookOpen(book) },
                                            onLongClick = { onBookOptions(book) },
                                            onLongClickLabel = "Book options"
                                        ),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    TactileBookCover(
                                        title = book.title,
                                        author = book.author,
                                        palette = book.palette,
                                        style = book.coverStyle,
                                        coverPath = book.coverPath,
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = 8.dp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = book.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FolioTheme.extendedColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = "${book.progressPercentage}% complete",
                                        fontSize = 11.5.sp,
                                        color = FolioTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty Reading History State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            FolioLibraryIcon(
                                tint = FolioTheme.extendedColors.accent,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Your Reading Journey Starts Here",
                            style = MaterialTheme.typography.titleLarge,
                            color = FolioTheme.extendedColors.textPrimary
                        )

                        Text(
                            text = "Explore your library and pick your first book to begin reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FolioTheme.extendedColors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onNavigateToLibrary,
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FolioTheme.extendedColors.accent
                            )
                        ) {
                            Text(
                                text = "Browse Library",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
