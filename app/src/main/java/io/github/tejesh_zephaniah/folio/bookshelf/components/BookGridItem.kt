package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.bookshelf.ActiveExploreDownload
import io.github.tejesh_zephaniah.folio.core.designsystem.components.CoverStyle
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioMoreDotsIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.TactileBookCover
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.CoverPalette
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.delay

@Composable
fun BookGridItem(
    book: Book,
    onBookClick: (Book) -> Unit,
    onOptionsClick: (Book) -> Unit,
    isNew: Boolean = false,
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
            libraryFinish = true,
            isNew = isNew
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
    isNew: Boolean = false,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FolioTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isNew) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFD12C24))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

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

private val NeutralDownloadPalette = CoverPalette(
    primary = Color(0xFF72777F),
    secondary = Color(0xFF565A62),
    accent = Color(0xFFB8BDC5),
    textColor = Color(0xFFEAECEF)
)

@Composable
internal fun ActiveDownloadGridItem(
    download: ActiveExploreDownload,
    onRevealComplete: (String) -> Unit,
    onCompletedBookClick: (Book) -> Unit,
    onCompletedBookOptions: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    var capsuleVisible by remember(download.id) { mutableStateOf(true) }
    var colorRevealed by remember(download.id) { mutableStateOf(false) }

    LaunchedEffect(download.id, download.isCompleting, download.isVisibleInLibrary) {
        if (download.isCompleting && download.isVisibleInLibrary) {
            // Hold briefly at 100% full capsule so user sees it complete, then fade capsule & reveal full color
            delay(260)
            capsuleVisible = false
            colorRevealed = true
            delay(360)
            onRevealComplete(download.id)
        }
    }

    val saturation by animateFloatAsState(
        targetValue = if (colorRevealed) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "CoverColorReveal"
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (capsuleVisible) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "CapsuleFade"
    )
    val targetProgress = download.progress
    val animatedProgress = if (targetProgress != null) {
        animateFloatAsState(
            targetValue = targetProgress.coerceIn(0.05f, 1f),
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            label = "DownloadBarFill"
        ).value
    } else null

    val completedBook = download.completedBook
    val displayTitle = completedBook?.title ?: download.title
    val displayAuthor = completedBook?.author ?: "Downloading..."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TactileBookCover(
            title = displayTitle,
            author = displayAuthor,
            palette = completedBook?.palette ?: NeutralDownloadPalette,
            style = completedBook?.coverStyle ?: CoverStyle.EditorialModern,
            coverPath = completedBook?.coverPath,
            modifier = Modifier.fillMaxWidth(),
            elevation = 9.dp,
            onClick = completedBook?.let { { onCompletedBookClick(it) } },
            onLongClick = completedBook?.let { { onCompletedBookOptions(it) } },
            libraryFinish = true,
            saturation = saturation,
            isNew = colorRevealed && completedBook != null,
            showDownloadCapsule = true,
            downloadProgress = animatedProgress,
            capsuleAlpha = capsuleAlpha
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = FolioTheme.extendedColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayAuthor,
                style = MaterialTheme.typography.bodySmall,
                color = FolioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusText = when {
                colorRevealed && completedBook != null -> "Unread"
                targetProgress != null -> "${(targetProgress * 100).toInt()}%"
                else -> "Downloading"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = FolioTheme.extendedColors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Box(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
internal fun ActiveDownloadListItem(
    download: ActiveExploreDownload,
    onRevealComplete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var capsuleVisible by remember(download.id) { mutableStateOf(true) }
    var colorRevealed by remember(download.id) { mutableStateOf(false) }

    LaunchedEffect(download.id, download.isCompleting, download.isVisibleInLibrary) {
        if (download.isCompleting && download.isVisibleInLibrary) {
            delay(260)
            capsuleVisible = false
            colorRevealed = true
            delay(360)
            onRevealComplete(download.id)
        }
    }

    val saturation by animateFloatAsState(
        targetValue = if (colorRevealed) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "ListColorReveal"
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (capsuleVisible) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "ListCapsuleFade"
    )
    val completedBook = download.completedBook

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TactileBookCover(
            title = completedBook?.title ?: download.title,
            author = completedBook?.author ?: "",
            palette = completedBook?.palette ?: NeutralDownloadPalette,
            style = completedBook?.coverStyle ?: CoverStyle.EditorialModern,
            coverPath = completedBook?.coverPath,
            modifier = Modifier.width(52.dp),
            elevation = 4.dp,
            libraryFinish = true,
            saturation = saturation,
            showDownloadCapsule = true,
            downloadProgress = download.progress,
            capsuleAlpha = capsuleAlpha
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = completedBook?.title ?: download.title,
                style = MaterialTheme.typography.titleMedium,
                color = FolioTheme.extendedColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = completedBook?.author ?: "Downloading...",
                style = MaterialTheme.typography.bodySmall,
                color = FolioTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val pct = download.progress?.let { "${(it * 100).toInt()}%" } ?: "Downloading…"
            Text(
                text = if (colorRevealed) "Ready to read" else pct,
                color = FolioTheme.extendedColors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }

        Box(modifier = Modifier.size(48.dp))
    }
}
