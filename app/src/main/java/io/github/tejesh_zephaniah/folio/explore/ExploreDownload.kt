package io.github.tejesh_zephaniah.folio.explore

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import io.github.tejesh_zephaniah.folio.core.storage.BookImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.net.ssl.HttpsURLConnection

internal const val EXPLORE_HOME = "https://z-library.sk/"

internal fun httpsExploreUri(value: String): URI? = try {
    URI(value).takeIf { it.scheme.equals("https", true) && it.host != null && it.rawUserInfo == null }
} catch (_: java.net.URISyntaxException) {
    null
}

internal fun isExploreSite(uri: URI): Boolean {
    val host = uri.host.lowercase(Locale.ROOT)
    return host == "z-library.sk" || host.endsWith(".z-library.sk")
}

internal fun exploreDownloadReferer(page: URI, target: URI): String {
    val pagePort = if (page.port == -1) 443 else page.port
    val targetPort = if (target.port == -1) 443 else target.port
    val sameOrigin = page.host.equals(target.host, ignoreCase = true) && pagePort == targetPort
    return if (sameOrigin) page.toString().substringBefore('#') else page.resolve("/").toString()
}

private const val MAX_BOOK_BYTES = 200L * 1024 * 1024
private const val MAX_REDIRECTS = 5

internal class ExploreDownloadException(message: String) : IOException(message)

internal suspend fun downloadExploreBook(
    context: Context,
    url: URI,
    userAgent: String,
    referer: URI?,
    suggestedName: String,
    onProgress: (Float?) -> Unit = {}
) = withContext(Dispatchers.IO) {
    val folder = Files.createTempDirectory(context.cacheDir.toPath(), "explore-book-").toFile()
    var file = File(folder, "download.part")
    try {
        var current = url
        val cookies = CookieManager.getInstance()
        repeat(MAX_REDIRECTS + 1) { hop ->
            ensureActive()
            val connection = current.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", userAgent)
                val address = current.toString()
                cookies.getCookie(address)?.let { connection.setRequestProperty("Cookie", it) }
                referer?.let { connection.setRequestProperty("Referer", exploreDownloadReferer(it, current)) }
                val status = connection.responseCode
                connection.headerFields.entries
                    .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                    .flatMap { it.value }
                    .forEach { cookies.setCookie(address, it) }
                if (status in 300..399) {
                    if (hop == MAX_REDIRECTS) throw ExploreDownloadException("The download link keeps redirecting.")
                    val next = connection.getHeaderField("Location")
                        ?.let { current.resolve(it).toString() }
                        ?.let(::httpsExploreUri)
                        ?: throw ExploreDownloadException("The download link could not be opened securely.")
                    current = next
                    return@repeat
                }
                if (status == 401 || status == 403) {
                    throw ExploreDownloadException("The site denied the download. Check your sign-in and try again.")
                }
                if (status == 429) {
                    throw ExploreDownloadException("The site has limited downloads. Check your account or try again later.")
                }
                if (status != 200) throw ExploreDownloadException("The site couldn’t download this book. Try again.")
                val totalBytes = connection.contentLengthLong
                if (totalBytes > MAX_BOOK_BYTES) {
                    throw ExploreDownloadException("This download is larger than 200 MB.")
                }
                if (totalBytes > 0L) onProgress(0f) else onProgress(null)
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var received = 0L
                        var lastReportedPct = -1
                        while (true) {
                            ensureActive()
                            val bytes = input.read(buffer)
                            if (bytes == -1) break
                            received += bytes
                            if (received > MAX_BOOK_BYTES) {
                                throw ExploreDownloadException("This download is larger than 200 MB.")
                            }
                            output.write(buffer, 0, bytes)
                            if (totalBytes > 0L) {
                                val fraction = (received.toFloat() / totalBytes).coerceIn(0f, 1f)
                                val pct = (fraction * 100).toInt()
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onProgress(fraction)
                                }
                            }
                        }
                    }
                }
                onProgress(1f)
                val extension = downloadedBookExtension(file)
                    ?: throw ExploreDownloadException("The site didn’t send a book file. Check your sign-in and download limit.")
                val name = connection.getHeaderField("Content-Disposition")
                    ?.let { URLUtil.guessFileName(current.toString(), it, connection.contentType) }
                    ?: suggestedName
                val bookFile = File(folder, safeBookFilename(name, extension))
                if (!file.renameTo(bookFile)) throw IOException("Could not stage the downloaded book")
                file = bookFile
                ensureActive()
                return@withContext BookImporter.importBook(context, Uri.fromFile(bookFile), requireReadable = true)
                    .getOrThrow()
            } finally {
                connection.disconnect()
            }
        }
        throw ExploreDownloadException("The download link could not be opened.")
    } finally {
        file.delete()
        folder.delete()
    }
}

internal fun downloadedBookExtension(file: File): String? {
    val header = file.inputStream().buffered().use { stream ->
        val bytes = ByteArray(minOf(1024L, file.length()).toInt())
        java.io.DataInputStream(stream).readFully(bytes)
        bytes.toString(Charsets.ISO_8859_1)
    }
    if (header.startsWith("%PDF-")) return "pdf"
    if (!header.startsWith("PK\u0003\u0004")) return null
    return try {
        ZipFile(file).use { archive ->
            val mime = archive.getEntry("mimetype") ?: return null
            if (mime.size != 20L || archive.getEntry("META-INF/container.xml") == null) return null
            val mediaType = archive.getInputStream(mime).use {
                val bytes = ByteArray(20)
                java.io.DataInputStream(it).readFully(bytes)
                bytes.toString(Charsets.US_ASCII)
            }
            "epub".takeIf { mediaType == "application/epub+zip" }
        }
    } catch (_: ZipException) {
        null
    }
}

internal fun safeBookFilename(name: String, extension: String): String {
    val stem = name.substringAfterLast('/').substringAfterLast('\\')
        .substringBeforeLast('.')
        .filterNot(Char::isISOControl)
        .trim('.', ' ')
        .take(120)
        .ifBlank { "Downloaded book" }
    return "$stem.$extension"
}
