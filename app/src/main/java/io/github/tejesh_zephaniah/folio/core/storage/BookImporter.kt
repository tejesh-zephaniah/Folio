package io.github.tejesh_zephaniah.folio.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.use
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object BookImporter {
    private const val TAG = "BookImporter"

    /**
     * Imports an EPUB or PDF from a content Uri or file path into app-private internal storage,
     * parses its metadata using Readium Streamer, extracts embedded covers, saves it to SQLite,
     * and returns the Book.
     */
    suspend fun importBook(
        context: Context,
        uri: Uri,
        requireReadable: Boolean = false
    ): Result<Book> = withContext(Dispatchers.IO) {
        var stagedBook: File? = null
        var committed = false
        try {
            val contentResolver = context.contentResolver
            var fileName = "imported_book"
            val isContentScheme = uri.scheme.equals("content", ignoreCase = true)

            if (isContentScheme) {
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val queriedName = cursor.getString(nameIndex)
                            if (!queriedName.isNullOrBlank()) {
                                fileName = queriedName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not query display name for $uri: ${e.message}")
                }
            } else {
                val path = uri.path ?: uri.toString()
                fileName = uri.lastPathSegment ?: File(path).name
            }

            val extension = when {
                fileName.endsWith(".epub", ignoreCase = true) -> "epub"
                fileName.endsWith(".pdf", ignoreCase = true) -> "pdf"
                else -> {
                    val mime = if (isContentScheme) contentResolver.getType(uri) ?: "" else ""
                    if (mime.contains("pdf", ignoreCase = true)) "pdf" else "epub"
                }
            }

            val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
            val bookId = UUID.randomUUID().toString()
            val targetFile = File(booksDir, "$bookId.$extension")
            stagedBook = targetFile

            // Copy file content to app-private storage
            val inputStream = if (isContentScheme) {
                contentResolver.openInputStream(uri)
            } else {
                val localFile = File(uri.path ?: "")
                if (localFile.exists()) FileInputStream(localFile) else null
            } ?: return@withContext Result.failure(Exception("Failed to open input stream for $uri"))

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Parse metadata with Readium
            var title = fileName.substringBeforeLast(".")
            var author = "Unknown Author"
            var genre = if (extension == "pdf") "PDF Document" else "EPUB Book"
            var coverPath: String? = null
            var totalPages = 100

            // 1. PDF cover & page count via Android PdfRenderer
            if (extension == "pdf") {
                try {
                    val descriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try {
                        PdfRenderer(descriptor)
                    } catch (e: Exception) {
                        descriptor.close()
                        throw e
                    }
                    renderer.use { pdf ->
                        totalPages = pdf.pageCount.coerceAtLeast(1)
                        if (pdf.pageCount > 0) {
                            pdf.openPage(0).use { page ->
                                val width = 400
                                val height = (width * (page.height.toFloat() / page.width)).toInt().coerceIn(300, 750)
                                val thumbnail = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(thumbnail, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                                    val coverFile = File(coversDir, "$bookId.png")
                                    FileOutputStream(coverFile).use { out ->
                                        thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out)
                                    }
                                    coverPath = coverFile.absolutePath
                                } finally {
                                    thumbnail.recycle()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to render PDF thumbnail", e)
                }
            }

            // 2. Readium metadata & EPUB embedded cover extraction
            var readable = false
            try {
                ReadiumManager.openPublication(context, targetFile)?.use { publication ->
                    readable = true
                    val pubTitle = publication.metadata.title
                    if (!pubTitle.isNullOrBlank()) {
                        title = pubTitle
                    }
                    val pubAuthors = publication.metadata.authors
                    if (pubAuthors.isNotEmpty()) {
                        author = pubAuthors.joinToString(", ") { it.name }
                    }
                    val pubSubjects = publication.metadata.subjects
                    if (pubSubjects.isNotEmpty()) {
                        genre = pubSubjects.first().name
                    }

                    // Extract embedded cover for EPUB if not already generated
                    if (coverPath == null) {
                        val coverBitmap = publication.cover()
                        if (coverBitmap != null) {
                            val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
                            val coverFile = File(coversDir, "$bookId.png")
                            FileOutputStream(coverFile).use { out ->
                                coverBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            coverPath = coverFile.absolutePath
                        }
                    }

                    if (extension != "pdf") {
                        val positionsList = try {
                            publication.positions()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read EPUB positions during import", e)
                            emptyList()
                        }
                        if (positionsList.isNotEmpty()) {
                            totalPages = positionsList.size
                        } else if (publication.readingOrder.isNotEmpty()) {
                            totalPages = publication.readingOrder.size
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract publication metadata", e)
            }
            if (requireReadable && !readable) {
                return@withContext Result.failure(IOException("The downloaded book could not be opened"))
            }

            val storedBook = StoredBook(
                id = bookId,
                title = title,
                author = author,
                genre = genre,
                filePath = targetFile.absolutePath,
                fileType = extension,
                coverPath = coverPath,
                totalPages = totalPages,
                progressPercentage = 0,
                lastLocatorJson = null,
                isFinished = false,
                addedAt = System.currentTimeMillis(),
                lastReadAt = System.currentTimeMillis()
            )

            BookRepository.getInstance(context).insertBook(storedBook)
            committed = true
            Result.success(storedBook.toBook())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import book: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (!committed) {
                stagedBook?.let { staged ->
                    staged.delete()
                    File(context.filesDir, "covers/${staged.nameWithoutExtension}.png").delete()
                }
            }
        }
    }
}
