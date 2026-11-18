package io.github.tejesh_zephaniah.folio.core.storage

import android.content.Context
import android.content.SharedPreferences

internal const val DEFAULT_READER_TEXT_STEP = 3
internal val readerTextScales = listOf(0.76, 0.84, 0.92, 1.0, 1.10, 1.20, 1.32, 1.46, 1.62, 1.80, 2.0)

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode"),
    BLUSH_BLOOM("Blush Bloom"),
    WALNUT_LIBRARY("Walnut Library"),
    COPPER_HORIZON("Copper Horizon")
}

enum class ReaderThemeMode(
    val displayName: String,
    val backgroundArgb: Long,
    val textPrimaryArgb: Long,
    val textSecondaryArgb: Long
) {
    WARM_PAPER("Warm Paper", 0xFFF9F7F3, 0xFF18171A, 0xFF726F76),
    SEPIA("Sepia", 0xFFF4ECD8, 0xFF433422, 0xFF7A6B58),
    DARK("Dark", 0xFF18181A, 0xFFE2E0D8, 0xFF8E8C86),
    PURE_WHITE("White", 0xFFFFFFFF, 0xFF111111, 0xFF666666)
}

/**
 * Lightweight persistent storage for reading preferences, appearance,
 * and reading history using native Android SharedPreferences.
 */
object PreferencesManager {
    private const val PREFS_NAME = "folio_reading_preferences"
    private const val KEY_LAST_OPENED_BOOK_ID = "last_opened_book_id"
    private const val KEY_APP_THEME = "app_theme_mode"
    private const val KEY_READER_THEME = "reader_theme_mode"
    private const val KEY_RECENTLY_READ_LIST = "recently_read_book_ids"
    private const val KEY_BOOKMARKED_BOOK_IDS = "bookmarked_book_ids"
    private const val KEY_STAGED_IMPORT_DISABLED = "staged_import_disabled"
    private const val PREFIX_BOOK_PAGE = "book_page_"
    private const val PREFIX_BOOK_PAGE_COUNT = "book_page_count_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Last opened book
    fun getLastOpenedBookId(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_OPENED_BOOK_ID, null)
    }

    fun setLastOpenedBookId(context: Context, bookId: String?) {
        getPrefs(context).edit().apply {
            if (bookId != null) {
                putString(KEY_LAST_OPENED_BOOK_ID, bookId)
            } else {
                remove(KEY_LAST_OPENED_BOOK_ID)
            }
            apply()
        }
        if (bookId != null) {
            recordRecentlyRead(context, bookId)
        }
    }

    // Recently read book IDs
    fun getRecentlyReadBookIds(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_RECENTLY_READ_LIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun recordRecentlyRead(context: Context, bookId: String) {
        val currentList = getRecentlyReadBookIds(context).toMutableList()
        currentList.remove(bookId)
        currentList.add(0, bookId)
        val trimmed = currentList.take(15)
        getPrefs(context).edit().putString(KEY_RECENTLY_READ_LIST, trimmed.joinToString(",")).apply()
    }

    // App Theme Mode (System / Light / Dark)
    fun getAppThemeMode(context: Context): AppThemeMode {
        val name = getPrefs(context).getString(KEY_APP_THEME, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setAppThemeMode(context: Context, mode: AppThemeMode) {
        getPrefs(context).edit().putString(KEY_APP_THEME, mode.name).apply()
    }

    // Reader Theme Mode (Warm Paper / Sepia / Dark / White)
    fun getReaderThemeMode(context: Context): ReaderThemeMode {
        val name = getPrefs(context).getString(KEY_READER_THEME, ReaderThemeMode.WARM_PAPER.name)
        return try {
            ReaderThemeMode.valueOf(name ?: ReaderThemeMode.WARM_PAPER.name)
        } catch (_: Exception) {
            ReaderThemeMode.WARM_PAPER
        }
    }

    fun getReaderTextStep(context: Context): Int =
        getPrefs(context).getInt("reader_text_step", DEFAULT_READER_TEXT_STEP)
            .coerceIn(readerTextScales.indices)

    fun setReaderTextStep(context: Context, step: Int) {
        getPrefs(context).edit().putInt("reader_text_step", step.coerceIn(readerTextScales.indices)).apply()
    }

    fun setReaderThemeMode(context: Context, mode: ReaderThemeMode) {
        getPrefs(context).edit().putString(KEY_READER_THEME, mode.name).apply()
    }

    fun isBookmarked(context: Context, bookId: String): Boolean =
        getPrefs(context).getStringSet(KEY_BOOKMARKED_BOOK_IDS, emptySet()).orEmpty().contains(bookId)

    fun setBookmarked(context: Context, bookId: String, bookmarked: Boolean) {
        val ids = getPrefs(context).getStringSet(KEY_BOOKMARKED_BOOK_IDS, emptySet()).orEmpty().toMutableSet()
        if (bookmarked) ids.add(bookId) else ids.remove(bookId)
        getPrefs(context).edit().putStringSet(KEY_BOOKMARKED_BOOK_IDS, ids).apply()
    }

    internal fun isStagedImportDisabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_STAGED_IMPORT_DISABLED, false)

    internal fun disableStagedImport(context: Context) {
        check(getPrefs(context).edit().putBoolean(KEY_STAGED_IMPORT_DISABLED, true).commit()) {
            "Could not save staged-library import preference"
        }
    }

    internal fun clearBookMetadata(context: Context, bookId: String) {
        val prefs = getPrefs(context)
        val bookmarks = prefs.getStringSet(KEY_BOOKMARKED_BOOK_IDS, emptySet()).orEmpty()
        prefs.edit().apply {
            if (getLastOpenedBookId(context) == bookId) remove(KEY_LAST_OPENED_BOOK_ID)
            putString(KEY_RECENTLY_READ_LIST, getRecentlyReadBookIds(context).filterNot { it == bookId }.joinToString(","))
            putStringSet(KEY_BOOKMARKED_BOOK_IDS, bookmarks - bookId)
            remove(PREFIX_BOOK_PAGE + bookId)
            remove(PREFIX_BOOK_PAGE_COUNT + bookId)
        }.apply()
    }
}
