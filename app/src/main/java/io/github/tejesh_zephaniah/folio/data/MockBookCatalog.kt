package io.github.tejesh_zephaniah.folio.data

import io.github.tejesh_zephaniah.folio.data.model.Book

object MockBookCatalog {
    val books: List<Book> = emptyList()
    fun getBookById(id: String): Book? = books.firstOrNull { it.id == id }
}
