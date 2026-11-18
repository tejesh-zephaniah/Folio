package io.github.tejesh_zephaniah.folio.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class BookRepository private constructor(context: Context) {

    private val dbHelper = FolioDatabaseHelper.getInstance(context)
    private val appContext = context.applicationContext
    private val booksLock = Any()
    private val stagedImportLock = Mutex()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    companion object {
        @Volatile
        private var instance: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return instance ?: synchronized(this) {
                instance ?: BookRepository(context).also { instance = it }
            }
        }
    }

    suspend fun refreshBooks() = withContext(Dispatchers.IO) {
        stagedImportLock.withLock {
            synchronized(booksLock) { publishBooks() }
            if (_books.value.isEmpty() && !PreferencesManager.isStagedImportDisabled(appContext)) {
                val directories = listOfNotNull(appContext.getExternalFilesDir(null), File(appContext.filesDir, "books"))
                for (directory in directories) {
                    for (name in listOf("moby-dick.epub", "sample.pdf")) {
                        if (PreferencesManager.isStagedImportDisabled(appContext)) return@withLock
                        val file = File(directory, name)
                        if (file.isFile && file.length() > 0) {
                            BookImporter.importBook(appContext, Uri.fromFile(file))
                        }
                    }
                }
            }
        }
    }

    private fun publishBooks() {
        _books.value = dbHelper.getAllBooks().map { it.toBook() }
    }

    suspend fun getStoredBook(id: String): StoredBook? = withContext(Dispatchers.IO) {
        dbHelper.getBookById(id)
    }

    suspend fun insertBook(storedBook: StoredBook) = withContext(Dispatchers.IO) {
        synchronized(booksLock) {
            dbHelper.insertOrUpdateBook(storedBook)
            publishBooks()
        }
    }

    suspend fun updateProgress(id: String, page: Int, pageCount: Int, locatorJson: String) = withContext(Dispatchers.IO) {
        synchronized(booksLock) {
            if (dbHelper.updateBookProgress(id, page, pageCount, locatorJson) > 0) {
                publishBooks()
            }
        }
    }

    suspend fun renameBook(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        synchronized(booksLock) {
            dbHelper.renameBook(id, newTitle)
            publishBooks()
        }
    }

    suspend fun toggleFinished(id: String, currentlyFinished: Boolean) = withContext(Dispatchers.IO) {
        synchronized(booksLock) {
            val book = dbHelper.getBookById(id) ?: return@synchronized
            val newFinished = !currentlyFinished
            dbHelper.setBookFinished(id, newFinished, book.totalPages.takeIf { it > 0 } ?: 100)
            publishBooks()
        }
    }

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        val stored = stagedImportLock.withLock {
            synchronized(booksLock) {
                val book = dbHelper.getBookById(id) ?: return@synchronized null
                // An empty library after a removal is intentional.
                PreferencesManager.disableStagedImport(appContext)
                dbHelper.deleteBook(id)
                PreferencesManager.clearBookMetadata(appContext, id)
                publishBooks()
                book
            }
        } ?: return@withContext
        deleteManagedFile(stored.filePath, "books", "${stored.id}.${stored.fileType}")
        stored.coverPath?.let { deleteManagedFile(it, "covers", "${stored.id}.png") }
    }

    private fun deleteManagedFile(path: String, directory: String, fileName: String) {
        try {
            val file = File(path).canonicalFile
            val privateDirectory = File(appContext.filesDir.canonicalFile, directory)
            if (file.parentFile == privateDirectory && file.name == fileName && file.isFile && !file.delete()) {
                Log.w("FolioLibrary", "Could not delete Folio's copy at $path")
            }
        } catch (e: IOException) {
            Log.w("FolioLibrary", "Could not resolve Folio's copy at $path", e)
        }
    }

    // --- Highlight operations ---

    suspend fun addHighlight(
        bookId: String,
        locatorJson: String,
        color: Int
    ): StoredHighlight = withContext(Dispatchers.IO) {
        val highlight = StoredHighlight(
            id = java.util.UUID.randomUUID().toString(),
            bookId = bookId,
            locatorJson = locatorJson,
            color = color,
            createdAt = System.currentTimeMillis()
        )
        dbHelper.insertHighlight(highlight)
        highlight
    }

    suspend fun getHighlights(bookId: String): List<StoredHighlight> = withContext(Dispatchers.IO) {
        dbHelper.getHighlightsForBook(bookId)
    }

    suspend fun deleteHighlight(id: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteHighlight(id)
    }

    // --- Bookmark operations ---

    suspend fun addBookmark(
        bookId: String,
        title: String,
        locatorJson: String,
        chapter: String,
        progression: Float,
        excerpt: String
    ): StoredBookmark = withContext(Dispatchers.IO) {
        val bookmark = StoredBookmark(
            id = java.util.UUID.randomUUID().toString(),
            bookId = bookId,
            title = title.ifBlank { "Bookmark at ${"%.1f".format(progression * 100)}%" },
            locatorJson = locatorJson,
            chapter = chapter,
            progression = progression,
            excerpt = excerpt,
            createdAt = System.currentTimeMillis()
        )
        dbHelper.insertBookmark(bookmark)
        bookmark
    }

    suspend fun getBookmarks(bookId: String): List<StoredBookmark> = withContext(Dispatchers.IO) {
        dbHelper.getBookmarksForBook(bookId)
    }

    suspend fun deleteBookmark(id: String) = withContext(Dispatchers.IO) {
        dbHelper.deleteBookmark(id)
    }
}
