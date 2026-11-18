package io.github.tejesh_zephaniah.folio

import io.github.tejesh_zephaniah.folio.data.model.mapPagePosition
import io.github.tejesh_zephaniah.folio.data.model.bookPageFromLocator
import io.github.tejesh_zephaniah.folio.data.model.bookmarkPageTitle
import io.github.tejesh_zephaniah.folio.data.model.pagePercentage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressTest {
    @Test
    fun percentagesUseTheSamePageCountForBooksAndBookmarks() {
        val book = testLibrary.first().copy(currentPage = 193, totalPages = 654)
        assertEquals(29, book.progressPercentage)
        assertEquals("Page 193 (29%)", bookmarkPageTitle(book.currentPage, book.totalPages))
        assertEquals(29, pagePercentage(29, 100))
        assertEquals(0, pagePercentage(0, 654))
        assertEquals(100, pagePercentage(654, 654))
    }

    @Test
    fun locatorUsesItsPageOrWholeBookProgress() {
        assertEquals(193, bookPageFromLocator(193, 0.1, 654))
        assertEquals(1, bookPageFromLocator(null, 0.0, 654))
        assertEquals(654, bookPageFromLocator(null, 1.0, 654))
        assertNull(bookPageFromLocator(null, Double.NaN, 654))
    }

    @Test
    fun mapsProgressBetweenDifferentPageCounts() {
        assertEquals(1, mapPagePosition(1, 9, 101))
        assertEquals(51, mapPagePosition(5, 9, 101))
        assertEquals(101, mapPagePosition(9, 9, 101))
    }

    @Test
    fun preservesUnreadPositionAndClampsInvalidPages() {
        assertEquals(0, mapPagePosition(0, 9, 101))
        assertEquals(0, mapPagePosition(-3, 9, 101))
        assertEquals(101, mapPagePosition(1000, 9, 101))
    }
}
