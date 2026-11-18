package io.github.tejesh_zephaniah.folio.reader.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Locator
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class EpubPageCurl(
    private val host: View,
    private val window: Window,
    private val scope: CoroutineScope,
    private val navigator: () -> EpubNavigatorFragment?,
    private val paperColor: () -> Int
) {
    private class Turn(
        val origin: Locator,
        val fromRight: Boolean,
        val rightCorner: PageTurnCorner?,
        val windowArea: Rect,
        val textArea: Rect
    ) {
        val width: Int get() = textArea.width()
        val release = CompletableDeferred<Boolean>()
        var distance = 0f
        var page: Bitmap? = null
        var showDestination = false
    }

    private val active = MutableStateFlow<Turn?>(null)
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var startAt = 0L
    private var armed = false
    private var consumesGesture = false
    private var turnJob: Job? = null
    private val screen = window.decorView
    private val renderer = CurlPainter()
    private val overlay = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val turn = active.value ?: return
            val page = turn.page ?: return
            val paper = paperColor()
            val checkpoint = canvas.save()
            canvas.clipRect(bounds)
            if (turn.showDestination) canvas.drawPaperOutside(turn.textArea, paper)
            else canvas.drawColor(paper)
            renderer.draw(
                canvas = canvas,
                page = page,
                width = bounds.width(),
                height = bounds.height(),
                distance = (turn.distance / turn.width) * bounds.width(),
                fromRight = turn.fromRight,
                rightCorner = turn.rightCorner,
                paper = paper
            )
            canvas.restoreToCount(checkpoint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Required by Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    val previewOrigin: Locator? get() = active.value?.origin
    val isTurning: Boolean get() = active.value != null

    suspend fun awaitSettled() {
        active.first { it == null }
    }

    fun tapEdge(fromRight: Boolean): Boolean {
        if (isTurning) return true
        val turn = startTurn(fromRight, distance = 0f) ?: return false
        turn.release.complete(true)
        return true
    }

    fun dispatchTouch(
        event: MotionEvent,
        hasSelection: Boolean,
        dispatchToReader: (MotionEvent) -> Boolean
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumesGesture = isTurning
                armed = !isTurning && !hasSelection
                startX = event.x
                startY = event.y
                startAt = event.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (armed && !consumesGesture) {
                    val rightCorner = forwardRightCorner()
                    val cornerPull = rightCorner != null && -dx > touchSlop && -dx > abs(dy) * 0.45f
                    if (hasSelection || !cornerPull && abs(dy) > touchSlop && abs(dy) >= abs(dx) &&
                        (rightCorner == null || abs(dy) > touchSlop * 2) ||
                        event.eventTime - startAt >= ViewConfiguration.getLongPressTimeout()
                    ) {
                        armed = false
                    } else if (cornerPull || abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.25f) {
                        armed = false
                        if (startTurn(fromRight = dx < 0, distance = abs(dx), rightCorner = rightCorner) != null) {
                            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                            try {
                                dispatchToReader(cancel)
                            } finally {
                                cancel.recycle()
                            }
                            consumesGesture = true
                        }
                    }
                }
                if (consumesGesture) {
                    active.value?.takeUnless { it.release.isCompleted }?.let { turn ->
                        updatePointer(turn, if (turn.fromRight) -dx else dx)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                armed = false
                if (consumesGesture) active.value?.release?.complete(false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                armed = false
                if (consumesGesture) {
                    consumesGesture = false
                    active.value?.takeUnless { it.release.isCompleted }?.let { turn ->
                        val distance = if (turn.fromRight) startX - event.x else event.x - startX
                        val fastSwipe = event.eventTime - startAt <= 230L
                        val threshold = if (fastSwipe) 0.13f else 0.28f
                        turn.release.complete(
                            event.actionMasked == MotionEvent.ACTION_UP && distance >= turn.width * threshold
                        )
                    }
                    return true
                }
            }
        }
        return consumesGesture || dispatchToReader(event)
    }

    private fun forwardRightCorner(): PageTurnCorner? {
        if (startX < host.width * 0.80f ||
            navigator()?.overflow?.value?.readingProgression != ReadingProgression.LTR
        ) return null
        return when {
            startY < host.height * 0.20f -> PageTurnCorner.TOP_RIGHT
            startY > host.height * 0.80f -> PageTurnCorner.BOTTOM_RIGHT
            else -> null
        }
    }

    private fun startTurn(fromRight: Boolean, distance: Float, rightCorner: PageTurnCorner? = null): Turn? {
        val epub = navigator() ?: return null
        val overflow = epub.overflow.value
        if (overflow.scroll || overflow.axis != Axis.HORIZONTAL || host.width < 16 || host.height < 16) return null
        val forward = fromRight == (overflow.readingProgression == ReadingProgression.LTR)
        val screenPosition = IntArray(2).also { screen.getLocationInWindow(it) }
        val textPosition = IntArray(2).also { host.getLocationInWindow(it) }
        val left = textPosition[0] - screenPosition[0]
        val top = textPosition[1] - screenPosition[1]
        val textArea = Rect(left, top, left + host.width, top + host.height)
        if (!textArea.intersect(0, 0, screen.width, screen.height)) return null
        val windowArea = Rect(
            screenPosition[0], screenPosition[1],
            screenPosition[0] + screen.width, screenPosition[1] + screen.height
        )
        val turn = Turn(epub.currentLocator.value, fromRight, rightCorner.takeIf { forward && fromRight }, windowArea, textArea)
        updatePointer(turn, distance)
        active.value = turn
        turnJob = scope.launch {
            try {
                playTurn(epub, turn, forward)
            } finally {
                screen.overlay.remove(overlay)
                renderer.clearTexture()
                active.value = null
            }
        }
        return turn
    }

    private suspend fun playTurn(epub: EpubNavigatorFragment, turn: Turn, forward: Boolean) {
        fun navigate(animated: Boolean): Boolean =
            if (forward) epub.goForward(animated) else epub.goBackward(animated)

        turn.page = withTimeoutOrNull(350) { capturePage(turn) }
        if (turn.page == null) {
            if (turn.release.await()) navigate(animated = true)
            return
        }
        overlay.setBounds(0, 0, turn.windowArea.width(), turn.windowArea.height())
        screen.overlay.add(overlay)
        if (turn.release.isCompleted && !turn.release.await()) {
            settle(turn, commit = false)
            return
        }
        overlay.invalidateSelf()

        // The real navigator supplies the exposed page. Its temporary location stays private
        // until the gesture commits or the original page is restored.
        if (!navigate(animated = false)) {
            settle(turn, commit = false)
            return
        }
        val destination = withTimeoutOrNull(1_400) {
            epub.currentLocator.first { !samePage(turn.origin, it) }
        }
        turn.showDestination = destination != null
        overlay.invalidateSelf()

        val commit = turn.release.await()
        settle(turn, commit = commit && destination != null)
        if (!commit) {
            val location = turn.origin.copy(
                text = Locator.Text(),
                locations = turn.origin.locations.copy(fragments = emptyList())
            )
            if (epub.go(location, animated = false)) {
                withTimeoutOrNull(1_400) { epub.currentLocator.first { samePage(turn.origin, it) } }
            }
        }
    }

    fun cancelGesture() {
        armed = false
        active.value?.release?.complete(false)
    }

    fun detach() {
        screen.overlay.remove(overlay)
        cancelGesture()
        turnJob?.cancel()
        consumesGesture = false
    }

    private fun updatePointer(turn: Turn, distance: Float) {
        turn.distance = distance.coerceIn(0f, turn.width.toFloat())
        overlay.invalidateSelf()
    }

    private suspend fun settle(turn: Turn, commit: Boolean) {
        val distance = turn.distance
        val end = if (commit) turn.width * 2f else 0f
        Animatable(0f).animateTo(1f, tween(if (commit) 240 else 195, easing = FastOutSlowInEasing)) {
            turn.distance = distance + (end - distance) * value
            overlay.invalidateSelf()
        }
    }

    private suspend fun capturePage(turn: Turn): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (!host.isAttachedToWindow || !host.isShown || !host.hasWindowFocus()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val area = turn.windowArea
        val scale = sqrt(2_500_000f / (area.width().toFloat() * area.height())).coerceAtMost(1f)
        val bitmap = Bitmap.createBitmap(
            (area.width() * scale).roundToInt().coerceAtLeast(1),
            (area.height() * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        try {
            PixelCopy.request(window, area, bitmap, { result ->
                if (continuation.isActive && result == PixelCopy.SUCCESS) {
                    // Keep the reading viewport in place and extend only the paper around it.
                    Canvas(bitmap).apply {
                        scale(bitmap.width.toFloat() / area.width(), bitmap.height.toFloat() / area.height())
                        drawPaperOutside(turn.textArea, paperColor())
                    }
                    val texture = if (Build.VERSION.SDK_INT < 29 && !turn.fromRight) {
                        val flip = Matrix().apply { setScale(-1f, 1f) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, flip, false)
                            .also { if (it !== bitmap) bitmap.recycle() }
                    } else {
                        bitmap
                    }
                    continuation.resume(texture)
                } else {
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            continuation.resume(null)
        }
    }

    private fun samePage(a: Locator, b: Locator): Boolean =
        a.href == b.href &&
            abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) < 0.0001
}

private fun Canvas.drawPaperOutside(textArea: Rect, paper: Int) {
    val checkpoint = save()
    clipOutRect(textArea)
    drawColor(paper)
    restoreToCount(checkpoint)
}

private class CurlPainter {
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint()
    private val positions = FloatArray((COLUMNS + 1) * 17 * 2)
    private val lighting = IntArray((COLUMNS + 1) * 17)
    private val textureCoords = FloatArray(positions.size)
    private val frontTriangles = ShortArray(COLUMNS * 16 * 6)
    private val backTriangles = ShortArray(frontTriangles.size)
    private val bounds = RectF()
    private var shaderBitmap: Bitmap? = null
    private var lastPaperColor: Int? = null
    private var undersideFilter: ColorMatrixColorFilter? = null

    fun clearTexture() {
        shaderBitmap = null
        texturePaint.shader = null
    }

    fun draw(
        canvas: Canvas,
        page: Bitmap,
        width: Int,
        height: Int,
        distance: Float,
        fromRight: Boolean,
        rightCorner: PageTurnCorner?,
        paper: Int
    ) {
        if (distance < 0.5f) {
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            val checkpoint = canvas.save()
            if (Build.VERSION.SDK_INT < 29 && !fromRight) canvas.scale(-1f, 1f, width * 0.5f, 0f)
            canvas.drawBitmap(page, null, bounds, bitmapPaint)
            canvas.restoreToCount(checkpoint)
            return
        }

        val (mesh, edge, bottomEdge) =
            PageCurlGeometry.boundPage(width.toFloat(), height.toFloat(), distance, fromRight, COLUMNS, rightCorner)
        val shadowWidth = (distance * 0.25f).coerceIn(1f, width * 0.14f)
        val shadowEnd = edge + if (fromRight) shadowWidth else -shadowWidth
        val shadowAlpha = (52f * ((2f - distance / width) * 5f).coerceIn(0f, 1f)).roundToInt()
        shadowPaint.shader = LinearGradient(edge, 0f, shadowEnd, 0f, Color.argb(shadowAlpha, 0, 0, 0),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        val shadowState = canvas.save()
        canvas.skew((bottomEdge - edge) / height, 0f)
        canvas.drawRect(minOf(edge, shadowEnd), 0f, maxOf(edge, shadowEnd), height.toFloat(), shadowPaint)
        canvas.restoreToCount(shadowState)

        val vertices = mesh.vertices
        val stride = PageCurlGeometry.FLOATS_PER_VERTEX
        val drawsVertices = Build.VERSION.SDK_INT >= 29
        for (vertex in 0 until vertices.size / stride) {
            val offset = vertex * stride
            val drawIndex = if (drawsVertices || fromRight) vertex else vertex + COLUMNS - 2 * (vertex % (COLUMNS + 1))
            positions[drawIndex * 2] = (0.5f + vertices[offset] * 0.5f) * width
            positions[drawIndex * 2 + 1] = (0.5f - vertices[offset + 1] * 0.5f) * height
            val normal = vertices[offset + 5]
            val shade = if (normal >= 0) 0.75f + 0.25f * normal else 0.70f - 0.19f * normal
            val channel = (shade * 255).roundToInt().coerceIn(0, 255)
            lighting[drawIndex] = Color.rgb(channel, channel, channel)
            textureCoords[vertex * 2] = vertices[offset + 6] * page.width
            textureCoords[vertex * 2 + 1] = vertices[offset + 7] * page.height
        }
        if (drawsVertices) {
            drawFaces(canvas, page, mesh, paper)
        } else {
            canvas.drawBitmapMesh(page, COLUMNS, mesh.rows, positions, 0, lighting, 0, texturePaint)
        }
    }

    private fun drawFaces(canvas: Canvas, page: Bitmap, mesh: PageCurlMesh, paper: Int) {
        if (shaderBitmap !== page) {
            texturePaint.shader = BitmapShader(page, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shaderBitmap = page
        }
        var frontCount = 0
        var backCount = 0
        for (triangle in mesh.indices.indices step 3) {
            val a = mesh.indices[triangle]
            val b = mesh.indices[triangle + 1]
            val c = mesh.indices[triangle + 2]
            val ax = positions[a * 2]
            val ay = positions[a * 2 + 1]
            val winding = (positions[b * 2] - ax) * (positions[c * 2 + 1] - ay) -
                (positions[b * 2 + 1] - ay) * (positions[c * 2] - ax)
            if (winding < 0) {
                frontTriangles[frontCount++] = a
                frontTriangles[frontCount++] = b
                frontTriangles[frontCount++] = c
            } else {
                backTriangles[backCount++] = a
                backTriangles[backCount++] = b
                backTriangles[backCount++] = c
            }
        }
        // Draw the underside last so it stays above the flat paper on either edge.
        val positionCount = mesh.vertices.size / PageCurlGeometry.FLOATS_PER_VERTEX * 2
        drawTriangles(canvas, positionCount, frontTriangles, frontCount)
        if (lastPaperColor != paper) {
            // The front's print shows faintly through the sheet.
            val ink = 0.27f
            val base = 1f - ink
            undersideFilter = ColorMatrixColorFilter(
                floatArrayOf(
                    ink, 0f, 0f, 0f, Color.red(paper) * base,
                    0f, ink, 0f, 0f, Color.green(paper) * base,
                    0f, 0f, ink, 0f, Color.blue(paper) * base,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            lastPaperColor = paper
        }
        texturePaint.colorFilter = undersideFilter
        drawTriangles(canvas, positionCount, backTriangles, backCount)
        texturePaint.colorFilter = null
    }

    private fun drawTriangles(canvas: Canvas, positionCount: Int, triangles: ShortArray, count: Int) {
        if (count == 0) return
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, positionCount, positions, 0, textureCoords, 0,
            lighting, 0, triangles, 0, count, texturePaint
        )
    }

    private companion object {
        const val COLUMNS = 96
    }
}
