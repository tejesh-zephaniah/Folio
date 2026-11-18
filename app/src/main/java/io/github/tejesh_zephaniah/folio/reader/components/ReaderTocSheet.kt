package io.github.tejesh_zephaniah.folio.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

data class TocEntry(
    val title: String,
    val link: Link?,
    val chapterIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    visible: Boolean,
    publication: Publication?,
    book: Book,
    onDismiss: () -> Unit,
    onEntryClick: (TocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tocEntries = mutableListOf<TocEntry>()
    if (publication != null && publication.tableOfContents.isNotEmpty()) {
        fun flattenLinks(links: List<Link>, depth: Int = 0) {
            for ((idx, link) in links.withIndex()) {
                val prefix = "    ".repeat(depth)
                val title = link.title ?: "Chapter ${idx + 1}"
                tocEntries.add(TocEntry("$prefix$title", link, idx))
                if (link.children.isNotEmpty()) {
                    flattenLinks(link.children, depth + 1)
                }
            }
        }
        flattenLinks(publication.tableOfContents)
    } else {
        for ((idx, chap) in book.chapters.withIndex()) {
            tocEntries.add(TocEntry(chap.title, null, idx))
        }
    }

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
            Text(
                text = "Table of Contents",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = FolioTheme.extendedColors.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FolioTheme.extendedColors.glassBorderEnd)
            Spacer(modifier = Modifier.height(10.dp))

            if (tocEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No table of contents available for this book.",
                        fontSize = 14.sp,
                        color = FolioTheme.extendedColors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tocEntries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onEntryClick(entry)
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = FolioTheme.extendedColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
