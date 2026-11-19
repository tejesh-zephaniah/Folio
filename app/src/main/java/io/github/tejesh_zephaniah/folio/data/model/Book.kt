package io.github.tejesh_zephaniah.folio.data.model

import io.github.tejesh_zephaniah.folio.core.designsystem.components.CoverStyle
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.CoverPalette

data class BookChapter(
    val chapterNumber: Int,
    val title: String,
    val content: List<String>
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val synopsis: String,
    val totalPages: Int,
    val currentPage: Int,
    val palette: CoverPalette,
    val coverStyle: CoverStyle,
    val chapters: List<BookChapter>,
    val coverPath: String? = null,
    val isFinished: Boolean = false,
    val addedAt: Long = 0
) {
    val progressPercentage: Int
        get() = pagePercentage(currentPage, totalPages)

    val isCurrentlyReading: Boolean
        get() = currentPage > 0 && !isFinished
}
