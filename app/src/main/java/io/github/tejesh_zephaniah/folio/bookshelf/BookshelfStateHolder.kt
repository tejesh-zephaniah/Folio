package io.github.tejesh_zephaniah.folio.bookshelf

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.tejesh_zephaniah.folio.core.storage.BookRepository
import io.github.tejesh_zephaniah.folio.core.storage.PreferencesManager
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class BookshelfState(
    initialBooks: List<Book> = emptyList(),
    recentlyReadIds: List<String> = emptyList(),
    private val onRenameAction: (Book, String) -> Unit = { _, _ -> },
    private val onToggleReadAction: (Book) -> Unit = {},
    private val onRemoveAction: (Book) -> Unit = {}
) {
    var uiState by mutableStateOf(
        BookshelfUiState(
            books = initialBooks,
            recentlyReadIds = recentlyReadIds
        )
    )
        private set

    var bookToRename by mutableStateOf<Book?>(null)

    fun onSearchQueryChanged(newQuery: String) {
        uiState = uiState.copy(searchQuery = newQuery)
    }

    fun onToggleSearchActive(active: Boolean) {
        uiState = uiState.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else uiState.searchQuery
        )
    }

    fun onFilterSelected(filter: BookshelfFilter) {
        uiState = uiState.copy(selectedFilter = filter)
    }

    fun onViewModeSelected(mode: BookshelfViewMode) {
        uiState = uiState.copy(viewMode = mode)
    }

    fun onSortOrderSelected(sort: SortOrder) {
        uiState = uiState.copy(sortOrder = sort)
    }

    fun onOpenBookDetails(book: Book) {
        uiState = uiState.copy(selectedBookForDetails = book)
    }

    fun onDismissBookDetails() {
        uiState = uiState.copy(selectedBookForDetails = null)
    }

    fun onStartRename(book: Book) {
        bookToRename = book
    }

    fun onDismissRename() {
        bookToRename = null
    }

    fun onConfirmRename(newTitle: String) {
        val book = bookToRename ?: return
        onRenameAction(book, newTitle)
        bookToRename = null
    }

    fun onToggleRead(book: Book) {
        onToggleReadAction(book)
    }

    fun onRemoveBook(book: Book) {
        onRemoveAction(book)
    }

    fun refreshRecentlyRead(recentlyReadIds: List<String>) {
        uiState = uiState.copy(recentlyReadIds = recentlyReadIds)
    }

    fun setBooks(books: List<Book>) {
        uiState = uiState.copy(books = books)
    }

}

@Composable
fun rememberBookshelfState(
    context: Context = LocalContext.current
): BookshelfState {
    val repository = remember(context) { BookRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val repoBooks by repository.books.collectAsState()

    val state = remember(context) {
        val recentlyRead = PreferencesManager.getRecentlyReadBookIds(context)
        BookshelfState(
            initialBooks = repoBooks,
            recentlyReadIds = recentlyRead,
            onRenameAction = { book, newTitle ->
                scope.launch {
                    repository.renameBook(book.id, newTitle)
                }
            },
            onToggleReadAction = { book ->
                scope.launch {
                    repository.toggleFinished(book.id, book.isFinished)
                }
            },
            onRemoveAction = { book ->
                scope.launch {
                    try {
                        repository.deleteBook(book.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FolioLibrary", "Could not remove book ${book.id}", e)
                        Toast.makeText(context, "Could not remove book.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        repository.refreshBooks()
    }

    LaunchedEffect(repoBooks) {
        state.setBooks(repoBooks)
        state.refreshRecentlyRead(PreferencesManager.getRecentlyReadBookIds(context))
    }

    return state
}
