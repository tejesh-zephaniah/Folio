package io.github.tejesh_zephaniah.folio.bookshelf

import io.github.tejesh_zephaniah.folio.data.model.Book

enum class BookshelfFilter(val displayName: String) {
    ALL("All"),
    READING("Reading"),
    FINISHED("Finished")
}

enum class BookshelfViewMode(val displayName: String) {
    GRID_2_COL("Grid View"),
    COMPACT_LIST("List View")
}

enum class SortOrder(val displayName: String) {
    RECENTLY_READ("Recently Read"),
    RECENTLY_ADDED("Recently Added"),
    TITLE("Title"),
    AUTHOR("Author")
}

data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedFilter: BookshelfFilter = BookshelfFilter.ALL,
    val viewMode: BookshelfViewMode = BookshelfViewMode.GRID_2_COL,
    val sortOrder: SortOrder = SortOrder.RECENTLY_ADDED,
    val selectedBookForDetails: Book? = null,
    val recentlyReadIds: List<String> = emptyList()
) {
    val filteredBooks: List<Book>
        get() {
            var result = books

            // Apply category filter
            result = when (selectedFilter) {
                BookshelfFilter.ALL -> result
                BookshelfFilter.READING -> result.filter { it.isCurrentlyReading }
                BookshelfFilter.FINISHED -> result.filter { it.isFinished }
            }

            // Apply search query
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                result = result.filter {
                    it.title.lowercase().contains(query) ||
                    it.author.lowercase().contains(query) ||
                    it.genre.lowercase().contains(query)
                }
            }

            // Apply Sort Order
            result = when (sortOrder) {
                SortOrder.RECENTLY_READ -> {
                    result.sortedWith(
                        compareBy<Book> { book ->
                            val index = recentlyReadIds.indexOf(book.id)
                            if (index != -1) index else (1000 + if (book.isCurrentlyReading) 0 else 1)
                        }.thenByDescending { it.currentPage }
                    )
                }
                SortOrder.RECENTLY_ADDED -> result.sortedByDescending { it.addedAt }
                SortOrder.TITLE -> result.sortedBy { it.title.lowercase() }
                SortOrder.AUTHOR -> result.sortedBy { it.author.lowercase() }
            }

            return result
        }

    val currentlyReadingBooks: List<Book>
        get() = books.filter { it.isCurrentlyReading }
}
