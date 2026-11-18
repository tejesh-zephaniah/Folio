package io.github.tejesh_zephaniah.folio.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.bookshelf.components.BookGridItem
import io.github.tejesh_zephaniah.folio.bookshelf.components.BookListItem
import io.github.tejesh_zephaniah.folio.bookshelf.components.BookOverflowBottomSheet
import io.github.tejesh_zephaniah.folio.bookshelf.components.BookshelfHeader
import io.github.tejesh_zephaniah.folio.bookshelf.components.RenameBookDialog
import io.github.tejesh_zephaniah.folio.bookshelf.components.SearchFilterBar
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioAddIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.BookImporter
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.launch

@Composable
fun BookshelfScreen(
    state: BookshelfState = rememberBookshelfState(),
    appThemeMode: AppThemeMode,
    onAppThemeChange: (AppThemeMode) -> Unit,
    onBookOpen: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = state.uiState

    // Storage Access Framework Document Picker Launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var firstImportedBook: Book? = null
                for (uri in uris) {
                    val result = BookImporter.importBook(context, uri)
                    result.onSuccess { book ->
                        if (firstImportedBook == null) firstImportedBook = book
                    }
                }
                firstImportedBook?.let { onBookOpen(it) }
            }
        }
    }

    fun launchBookPicker() {
        documentPickerLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/pdf",
                "application/octet-stream"
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Editorial Header with working appearance, layout menus, and Add Book button
            BookshelfHeader(
                title = "Library",
                viewMode = uiState.viewMode,
                sortOrder = uiState.sortOrder,
                appThemeMode = appThemeMode,
                onViewModeChange = { state.onViewModeSelected(it) },
                onSortOrderChange = { state.onSortOrderSelected(it) },
                onAppThemeChange = onAppThemeChange,
                onImportBookClick = { launchBookPicker() }
            )

            // Search & Filter Bar
            SearchFilterBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { state.onSearchQueryChanged(it) },
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { state.onFilterSelected(it) },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Books Content Grid or List
            val displayedBooks = uiState.filteredBooks

            if (displayedBooks.isEmpty()) {
                // Thoughtful, Editorial Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No Matching Books" else "Your Library is Empty",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = FolioTheme.extendedColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank())
                                "No books found matching \"${uiState.searchQuery}\"."
                            else
                                "Import your personal collection of EPUB and PDF books to read them offline with an immersive editorial experience.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = FolioTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { launchBookPicker() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FolioTheme.extendedColors.accent
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            FolioAddIcon(
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Import Books",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                when (uiState.viewMode) {
                    BookshelfViewMode.GRID_2_COL -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = 18.dp,
                                end = 18.dp,
                                top = 8.dp,
                                bottom = 100.dp // Clear bottom pill bar
                            ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayedBooks,
                                key = { it.id }
                            ) { book ->
                                BookGridItem(
                                    book = book,
                                    onBookClick = { onBookOpen(it) },
                                    onOptionsClick = { state.onOpenBookDetails(it) }
                                )
                            }
                        }
                    }

                    BookshelfViewMode.COMPACT_LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 100.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayedBooks,
                                key = { it.id }
                            ) { book ->
                                BookListItem(
                                    book = book,
                                    onBookClick = { onBookOpen(it) },
                                    onOptionsClick = { state.onOpenBookDetails(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookActions(state: BookshelfState) {
    var bookToRemove by remember { mutableStateOf<Book?>(null) }

    BookOverflowBottomSheet(
        book = state.uiState.selectedBookForDetails,
        onDismiss = state::onDismissBookDetails,
        onRenameClick = state::onStartRename,
        onToggleReadStatus = state::onToggleRead,
        onRemoveClick = { bookToRemove = it }
    )

    bookToRemove?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToRemove = null },
            title = { Text("Remove Book?") },
            text = { Text("Remove “${book.title}” and its reading progress, bookmarks, and highlights from Folio?") },
            confirmButton = {
                TextButton(onClick = {
                    state.onRemoveBook(book)
                    bookToRemove = null
                }) { Text("Remove Book") }
            },
            dismissButton = {
                TextButton(onClick = { bookToRemove = null }) { Text("Cancel") }
            }
        )
    }

    state.bookToRename?.let { book ->
        RenameBookDialog(
            initialTitle = book.title,
            onDismiss = state::onDismissRename,
            onConfirm = state::onConfirmRename
        )
    }
}
