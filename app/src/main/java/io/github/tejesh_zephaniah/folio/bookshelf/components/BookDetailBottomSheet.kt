package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.TactileBookCover
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailBottomSheet(
    book: Book?,
    onDismiss: () -> Unit,
    onReadBook: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    if (book == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FolioTheme.extendedColors.glassBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(FolioTheme.extendedColors.textTertiary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    FolioCloseIcon(
                        tint = FolioTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Cover Preview & Title
            TactileBookCover(
                title = book.title,
                author = book.author,
                palette = book.palette,
                style = book.coverStyle,
                coverPath = book.coverPath,
                modifier = Modifier.width(130.dp),
                elevation = 12.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = book.title,
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = FolioTheme.extendedColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = book.author,
                fontSize = 15.sp,
                color = FolioTheme.extendedColors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${book.genre} • ${book.totalPages} pages",
                fontSize = 12.5.sp,
                color = FolioTheme.extendedColors.textTertiary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Reading Progress Indicator
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Reading Progress",
                        fontSize = 12.sp,
                        color = FolioTheme.extendedColors.textSecondary
                    )
                    Text(
                        text = "${book.progressPercentage}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FolioTheme.extendedColors.accent
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(FolioTheme.extendedColors.textTertiary.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.progressPercentage / 100f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(FolioTheme.extendedColors.accent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Synopsis
            Text(
                text = "SYNOPSIS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FolioTheme.extendedColors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = book.synopsis,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = FolioTheme.extendedColors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent Read Action Button
            Button(
                onClick = {
                    onDismiss()
                    onReadBook(book)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FolioTheme.extendedColors.accent
                )
            ) {
                Text(
                    text = if (book.currentPage > 0) "Continue Reading (Page ${book.currentPage})" else "Start Reading",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
