package io.github.tejesh_zephaniah.folio.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioSearchIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.TactileBookCover
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.GlassSearchSurface
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import io.github.tejesh_zephaniah.folio.core.storage.BookRepository
import io.github.tejesh_zephaniah.folio.data.model.Book

@Composable
fun SearchScreen(
    onBookOpen: (Book) -> Unit,
    onBookOptions: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { BookRepository.getInstance(context) }
    val allBooks by repository.books.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val searchResults = remember(searchQuery, allBooks) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            allBooks.filter {
                it.title.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.genre.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.displayLarge,
                    color = FolioTheme.extendedColors.textPrimary
                )
            }

            // Dedicated Search Bar
            GlassSearchSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FolioSearchIcon(
                        tint = FolioTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search by title, author, or genre...",
                                color = FolioTheme.extendedColors.textSecondary,
                                fontSize = 15.sp
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = FolioTheme.extendedColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(FolioTheme.extendedColors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" },
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

            if (searchQuery.isBlank()) return@Column
            Spacer(modifier = Modifier.height(12.dp))

            if (searchResults.isEmpty()) {
                // Empty Results State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No Books Found",
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            color = FolioTheme.extendedColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "We couldn't find any books matching \"$searchQuery\". Try checking the spelling or searching for another author or genre.",
                            fontSize = 14.sp,
                            color = FolioTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Results List
                Text(
                    text = "${searchResults.size} RESULT${if (searchResults.size > 1) "S" else ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = FolioTheme.extendedColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 110.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = searchResults,
                        key = { it.id }
                    ) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .combinedClickable(
                                    onClick = { onBookOpen(book) },
                                    onLongClick = { onBookOptions(book) },
                                    onLongClickLabel = "Book options"
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TactileBookCover(
                                title = book.title,
                                author = book.author,
                                palette = book.palette,
                                style = book.coverStyle,
                                coverPath = book.coverPath,
                                modifier = Modifier.width(60.dp),
                                elevation = 6.dp
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = book.title,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = FolioTheme.extendedColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = book.author,
                                    fontSize = 13.5.sp,
                                    color = FolioTheme.extendedColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(FolioTheme.extendedColors.accent.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = book.genre,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = FolioTheme.extendedColors.accent
                                        )
                                    }

                                    if (book.currentPage > 0) {
                                        Text(
                                            text = "${book.progressPercentage}% read",
                                            fontSize = 11.sp,
                                            color = FolioTheme.extendedColors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
