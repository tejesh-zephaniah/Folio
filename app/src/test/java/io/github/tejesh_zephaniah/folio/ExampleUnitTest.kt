package io.github.tejesh_zephaniah.folio

import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfFilter
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfState
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfUiState
import io.github.tejesh_zephaniah.folio.bookshelf.SortOrder
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.core.designsystem.components.CoverStyle
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes
import io.github.tejesh_zephaniah.folio.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val gatsby = Book(
    id = "gatsby",
    title = "The Great Gatsby",
    author = "F. Scott Fitzgerald",
    genre = "Fiction",
    synopsis = "",
    totalPages = 100,
    currentPage = 25,
    palette = BookPalettes.WildLight,
    coverStyle = CoverStyle.VintageClassic,
    chapters = emptyList()
)

internal val testLibrary = listOf(
    gatsby,
    gatsby.copy(id = "moby", title = "Moby-Dick", author = "Herman Melville", currentPage = 0),
    gatsby.copy(id = "emma", title = "Emma", author = "Jane Austen", isFinished = true)
)

class FolioUnitTest {

    @Test
    fun emptyLibrary_hasNoBooksToDisplay() {
        assertTrue(BookshelfUiState().filteredBooks.isEmpty())
    }

    @Test
    fun bookshelfUiState_filter_all_returnsAllBooks() {
        val state = BookshelfUiState(
            books = testLibrary,
            selectedFilter = BookshelfFilter.ALL
        )
        assertEquals(testLibrary.size, state.filteredBooks.size)
    }

    @Test
    fun bookshelfUiState_filter_reading_returnsOnlyStartedUnfinishedBooks() {
        val state = BookshelfUiState(
            books = testLibrary,
            selectedFilter = BookshelfFilter.READING
        )
        assertEquals(listOf("gatsby"), state.filteredBooks.map { it.id })
        state.filteredBooks.forEach { book ->
            assertTrue("Book must be currently reading", book.isCurrentlyReading)
            assertFalse("Book must not be finished", book.isFinished)
        }
    }

    @Test
    fun bookshelfUiState_searchQuery_filtersByTitleAndAuthor() {
        val state = BookshelfUiState(
            books = testLibrary,
            searchQuery = "Fitzgerald"
        )
        assertEquals(1, state.filteredBooks.size)
        assertEquals("The Great Gatsby", state.filteredBooks.first().title)
    }

    @Test
    fun bookshelfUiState_sortByTitle_ordersAlphabetically() {
        val state = BookshelfUiState(
            books = testLibrary,
            sortOrder = SortOrder.TITLE
        )
        val titles = state.filteredBooks.map { it.title.lowercase() }
        assertEquals(titles.sorted(), titles)
    }

    @Test
    fun bookshelfUiState_sortByAuthor_ordersAlphabetically() {
        val state = BookshelfUiState(
            books = testLibrary,
            sortOrder = SortOrder.AUTHOR
        )
        val authors = state.filteredBooks.map { it.author.lowercase() }
        assertEquals(authors.sorted(), authors)
    }

    @Test
    fun recentlyAdded_usesImportTimeEvenWhenReadingOrderDiffers() {
        val olderRead = gatsby.copy(addedAt = 10)
        val newUnread = testLibrary[1].copy(addedAt = 20)
        val state = BookshelfUiState(books = listOf(olderRead, newUnread), recentlyReadIds = listOf(olderRead.id))
        assertEquals(listOf(newUnread.id, olderRead.id), state.filteredBooks.map { it.id })
        assertEquals(olderRead.id, state.copy(sortOrder = SortOrder.RECENTLY_READ).filteredBooks.first().id)
    }

    @Test
    fun exploreImport_revealsTheNewBookThroughFiltersAndDoesNotDuplicateIt() {
        val imported = testLibrary[1].copy(addedAt = 20)
        val state = BookshelfState(initialBooks = listOf(gatsby, imported))
        state.onSortOrderSelected(SortOrder.AUTHOR)
        state.onFilterSelected(BookshelfFilter.READING)
        state.onSearchQueryChanged("Fitzgerald")
        state.showExploreImport(imported)
        assertEquals(listOf(imported.id, gatsby.id), state.uiState.filteredBooks.map { it.id })
        assertEquals(imported.id, state.latestExploreImport)
    }

    @Test
    fun bookProgressPercentage_calculatesCorrectly() {
        val book = testLibrary.single { it.id == "gatsby" }
        val expected = ((book.currentPage.toFloat() / book.totalPages) * 100).toInt()
        assertEquals(expected, book.progressPercentage)
    }

    @Test
    fun themeModes_haveValidEnums() {
        assertEquals(6, AppThemeMode.entries.size)
        assertEquals(4, ReaderThemeMode.entries.size)
    }
}
