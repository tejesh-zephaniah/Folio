package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioMoreDotsIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.TactileBookCover
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book

@Composable
fun BookGridItem(
    book: Book,
    onBookClick: (Book) -> Unit,
    onOptionsClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Book Cover with Realistic Depth and Embedded Artwork Support
        TactileBookCover(
            title = book.title,
            author = book.author,
            palette = book.palette,
            style = book.coverStyle,
            coverPath = book.coverPath,
            modifier = Modifier.fillMaxWidth(),
            elevation = 9.dp,
            onClick = { onBookClick(book) },
            onLongClick = { onOptionsClick(book) },
            libraryFinish = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Book title and author under cover with Material 3 type scale
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = FolioTheme.extendedColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = FolioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Metadata Row (Percentage on Left, 48dp hit-target 3-dots on Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val progressText = when {
                book.currentPage > 0 -> "${book.progressPercentage}%"
                else -> "Unread"
            }

            Text(
                text = progressText,
                style = MaterialTheme.typography.labelSmall,
                color = FolioTheme.extendedColors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            // Three dots action button with 48x48dp accessible touch target
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, radius = 20.dp),
                        onClick = { onOptionsClick(book) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                FolioMoreDotsIcon(
                    tint = FolioTheme.extendedColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BookListItem(
    book: Book,
    onBookClick: (Book) -> Unit,
    onOptionsClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onBookClick(book) },
                onLongClick = { onOptionsClick(book) },
                onLongClickLabel = "Book options"
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Miniature Cover Thumbnail
        TactileBookCover(
            title = book.title,
            author = book.author,
            palette = book.palette,
            style = book.coverStyle,
            coverPath = book.coverPath,
            modifier = Modifier.width(52.dp),
            elevation = 4.dp,
            libraryFinish = true
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Book Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = FolioTheme.extendedColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${book.author} • ${book.genre}",
                style = MaterialTheme.typography.bodySmall,
                color = FolioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val progressText = when {
                book.isFinished && book.progressPercentage == 100 -> "Completed"
                book.currentPage > 0 -> "${book.progressPercentage}% complete • Page ${book.currentPage} of ${book.totalPages}"
                else -> "Not started"
            }

            Text(
                text = progressText,
                color = FolioTheme.extendedColors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Accessible 48x48dp 3-dots touch target
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 20.dp),
                    onClick = { onOptionsClick(book) }
                ),
            contentAlignment = Alignment.Center
        ) {
            FolioMoreDotsIcon(
                tint = FolioTheme.extendedColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
