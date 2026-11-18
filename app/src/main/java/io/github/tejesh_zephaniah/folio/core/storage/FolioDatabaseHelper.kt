package io.github.tejesh_zephaniah.folio.core.storage

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.tejesh_zephaniah.folio.core.designsystem.components.CoverStyle
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.CoverPalette
import io.github.tejesh_zephaniah.folio.data.model.Book
import io.github.tejesh_zephaniah.folio.data.model.BookChapter
import io.github.tejesh_zephaniah.folio.data.model.bookPageFromLocator
import io.github.tejesh_zephaniah.folio.data.model.pagePercentage
import kotlin.math.roundToInt

data class StoredBookmark(
    val id: String,
    val bookId: String,
    val title: String,
    val locatorJson: String,
    val chapter: String,
    val progression: Float,
    val excerpt: String,
    val createdAt: Long
)

data class StoredHighlight(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val color: Int,
    val createdAt: Long
)

data class StoredBook(
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val filePath: String,
    val fileType: String, // "epub" or "pdf"
    val coverPath: String?,
    val totalPages: Int,
    val progressPercentage: Int,
    val lastLocatorJson: String?,
    val isFinished: Boolean,
    val addedAt: Long,
    val lastReadAt: Long,
    val currentPage: Int? = null
) {
    fun toBook(): Book {
        val pageCount = totalPages.takeIf { it > 0 } ?: 100
        val page = currentPage ?: if (isFinished) {
            pageCount
        } else {
            val locations = ReadiumManager.parseLocatorJson(lastLocatorJson)?.locations
            bookPageFromLocator(locations?.position, locations?.totalProgression, pageCount)
                ?: ((progressPercentage.coerceIn(0, 100) / 100.0) * pageCount).roundToInt()
        }

        // Deterministic palette based on title hash
        val palettes = listOf(
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.WildLight,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.MidnightMystery,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.OuterSpace,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.PurplePop,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.MartianSand,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.MinimalistIvory,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.EmeraldClassic,
            io.github.tejesh_zephaniah.folio.core.designsystem.theme.BookPalettes.ObsidianPoetry
        )
        val styles = CoverStyle.values()
        val hash = title.hashCode()
        val pIndex = Math.abs(hash % palettes.size)
        val sIndex = Math.abs((hash / palettes.size) % styles.size)
        val palette = palettes[pIndex]
        val style = styles[sIndex]

        return Book(
            id = id,
            title = title,
            author = author,
            genre = genre.ifBlank { if (fileType == "pdf") "PDF Document" else "EPUB Book" },
            synopsis = "Imported $fileType document located at $filePath",
            totalPages = pageCount,
            currentPage = page.coerceIn(0, pageCount),
            palette = palette,
            coverStyle = style,
            chapters = listOf(BookChapter(1, title, emptyList())),
            coverPath = coverPath,
            isFinished = isFinished
        )
    }
}

class FolioDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "folio.db"
        const val DATABASE_VERSION = 2

        // Books table
        const val TABLE_BOOKS = "books"
        const val COL_BOOK_ID = "id"
        const val COL_BOOK_TITLE = "title"
        const val COL_BOOK_AUTHOR = "author"
        const val COL_BOOK_GENRE = "genre"
        const val COL_BOOK_FILE_PATH = "file_path"
        const val COL_BOOK_FILE_TYPE = "file_type"
        const val COL_BOOK_COVER_PATH = "cover_path"
        const val COL_BOOK_TOTAL_PAGES = "total_pages"
        const val COL_BOOK_CURRENT_PAGE = "current_page"
        const val COL_BOOK_PROGRESS_PCT = "progress_percentage"
        const val COL_BOOK_LAST_LOCATOR = "last_locator_json"
        const val COL_BOOK_IS_FINISHED = "is_finished"
        const val COL_BOOK_ADDED_AT = "added_at"
        const val COL_BOOK_LAST_READ_AT = "last_read_at"

        // Bookmarks table
        const val TABLE_BOOKMARKS = "bookmarks"
        const val COL_BM_ID = "id"
        const val COL_BM_BOOK_ID = "book_id"
        const val COL_BM_TITLE = "title"
        const val COL_BM_LOCATOR = "locator_json"
        const val COL_BM_CHAPTER = "chapter"
        const val COL_BM_PROGRESSION = "progression"
        const val COL_BM_EXCERPT = "excerpt"
        const val COL_BM_CREATED_AT = "created_at"

        // Highlights table
        const val TABLE_HIGHLIGHTS = "highlights"
        const val COL_HL_ID = "id"
        const val COL_HL_BOOK_ID = "book_id"
        const val COL_HL_LOCATOR = "locator_json"
        const val COL_HL_COLOR = "color"
        const val COL_HL_CREATED_AT = "created_at"

        @Volatile
        private var instance: FolioDatabaseHelper? = null

        fun getInstance(context: Context): FolioDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: FolioDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOKS (
                $COL_BOOK_ID TEXT PRIMARY KEY,
                $COL_BOOK_TITLE TEXT NOT NULL,
                $COL_BOOK_AUTHOR TEXT NOT NULL,
                $COL_BOOK_GENRE TEXT NOT NULL,
                $COL_BOOK_FILE_PATH TEXT NOT NULL,
                $COL_BOOK_FILE_TYPE TEXT NOT NULL,
                $COL_BOOK_COVER_PATH TEXT,
                $COL_BOOK_TOTAL_PAGES INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_CURRENT_PAGE INTEGER,
                $COL_BOOK_PROGRESS_PCT INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_LAST_LOCATOR TEXT,
                $COL_BOOK_IS_FINISHED INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_ADDED_AT INTEGER NOT NULL,
                $COL_BOOK_LAST_READ_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOKMARKS (
                $COL_BM_ID TEXT PRIMARY KEY,
                $COL_BM_BOOK_ID TEXT NOT NULL,
                $COL_BM_TITLE TEXT NOT NULL,
                $COL_BM_LOCATOR TEXT NOT NULL,
                $COL_BM_CHAPTER TEXT NOT NULL,
                $COL_BM_PROGRESSION REAL NOT NULL DEFAULT 0.0,
                $COL_BM_EXCERPT TEXT NOT NULL,
                $COL_BM_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_BM_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HIGHLIGHTS (
                $COL_HL_ID TEXT PRIMARY KEY,
                $COL_HL_BOOK_ID TEXT NOT NULL,
                $COL_HL_LOCATOR TEXT NOT NULL,
                $COL_HL_COLOR INTEGER NOT NULL,
                $COL_HL_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_HL_BOOK_ID) REFERENCES $TABLE_BOOKS($COL_BOOK_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HIGHLIGHTS (
                $COL_HL_ID TEXT PRIMARY KEY,
                $COL_HL_BOOK_ID TEXT NOT NULL,
                $COL_HL_LOCATOR TEXT NOT NULL,
                $COL_HL_COLOR INTEGER NOT NULL,
                $COL_HL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COL_BOOK_CURRENT_PAGE INTEGER")
        }
    }

    // --- Book Operations ---

    fun insertOrUpdateBook(book: StoredBook) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_ID, book.id)
            put(COL_BOOK_TITLE, book.title)
            put(COL_BOOK_AUTHOR, book.author)
            put(COL_BOOK_GENRE, book.genre)
            put(COL_BOOK_FILE_PATH, book.filePath)
            put(COL_BOOK_FILE_TYPE, book.fileType)
            put(COL_BOOK_COVER_PATH, book.coverPath)
            put(COL_BOOK_TOTAL_PAGES, book.totalPages)
            put(COL_BOOK_CURRENT_PAGE, book.currentPage)
            put(COL_BOOK_PROGRESS_PCT, book.progressPercentage)
            put(COL_BOOK_LAST_LOCATOR, book.lastLocatorJson)
            put(COL_BOOK_IS_FINISHED, if (book.isFinished) 1 else 0)
            put(COL_BOOK_ADDED_AT, book.addedAt)
            put(COL_BOOK_LAST_READ_AT, book.lastReadAt)
        }
        check(db.insertWithOnConflict(TABLE_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save the imported book"
        }
    }

    fun getBookById(id: String): StoredBook? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_BOOKS,
            null,
            "$COL_BOOK_ID = ?",
            arrayOf(id),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToStoredBook(it) else null
        }
    }

    fun getAllBooks(): List<StoredBook> {
        val db = readableDatabase
        val books = mutableListOf<StoredBook>()
        val cursor = db.query(
            TABLE_BOOKS,
            null,
            null, null, null, null,
            "$COL_BOOK_LAST_READ_AT DESC, $COL_BOOK_ADDED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                books.add(cursorToStoredBook(it))
            }
        }
        return books
    }

    fun updateBookProgress(id: String, page: Int, pageCount: Int, locatorJson: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_CURRENT_PAGE, page)
            put(COL_BOOK_TOTAL_PAGES, pageCount)
            put(COL_BOOK_PROGRESS_PCT, pagePercentage(page, pageCount))
            put(COL_BOOK_LAST_LOCATOR, locatorJson)
            put(COL_BOOK_LAST_READ_AT, System.currentTimeMillis())
            if (pageCount > 1 && page == pageCount) {
                put(COL_BOOK_IS_FINISHED, 1)
            }
        }
        return db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(id))
    }

    fun renameBook(id: String, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_TITLE, newTitle.trim())
        }
        db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(id))
    }

    fun setBookFinished(id: String, isFinished: Boolean, pageCount: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_IS_FINISHED, if (isFinished) 1 else 0)
            put(COL_BOOK_CURRENT_PAGE, if (isFinished) pageCount else 0)
            if (isFinished) {
                put(COL_BOOK_PROGRESS_PCT, 100)
            } else {
                put(COL_BOOK_PROGRESS_PCT, 0)
            }
        }
        db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(id))
    }

    fun deleteBook(id: String) {
        writableDatabase.transaction {
            delete(TABLE_BOOKMARKS, "$COL_BM_BOOK_ID = ?", arrayOf(id))
            delete(TABLE_HIGHLIGHTS, "$COL_HL_BOOK_ID = ?", arrayOf(id))
            delete(TABLE_BOOKS, "$COL_BOOK_ID = ?", arrayOf(id))
        }
    }

    // --- Bookmark Operations ---

    fun insertBookmark(bookmark: StoredBookmark) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BM_ID, bookmark.id)
            put(COL_BM_BOOK_ID, bookmark.bookId)
            put(COL_BM_TITLE, bookmark.title)
            put(COL_BM_LOCATOR, bookmark.locatorJson)
            put(COL_BM_CHAPTER, bookmark.chapter)
            put(COL_BM_PROGRESSION, bookmark.progression)
            put(COL_BM_EXCERPT, bookmark.excerpt)
            put(COL_BM_CREATED_AT, bookmark.createdAt)
        }
        db.insertWithOnConflict(TABLE_BOOKMARKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getBookmarksForBook(bookId: String): List<StoredBookmark> {
        val db = readableDatabase
        val bookmarks = mutableListOf<StoredBookmark>()
        val cursor = db.query(
            TABLE_BOOKMARKS,
            null,
            "$COL_BM_BOOK_ID = ?",
            arrayOf(bookId),
            null, null,
            "$COL_BM_PROGRESSION ASC, $COL_BM_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                bookmarks.add(cursorToStoredBookmark(it))
            }
        }
        return bookmarks
    }

    fun deleteBookmark(id: String) {
        val db = writableDatabase
        db.delete(TABLE_BOOKMARKS, "$COL_BM_ID = ?", arrayOf(id))
    }

    // --- Highlight Operations ---

    fun insertHighlight(highlight: StoredHighlight) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HL_ID, highlight.id)
            put(COL_HL_BOOK_ID, highlight.bookId)
            put(COL_HL_LOCATOR, highlight.locatorJson)
            put(COL_HL_COLOR, highlight.color)
            put(COL_HL_CREATED_AT, highlight.createdAt)
        }
        db.insertWithOnConflict(TABLE_HIGHLIGHTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHighlightsForBook(bookId: String): List<StoredHighlight> {
        val db = readableDatabase
        val highlights = mutableListOf<StoredHighlight>()
        val cursor = db.query(
            TABLE_HIGHLIGHTS,
            null,
            "$COL_HL_BOOK_ID = ?",
            arrayOf(bookId),
            null, null,
            "$COL_HL_CREATED_AT ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                highlights.add(
                    StoredHighlight(
                        id = it.getString(it.getColumnIndexOrThrow(COL_HL_ID)),
                        bookId = it.getString(it.getColumnIndexOrThrow(COL_HL_BOOK_ID)),
                        locatorJson = it.getString(it.getColumnIndexOrThrow(COL_HL_LOCATOR)),
                        color = it.getInt(it.getColumnIndexOrThrow(COL_HL_COLOR)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_HL_CREATED_AT))
                    )
                )
            }
        }
        return highlights
    }

    fun deleteHighlight(id: String) {
        val db = writableDatabase
        db.delete(TABLE_HIGHLIGHTS, "$COL_HL_ID = ?", arrayOf(id))
    }

    private fun cursorToStoredBook(cursor: Cursor): StoredBook {
        return StoredBook(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_TITLE)),
            author = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_AUTHOR)),
            genre = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_GENRE)),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)),
            fileType = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_FILE_TYPE)),
            coverPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_COVER_PATH)),
            totalPages = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_TOTAL_PAGES)),
            progressPercentage = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_PROGRESS_PCT)),
            lastLocatorJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_BOOK_LAST_LOCATOR)),
            isFinished = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOOK_IS_FINISHED)) == 1,
            addedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_ADDED_AT)),
            lastReadAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BOOK_LAST_READ_AT)),
            currentPage = cursor.getColumnIndexOrThrow(COL_BOOK_CURRENT_PAGE).let { index ->
                if (cursor.isNull(index)) null else cursor.getInt(index)
            }
        )
    }

    private fun cursorToStoredBookmark(cursor: Cursor): StoredBookmark {
        return StoredBookmark(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_ID)),
            bookId = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_BOOK_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_TITLE)),
            locatorJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_LOCATOR)),
            chapter = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_CHAPTER)),
            progression = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_BM_PROGRESSION)),
            excerpt = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_EXCERPT)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BM_CREATED_AT))
        )
    }
}
