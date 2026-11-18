package io.github.tejesh_zephaniah.folio.reader.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.StaticLayout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.data.model.Book

interface PageSurfaceProvider {
    val pageCount: Int

    suspend fun loadPage(page: Int, widthPx: Int, heightPx: Int): Bitmap
}

class MockBookPageSurfaceProvider private constructor(
    private val layout: StaticLayout,
    private val pageSlices: List<PageSlice>,
    private val backgroundColor: Int,
    private val contentLeft: Int,
    private val contentTop: Int,
    private val contentWidth: Int,
    private val contentBottom: Int
) : PageSurfaceProvider {
    override val pageCount: Int
        get() = pageSlices.size

    override suspend fun loadPage(page: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val slice = pageSlices.getOrNull(page - 1) ?: return bitmap
        canvas.save()
        val sliceBottom = contentTop + layout.getLineTop(slice.endLine) - layout.getLineTop(slice.firstLine)
        canvas.clipRect(
            contentLeft,
            contentTop,
            contentLeft + contentWidth,
            sliceBottom.coerceAtMost(contentBottom)
        )
        canvas.translate(contentLeft.toFloat(), (contentTop - layout.getLineTop(slice.firstLine)).toFloat())
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    companion object {
        fun create(
            book: Book,
            widthPx: Int,
            heightPx: Int,
            density: Float,
            theme: ReaderThemeMode
        ): MockBookPageSurfaceProvider {
            val margin = (26f * density).toInt()
            val contentTop = (76f * density).toInt()
            val contentBottom = heightPx - (100f * density).toInt()
            val contentWidth = (widthPx - margin * 2).coerceAtLeast(1)
            val contentHeight = (contentBottom - contentTop).coerceAtLeast(1)
            val text = SpannableStringBuilder()

            book.chapters.forEachIndexed { index, chapter ->
                val numberStart = text.length
                text.append(chapter.chapterNumber.toString())
                text.setSpan(
                    AbsoluteSizeSpan((24f * density).toInt(), false),
                    numberStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.setSpan(
                    ForegroundColorSpan(theme.textSecondaryArgb.toInt()),
                    numberStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.setSpan(
                    TypefaceSpan("serif"),
                    numberStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.append("\n\n")

                val titleStart = text.length
                text.append(chapter.title)
                text.setSpan(
                    AbsoluteSizeSpan((22f * density).toInt(), false),
                    titleStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.setSpan(
                    StyleSpan(Typeface.BOLD),
                    titleStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.setSpan(
                    TypefaceSpan("serif"),
                    titleStart,
                    text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.append("\n\n")

                chapter.content.forEach { paragraph ->
                    val paragraphStart = text.length
                    text.append(paragraph)
                    text.setSpan(
                        AbsoluteSizeSpan((17.5f * density).toInt(), false),
                        paragraphStart,
                        text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text.setSpan(
                        ForegroundColorSpan(theme.textPrimaryArgb.toInt()),
                        paragraphStart,
                        text.length,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text.setSpan(
                        TypefaceSpan("serif"),
                        paragraphStart,
                        text.length,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text.append("\n\n")
                }

                if (index < book.chapters.lastIndex) text.append("\n")
            }

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textPrimaryArgb.toInt()
                textSize = 17.5f * density
                typeface = Typeface.create("serif", Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(11.5f * density, 1f)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()

            val pages = mutableListOf<PageSlice>()
            var startLine = 0
            while (startLine < layout.lineCount) {
                val top = layout.getLineTop(startLine)
                var endLine = startLine
                while (
                    endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - top <= contentHeight
                ) {
                    endLine++
                }
                if (endLine == startLine) endLine++
                pages += PageSlice(startLine, endLine)
                startLine = endLine
            }

            return MockBookPageSurfaceProvider(
                layout = layout,
                pageSlices = pages.ifEmpty { listOf(PageSlice(0, 0)) },
                backgroundColor = theme.backgroundArgb.toInt(),
                contentLeft = margin,
                contentTop = contentTop,
                contentWidth = contentWidth,
                contentBottom = contentBottom
            )
        }
    }

    private data class PageSlice(val firstLine: Int, val endLine: Int)
}
