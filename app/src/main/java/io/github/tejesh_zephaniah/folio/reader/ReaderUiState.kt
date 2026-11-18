package io.github.tejesh_zephaniah.folio.reader

import io.github.tejesh_zephaniah.folio.data.model.Book

data class ReaderUiState(
    val book: Book,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isBookmarked: Boolean = false,
    val areControlsVisible: Boolean = true
) {
    val progressPercentage: Int
        get() = if (totalPages > 0) {
            ((currentPage.toFloat() / totalPages) * 100).toInt().coerceIn(0, 100)
        } else 0
}
