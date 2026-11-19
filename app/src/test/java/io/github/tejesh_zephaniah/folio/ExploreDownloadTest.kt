package io.github.tejesh_zephaniah.folio

import io.github.tejesh_zephaniah.folio.explore.downloadedBookExtension
import io.github.tejesh_zephaniah.folio.explore.exploreDownloadReferer
import io.github.tejesh_zephaniah.folio.explore.httpsExploreUri
import io.github.tejesh_zephaniah.folio.explore.isExploreSite
import io.github.tejesh_zephaniah.folio.explore.safeBookFilename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExploreDownloadTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun siteNavigationMatchesTheHostAndRequiresHttps() {
        assertTrue(isExploreSite(httpsExploreUri("https://z-library.sk/")!!))
        assertTrue(isExploreSite(httpsExploreUri("https://books.Z-LIBRARY.SK/read")!!))
        assertFalse(isExploreSite(httpsExploreUri("https://z-library.sk.example.org/")!!))
        assertFalse(isExploreSite(httpsExploreUri("https://fake-z-library.sk/")!!))
        assertNull(httpsExploreUri("https://z-library.sk@example.org/"))
        assertNull(httpsExploreUri("http://z-library.sk/"))
    }

    @Test
    fun crossOriginDownloadsReceiveThePageOriginWithoutItsQuery() {
        val page = httpsExploreUri("https://z-library.sk/book/edition?temporary=value#file")!!
        val sameSite = httpsExploreUri("https://z-library.sk:443/dl/edition")!!
        val fileHost = httpsExploreUri("https://files.example.org/download/temporary")!!
        val otherPort = httpsExploreUri("https://z-library.sk:8443/file")!!
        assertEquals("https://z-library.sk/book/edition?temporary=value", exploreDownloadReferer(page, sameSite))
        assertEquals("https://z-library.sk/", exploreDownloadReferer(page, fileHost))
        assertEquals("https://z-library.sk/", exploreDownloadReferer(page, otherPort))
    }

    @Test
    fun downloadsAreIdentifiedByContentsAndGetSafeFilenames() {
        val pdf = files.newFile("book.part").apply { writeText("%PDF-1.7\n") }
        assertEquals("pdf", downloadedBookExtension(pdf))
        val epub = files.newFile("book.bin")
        writeArchive(epub, "application/epub+zip")
        assertEquals("epub", downloadedBookExtension(epub))
        val html = files.newFile("missing.epub").apply { writeText("<html>Sign in again</html>") }
        assertNull(downloadedBookExtension(html))
        val archive = files.newFile("archive.pdf")
        writeArchive(archive, "application/zip")
        assertNull(downloadedBookExtension(archive))
        assertEquals("Example.pdf", safeBookFilename("../secret\\Example.epub", "pdf"))
        assertEquals("Downloaded book.epub", safeBookFilename("..", "epub"))
    }

    private fun writeArchive(file: File, mime: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write(mime.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
            zip.closeEntry()
        }
    }
}
