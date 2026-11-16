package io.github.tejesh_zephaniah.folio

import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfFilter
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfUiState
import io.github.tejesh_zephaniah.folio.bookshelf.SortOrder
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.data.MockBookCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolioUnitTest {

    @Test
    fun mockCatalog_hasBooksWithValidData() {
        val books = MockBookCatalog.books
        assertTrue("Catalog must not be empty", books.isNotEmpty())

        books.forEach { book ->
            assertTrue("Book ID must not be blank", book.id.isNotBlank())
            assertTrue("Book title must not be blank", book.title.isNotBlank())
            assertTrue("Book author must not be blank", book.author.isNotBlank())
            assertTrue("Book must have at least one chapter", book.chapters.isNotEmpty())
            assertTrue("Total pages must be positive", book.totalPages > 0)
            assertTrue("Progress percentage must be between 0 and 100", book.progressPercentage in 0..100)
        }
    }

    @Test
    fun mockCatalog_getBookById_returnsCorrectBook() {
        val book = MockBookCatalog.getBookById("gatsby")
        assertNotNull("Should find book with id 'gatsby'", book)
        assertEquals("The Great Gatsby", book?.title)
    }

    @Test
    fun bookshelfUiState_filter_all_returnsAllBooks() {
        val state = BookshelfUiState(
            books = MockBookCatalog.books,
            selectedFilter = BookshelfFilter.ALL
        )
        assertEquals(MockBookCatalog.books.size, state.filteredBooks.size)
    }

    @Test
    fun bookshelfUiState_filter_reading_returnsOnlyStartedUnfinishedBooks() {
        val state = BookshelfUiState(
            books = MockBookCatalog.books,
            selectedFilter = BookshelfFilter.READING
        )
        state.filteredBooks.forEach { book ->
            assertTrue("Book must be currently reading", book.isCurrentlyReading)
            assertFalse("Book must not be finished", book.isFinished)
        }
    }

    @Test
    fun bookshelfUiState_searchQuery_filtersByTitleAndAuthor() {
        val state = BookshelfUiState(
            books = MockBookCatalog.books,
            searchQuery = "Fitzgerald"
        )
        assertEquals(1, state.filteredBooks.size)
        assertEquals("The Great Gatsby", state.filteredBooks.first().title)
    }

    @Test
    fun bookshelfUiState_sortByTitle_ordersAlphabetically() {
        val state = BookshelfUiState(
            books = MockBookCatalog.books,
            sortOrder = SortOrder.TITLE
        )
        val titles = state.filteredBooks.map { it.title.lowercase() }
        assertEquals(titles.sorted(), titles)
    }

    @Test
    fun bookshelfUiState_sortByAuthor_ordersAlphabetically() {
        val state = BookshelfUiState(
            books = MockBookCatalog.books,
            sortOrder = SortOrder.AUTHOR
        )
        val authors = state.filteredBooks.map { it.author.lowercase() }
        assertEquals(authors.sorted(), authors)
    }

    @Test
    fun bookProgressPercentage_calculatesCorrectly() {
        val book = MockBookCatalog.getBookById("gatsby")!!
        val expected = ((book.currentPage.toFloat() / book.totalPages) * 100).toInt()
        assertEquals(expected, book.progressPercentage)
    }

    @Test
    fun themeModes_haveValidEnums() {
        assertEquals(3, AppThemeMode.entries.size)
        assertEquals(4, ReaderThemeMode.entries.size)
    }
}
