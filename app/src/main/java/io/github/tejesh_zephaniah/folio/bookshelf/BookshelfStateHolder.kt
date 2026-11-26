package io.github.tejesh_zephaniah.folio.bookshelf

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    initialNewBookIds: Set<String> = emptySet(),
    private val onRenameAction: (Book, String) -> Unit = { _, _ -> },
    private val onToggleReadAction: (Book) -> Unit = {},
    private val onRemoveAction: (Book) -> Unit = {},
    private val onMarkBookNewAction: (String) -> Unit = {},
    private val onMarkBookOpenedAction: (String) -> Unit = {}
) {
    var uiState by mutableStateOf(
        BookshelfUiState(
            books = initialBooks,
            recentlyReadIds = recentlyReadIds,
            newBookIds = initialNewBookIds
        )
    )
        private set

    var bookToRename by mutableStateOf<Book?>(null)
    var latestExploreImport by mutableStateOf<String?>(null)
        private set

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

    internal fun showExploreImport(book: Book) {
        if (uiState.activeDownloads.any { it.completedBook?.id == book.id }) return
        onMarkBookNewAction(book.id)
        uiState = uiState.copy(
            books = if (uiState.books.any { it.id == book.id }) uiState.books else listOf(book) + uiState.books,
            searchQuery = "",
            isSearchActive = false,
            selectedFilter = BookshelfFilter.ALL,
            sortOrder = SortOrder.RECENTLY_ADDED,
            newBookIds = uiState.newBookIds + book.id
        )
        latestExploreImport = book.id
    }

    internal fun registerExploreDownload(id: String, title: String) {
        val cleanTitle = title.substringBeforeLast('.').ifBlank { "Downloading..." }
        val entry = ActiveExploreDownload(id = id, title = cleanTitle, progress = 0f, isVisibleInLibrary = false)
        uiState = uiState.copy(
            activeDownloads = listOf(entry) + uiState.activeDownloads.filterNot { it.id == id }
        )
    }

    internal fun revealExploreDownloadInLibrary(id: String) {
        uiState = uiState.copy(
            activeDownloads = uiState.activeDownloads.map {
                if (it.id == id) it.copy(isVisibleInLibrary = true) else it
            },
            searchQuery = "",
            isSearchActive = false,
            selectedFilter = BookshelfFilter.ALL,
            sortOrder = SortOrder.RECENTLY_ADDED
        )
        latestExploreImport = id
    }

    internal fun updateExploreDownloadProgress(id: String, progress: Float?) {
        uiState = uiState.copy(
            activeDownloads = uiState.activeDownloads.map {
                if (it.id == id && !it.isCompleting) it.copy(progress = progress) else it
            }
        )
    }

    internal fun completeExploreDownload(id: String, book: Book) {
        onMarkBookNewAction(book.id)
        val hasActive = uiState.activeDownloads.any { it.id == id }
        val updatedBooks = if (uiState.books.any { it.id == book.id }) uiState.books else listOf(book) + uiState.books
        if (hasActive) {
            uiState = uiState.copy(
                books = updatedBooks,
                newBookIds = uiState.newBookIds + book.id,
                activeDownloads = uiState.activeDownloads.map {
                    if (it.id == id) it.copy(
                        title = book.title,
                        progress = 1f,
                        isCompleting = true,
                        completedBook = book
                    ) else it
                }
            )
        } else {
            showExploreImport(book)
        }
    }

    internal fun finishExploreDownloadAnimation(id: String) {
        uiState = uiState.copy(
            activeDownloads = uiState.activeDownloads.filterNot { it.id == id }
        )
    }

    internal fun failExploreDownload(id: String) {
        uiState = uiState.copy(
            activeDownloads = uiState.activeDownloads.filterNot { it.id == id }
        )
    }

    internal fun markBookOpened(bookId: String) {
        if (bookId in uiState.newBookIds) {
            uiState = uiState.copy(newBookIds = uiState.newBookIds - bookId)
        }
        onMarkBookOpenedAction(bookId)
    }

    internal fun acknowledgeExploreImport(id: String) {
        if (latestExploreImport == id) latestExploreImport = null
    }
}

@Composable
fun rememberBookshelfState(
    context: Context = LocalContext.current
): BookshelfState {
    val repository = remember(context) { BookRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val state = remember(context) {
        val recentlyRead = PreferencesManager.getRecentlyReadBookIds(context)
        val newBookIds = PreferencesManager.getNewBookIds(context)
        BookshelfState(
            initialBooks = repository.books.value,
            recentlyReadIds = recentlyRead,
            initialNewBookIds = newBookIds,
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
            },
            onMarkBookNewAction = { bookId ->
                PreferencesManager.addNewBookId(context, bookId)
            },
            onMarkBookOpenedAction = { bookId ->
                PreferencesManager.clearNewBookId(context, bookId)
            }
        )
    }

    LaunchedEffect(Unit) {
        repository.refreshBooks()
    }

    LaunchedEffect(repository, state) {
        repository.books.collect { books ->
            state.setBooks(books)
            state.refreshRecentlyRead(PreferencesManager.getRecentlyReadBookIds(context))
        }
    }

    return state
}
