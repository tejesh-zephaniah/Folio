package io.github.tejesh_zephaniah.folio

import androidx.compose.ui.graphics.Color
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.readerThemeColors
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderThemeAndLaunchTest {

    @Test
    fun continueReading_requiresSavedBookInLocalLibrary() {
        assertNull(resolveLastSessionBook(null, testLibrary))
        assertNull(resolveLastSessionBook("gatsby", emptyList()))
        assertNull(resolveLastSessionBook("missing", testLibrary))

        assertEquals(
            "The Great Gatsby",
            resolveLastSessionBook("gatsby", testLibrary)?.title
        )
    }

    @Test
    fun readerColorSchemes_followSelectedPageTheme() {
        val white = readerThemeColors(ReaderThemeMode.PURE_WHITE)
        val sepia = readerThemeColors(ReaderThemeMode.SEPIA)
        val dark = readerThemeColors(ReaderThemeMode.DARK)

        assertEquals(Color(ReaderThemeMode.PURE_WHITE.backgroundArgb), white.material.background)
        assertEquals(Color(ReaderThemeMode.PURE_WHITE.textPrimaryArgb), white.extended.textPrimary)
        assertEquals(Color(ReaderThemeMode.SEPIA.backgroundArgb), sepia.material.background)
        assertEquals(Color(ReaderThemeMode.SEPIA.textPrimaryArgb), sepia.material.onSurface)
        assertEquals(Color(ReaderThemeMode.DARK.backgroundArgb), dark.material.background)
        assertEquals(Color(ReaderThemeMode.DARK.textPrimaryArgb), dark.extended.textPrimary)
        assertNotEquals(white.extended.glassBackground, dark.extended.glassBackground)
        assertNotEquals(sepia.extended.glassBackground, white.extended.glassBackground)
    }
}
