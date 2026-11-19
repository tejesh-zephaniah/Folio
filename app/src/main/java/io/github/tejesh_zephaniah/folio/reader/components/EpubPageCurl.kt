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
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.core.view.allViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
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

private data class PageGrip(val x: Float, val y: Float)

internal class EpubPageCurl(
    private val host: View,
    private val window: Window,
    private val scope: CoroutineScope,
    private val navigator: () -> EpubNavigatorFragment?,
    private val isEdgeResource: (Locator, Boolean) -> Boolean,
    private val paperColor: () -> Int
) {
    private data class PageAreas(val window: Rect, val text: Rect)

    private data class PreparedPage(val areas: PageAreas, val fromRight: Boolean, val capture: Deferred<Bitmap?>)

    private class Turn(
        val origin: Locator,
        val fromRight: Boolean,
        val rightCorner: PageTurnCorner?,
        val rightGrab: PageGrip?,
        val areas: PageAreas
    ) {
        val width: Int get() = areas.text.width()
        val release = CompletableDeferred<Boolean>()
        var distance = 0f
        var inwardDrag = 0f
        var dragX = 0f
        var dragY = 0f
        var velocity = 0f
        var lastMove = 0L
        var page: Bitmap? = null
        val destinationReady = CompletableDeferred<Unit>()
        val showDestination: Boolean get() = destinationReady.isCompleted
        var returning = false
        var committed = false
    }

    private val active = MutableStateFlow<Turn?>(null)
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var startAt = 0L
    private var armed = false
    private var consumesGesture = false
    private var turnJob: Job? = null
    private var preparedPage: PreparedPage? = null
    private val screen = window.decorView
    private val renderer = CurlPainter()
    private val overlay = object : Drawable() {
        override fun draw(canvas: Canvas) {
            val turn = active.value ?: return
            val page = turn.page ?: return
            val paper = paperColor()
            val checkpoint = canvas.save()
            canvas.clipRect(bounds)
            if (turn.showDestination) canvas.drawPaperOutside(turn.areas.text, paper)
            else canvas.drawColor(paper)
            renderer.draw(
                canvas = canvas,
                page = page,
                width = bounds.width(),
                height = bounds.height(),
                distance = (turn.distance / turn.width) * bounds.width(),
                fromRight = turn.fromRight,
                rightCorner = turn.rightCorner,
                inwardDrag = turn.inwardDrag,
                rightGrab = turn.rightGrab,
                dragX = turn.dragX,
                dragY = turn.dragY,
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
        finishCommittedTurn()
        if (isTurning) return true
        val epub = navigator() ?: return false
        val forward = pageDirection(epub, fromRight) ?: return false
        if (atBoundary(epub, fromRight, forward)) return true
        val turn = startTurn(fromRight, distance = 0f, rightCorner = null, dragging = false) ?: return false
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
                finishCommittedTurn()
                consumesGesture = isTurning
                armed = !isTurning && !hasSelection
                startX = event.x
                startY = event.y
                startAt = event.eventTime
                discardPreparedPage()
                if (armed) {
                    when {
                        startX >= host.width * 0.80f -> preparePage(fromRight = true)
                        startX <= host.width * 0.20f -> preparePage(fromRight = false)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (armed && !consumesGesture) {
                    val rightCorner = forwardRightCorner()
                    val forwardPull = Build.VERSION.SDK_INT >= 29 && -dx > touchSlop &&
                        -dx > abs(dy) * 0.58f && isForwardReading()
                    val cornerPull = rightCorner != null && (
                        (-dx > touchSlop && -dx > abs(dy) * 0.35f) ||
                            (dx <= touchSlop * 0.5f && inwardDistance(rightCorner, dy) > touchSlop * 1.5f)
                    )
                    if (hasSelection || !cornerPull && !forwardPull && abs(dy) > touchSlop && abs(dy) >= abs(dx) &&
                        (rightCorner == null || abs(dy) > touchSlop * 3) ||
                        event.eventTime - startAt >= ViewConfiguration.getLongPressTimeout()
                    ) {
                        armed = false
                        discardPreparedPage()
                    } else {
                        if (abs(dx) > touchSlop * 0.5f && abs(dx) > abs(dy)) preparePage(fromRight = dx < 0)
                        if (cornerPull || forwardPull || abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.25f) {
                            armed = false
                            if (startTurn(cornerPull || dx < 0, abs(dx), rightCorner) != null) {
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
                }
                if (consumesGesture) {
                    active.value?.takeUnless { it.release.isCompleted }?.let { turn ->
                        updatePointer(turn, if (turn.fromRight) -dx else dx, dy, event.eventTime)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                armed = false
                discardPreparedPage()
                if (consumesGesture) active.value?.release?.complete(false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                armed = false
                discardPreparedPage()
                if (consumesGesture) {
                    consumesGesture = false
                    active.value?.takeUnless { it.release.isCompleted }?.let { turn ->
                        val distance = if (turn.fromRight) startX - event.x else event.x - startX
                        val fastSwipe = event.eventTime - startAt <= 230L
                        val threshold = if (fastSwipe) 0.13f else 0.28f
                        val required = turn.rightGrab?.let {
                            minOf(turn.width * threshold, (turn.width * it.x * 0.64f).coerceAtLeast(touchSlop * 2f))
                        } ?: (turn.width * threshold)
                        turn.release.complete(
                            event.actionMasked == MotionEvent.ACTION_UP && distance >= required
                        )
                    }
                    return true
                }
            }
        }
        return consumesGesture || dispatchToReader(event)
    }

    private fun forwardRightCorner(): PageTurnCorner? {
        if (!isForwardRightEdge()) return null
        return when {
            startY < host.height * 0.20f -> PageTurnCorner.TOP_RIGHT
            startY > host.height * 0.80f -> PageTurnCorner.BOTTOM_RIGHT
            else -> null
        }
    }

    private fun isForwardRightEdge(): Boolean = startX >= host.width * 0.80f && isForwardReading()

    private fun isForwardReading(): Boolean {
        val overflow = navigator()?.overflow?.value ?: return false
        return !overflow.scroll && overflow.axis == Axis.HORIZONTAL && overflow.readingProgression == ReadingProgression.LTR
    }

    private fun pageDirection(epub: EpubNavigatorFragment, fromRight: Boolean): Boolean? {
        val overflow = epub.overflow.value
        if (overflow.scroll || overflow.axis != Axis.HORIZONTAL) return null
        return fromRight == (overflow.readingProgression == ReadingProgression.LTR)
    }

    private fun startTurn(
        fromRight: Boolean, distance: Float, rightCorner: PageTurnCorner?, dragging: Boolean = true
    ): Turn? {
        val epub = navigator() ?: return null
        val forward = pageDirection(epub, fromRight) ?: return null
        if (host.width < 16 || host.height < 16 || atBoundary(epub, fromRight, forward)) return null
        val areas = currentPageAreas() ?: return null
        val prepared = preparedPage?.takeIf { it.fromRight == fromRight && it.areas == areas }?.capture
        if (prepared == null) discardPreparedPage() else preparedPage = null
        val grab = if (Build.VERSION.SDK_INT >= 29 && dragging && forward && fromRight) {
            PageGrip(
                (startX / host.width).coerceIn(0f, 1f),
                ((areas.text.top + startY) / areas.window.height()).coerceIn(0f, 1f)
            )
        } else null
        val turn = Turn(epub.currentLocator.value, fromRight, rightCorner.takeIf { forward && fromRight }, grab, areas)
        updatePointer(turn, distance)
        active.value = turn
        turnJob = scope.launch {
            try {
                playTurn(epub, turn, forward, prepared)
            } finally {
                prepared?.cancel()
                clearTurn(turn)
            }
        }
        return turn
    }

    private fun clearTurn(turn: Turn) {
        if (active.value !== turn) return
        screen.overlay.remove(overlay)
        renderer.clearTexture()
        active.value = null
    }

    private fun finishCommittedTurn() {
        val turn = active.value ?: return
        val epub = navigator() ?: return
        if (turn.committed && turn.distance >= turn.width * 1.65f &&
            !samePage(turn.origin, epub.currentLocator.value)
        ) {
            turnJob?.cancel()
            clearTurn(turn)
        }
    }

    private fun currentPageAreas(): PageAreas? {
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
        return PageAreas(windowArea, textArea)
    }

    private fun atBoundary(epub: EpubNavigatorFragment, fromRight: Boolean, forward: Boolean): Boolean =
        isEdgeResource(epub.currentLocator.value, forward) && visibleWebView(epub)?.let { view ->
            view.contentHeight > 0 && !view.canScrollHorizontally(if (fromRight) 1 else -1)
        } == true

    private fun preparePage(fromRight: Boolean) {
        if (preparedPage?.fromRight == fromRight) return
        val epub = navigator() ?: return
        val forward = pageDirection(epub, fromRight) ?: return
        if (atBoundary(epub, fromRight, forward)) return
        val areas = currentPageAreas() ?: return
        discardPreparedPage()
        preparedPage = PreparedPage(areas, fromRight, scope.async {
            withTimeoutOrNull(180) { capturePage(areas, fromRight) }
        })
    }

    private suspend fun playTurn(
        epub: EpubNavigatorFragment,
        turn: Turn,
        forward: Boolean,
        prepared: Deferred<Bitmap?>?
    ): Unit = coroutineScope {
        fun navigate(animated: Boolean): Boolean =
            if (forward) epub.goForward(animated) else epub.goBackward(animated)

        // A flick can finish before PixelCopy. Keep its release decision for the curl.
        turn.page = withTimeoutOrNull(350) {
            prepared?.await() ?: capturePage(turn.areas, turn.fromRight)
        }
        if (turn.page == null) {
            prepared?.cancel()
            if (turn.release.await()) navigate(animated = true)
            return@coroutineScope
        }
        overlay.setBounds(0, 0, turn.areas.window.width(), turn.areas.window.height())
        screen.overlay.add(overlay)
        if (turn.release.isCompleted && !turn.release.await()) {
            settle(turn, commit = false)
            return@coroutineScope
        }
        overlay.invalidateSelf()

        // The real navigator supplies the exposed page. Its temporary location stays private
        // until the gesture commits or the original page is restored.
        val sourceWebView = visibleWebView(epub)
        val fromX = sourceWebView?.scrollX
        if (!navigate(animated = false)) {
            settle(turn, commit = false)
            return@coroutineScope
        }
        // Readium can scroll the WebView asynchronously, before publishing the new locator.
        val visualJob = sourceWebView?.let { webView ->
            launch {
                while (!turn.showDestination && !turn.returning && webView.isAttachedToWindow) {
                    if (webView.scrollX != fromX && visibleWebView(epub) === webView) {
                        webView.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                revealDestination(turn)
                            }
                        })
                        break
                    }
                    withFrameNanos { }
                }
            }
        }
        val destinationJob = launch {
            val destination = withTimeoutOrNull(1_400) {
                epub.currentLocator.first { !samePage(turn.origin, it) }
            }
            if (destination != null) revealDestination(turn)
        }

        val commit = turn.release.await()
        turn.returning = !commit
        if (commit && !turn.showDestination) {
            val start = turn.distance
            val approach = start + turn.width * 0.12f
            val continueMotion = launch {
                Animatable(start).animateTo(approach, tween(55)) {
                    turn.distance = value
                    overlay.invalidateSelf()
                }
            }
            select<Unit> {
                turn.destinationReady.onAwait { }
                destinationJob.onJoin { }
            }
            continueMotion.cancel()
            if (!turn.showDestination) Log.w("FolioReader", "EPUB turn finished waiting for a destination")
        }
        visualJob?.cancel()
        turn.committed = commit && turn.showDestination
        settle(turn, commit = turn.committed)
        if (commit) {
            // Visible pixels can lead the locator. Give it a short chance to catch up.
            withTimeoutOrNull(120) { destinationJob.join() }
            destinationJob.cancel()
        } else {
            destinationJob.cancel()
            val location = turn.origin.copy(
                text = Locator.Text(),
                locations = turn.origin.locations.copy(fragments = emptyList())
            )
            if (epub.go(location, animated = false)) {
                withTimeoutOrNull(1_400) { epub.currentLocator.first { samePage(turn.origin, it) } }
            }
        }
    }

    private fun visibleWebView(epub: EpubNavigatorFragment): WebView? {
        val visible = Rect()
        return epub.view?.allViews?.filterIsInstance<WebView>()?.firstOrNull {
            it.isShown && it.getGlobalVisibleRect(visible) && visible.width() >= host.width / 2
        }
    }

    private fun revealDestination(turn: Turn) {
        if (active.value !== turn || turn.returning) return
        turn.destinationReady.complete(Unit)
        overlay.invalidateSelf()
    }

    private fun discardPreparedPage() {
        preparedPage?.capture?.cancel()
        preparedPage = null
    }

    fun cancelGesture() {
        armed = false
        discardPreparedPage()
        active.value?.release?.complete(false)
    }

    fun detach() {
        screen.overlay.remove(overlay)
        cancelGesture()
        turnJob?.cancel()
        consumesGesture = false
    }

    private fun updatePointer(turn: Turn, distance: Float, dy: Float = 0f, eventTime: Long = 0L) {
        turn.inwardDrag = inwardDistance(turn.rightCorner, dy)
        val pull = maxOf(distance, turn.inwardDrag * 0.40f).coerceIn(0f, turn.width.toFloat())
        if (turn.rightGrab != null) {
            val elapsed = eventTime - turn.lastMove
            turn.velocity = if (turn.lastMove != 0L && elapsed in 1..80) {
                turn.velocity * 0.5f + (pull - turn.distance) / elapsed * 0.5f
            } else 0f
            turn.lastMove = eventTime
            turn.dragX = distance.coerceAtLeast(0f)
            turn.dragY = dy
        }
        turn.distance = pull
        overlay.invalidateSelf()
    }

    private fun inwardDistance(corner: PageTurnCorner?, dy: Float): Float = when (corner) {
        PageTurnCorner.TOP_RIGHT -> dy.coerceAtLeast(0f)
        PageTurnCorner.BOTTOM_RIGHT -> (-dy).coerceAtLeast(0f)
        else -> 0f
    }

    private suspend fun settle(turn: Turn, commit: Boolean) {
        val distance = turn.distance
        val end = if (commit) turn.width * 2f else 0f
        val remaining = abs(end - distance)
        val duration = (if (commit) 150 + 45 * sqrt(remaining / turn.width)
            else 105 + 48 * sqrt(remaining / turn.width)).roundToInt()
        val releaseSpec = if (turn.rightGrab != null) {
            val carry = (if (commit) turn.velocity else -turn.velocity).coerceAtLeast(0f)
            val entry = (carry * duration / remaining.coerceAtLeast(1f) * 0.22f).coerceIn(0.10f, 0.48f)
            tween<Float>(duration, easing = CubicBezierEasing(0.22f, entry, 0.42f, 1f))
        } else {
            tween<Float>(duration, easing = FastOutSlowInEasing)
        }
        Animatable(0f).animateTo(1f, releaseSpec) {
            turn.distance = distance + (end - distance) * value
            overlay.invalidateSelf()
        }
    }

    private suspend fun capturePage(areas: PageAreas, fromRight: Boolean): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (!host.isAttachedToWindow || !host.isShown || !host.hasWindowFocus()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val area = areas.window
        val scale = sqrt(1_650_000f / (area.width().toFloat() * area.height())).coerceAtMost(1f)
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
                        drawPaperOutside(areas.text, paperColor())
                    }
                    val texture = if (Build.VERSION.SDK_INT < 29 && !fromRight) {
                        val flip = Matrix().apply { setScale(-1f, 1f) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, flip, false)
                            .also { if (it !== bitmap) bitmap.recycle() }
                    } else {
                        bitmap
                    }
                    texture.prepareToDraw()
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
    private val positions = FloatArray((CORNER_COLUMNS + 1) * (PageCurlGeometry.CORNER_ROWS + 1) * 2)
    private val lighting = IntArray(positions.size / 2)
    private val textureCoords = FloatArray(positions.size)
    private val frontTriangles = ShortArray(CORNER_COLUMNS * PageCurlGeometry.CORNER_ROWS * 6)
    private val backTriangles = ShortArray(frontTriangles.size)
    private val bounds = RectF()
    private var shaderBitmap: Bitmap? = null
    private var lastPaperColor: Int? = null
    private var undersideFilter: ColorMatrixColorFilter? = null
    private var edgeMesh: PageCurlMesh? = null
    private var cornerMesh: PageCurlMesh? = null
    private val forwardFold = ForwardPageFold()
    private val foldLighting = IntArray(forwardFold.positions.size / 2)
    private val foldShadowPaint = Paint()
    private val foldShadowColors = IntArray(forwardFold.shadow.size / 2)

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
        inwardDrag: Float,
        rightGrab: PageGrip?,
        dragX: Float,
        dragY: Float,
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

        if (rightGrab != null) {
            drawForwardFold(canvas, page, width, height, distance, rightGrab, dragX, dragY, paper)
            return
        }

        val columns = if (rightCorner == null) EDGE_COLUMNS else CORNER_COLUMNS
        val previousMesh = if (rightCorner == null) edgeMesh else cornerMesh
        val drawsVertices = Build.VERSION.SDK_INT >= 29
        val (mesh, edge, bottomEdge) = PageCurlGeometry.boundPage(
            width.toFloat(), height.toFloat(), distance, fromRight, columns, rightCorner, inwardDrag, previousMesh,
            uniformRows = !drawsVertices
        )
        if (rightCorner == null) edgeMesh = mesh else cornerMesh = mesh
        drawShadow(canvas, width, height, distance, fromRight, edge, bottomEdge)

        val vertices = mesh.vertices
        val stride = PageCurlGeometry.FLOATS_PER_VERTEX
        for (vertex in 0 until vertices.size / stride) {
            val offset = vertex * stride
            val drawIndex = if (drawsVertices || fromRight) vertex else vertex + columns - 2 * (vertex % (columns + 1))
            positions[drawIndex * 2] = (0.5f + vertices[offset] * 0.5f) * width
            positions[drawIndex * 2 + 1] = (0.5f - vertices[offset + 1] * 0.5f) * height
            lighting[drawIndex] = shading(vertices[offset + 5])
            textureCoords[vertex * 2] = vertices[offset + 6] * page.width
            textureCoords[vertex * 2 + 1] = vertices[offset + 7] * page.height
        }
        if (drawsVertices) {
            drawFaces(canvas, page, mesh, paper)
        } else {
            canvas.drawBitmapMesh(page, columns, mesh.rows, positions, 0, lighting, 0, texturePaint)
        }
    }

    private fun drawShadow(canvas: Canvas, width: Int, height: Int, distance: Float, fromRight: Boolean, edge: Float, bottomEdge: Float) {
        val shadowWidth = (distance * 0.25f).coerceIn(1f, width * 0.14f)
        val shadowEnd = edge + if (fromRight) shadowWidth else -shadowWidth
        val shadowAlpha = (52f * ((2f - distance / width) * 5f).coerceIn(0f, 1f)).roundToInt()
        shadowPaint.shader = LinearGradient(edge, 0f, shadowEnd, 0f, Color.argb(shadowAlpha, 0, 0, 0),
            Color.TRANSPARENT, Shader.TileMode.CLAMP)
        val shadowState = canvas.save()
        canvas.skew((bottomEdge - edge) / height, 0f)
        canvas.drawRect(minOf(edge, shadowEnd), 0f, maxOf(edge, shadowEnd), height.toFloat(), shadowPaint)
        canvas.restoreToCount(shadowState)
    }

    private fun shading(normal: Float): Int {
        val shade = if (normal >= 0) 0.75f + 0.25f * normal else 0.70f - 0.19f * normal
        val channel = (shade * 255).roundToInt().coerceIn(0, 255)
        return Color.rgb(channel, channel, channel)
    }

    private fun drawForwardFold(
        canvas: Canvas, page: Bitmap, width: Int, height: Int, distance: Float,
        grab: PageGrip, dragX: Float, dragY: Float, paper: Int
    ) {
        val fold = forwardFold
        fold.shape(width.toFloat(), height.toFloat(), distance, dragX, dragY, grab.x, grab.y, page.width, page.height)
        val shadowAlpha = (52f * ((2f - distance / width) * 5f).coerceIn(0f, 1f)).roundToInt()
        val base = Color.argb(shadowAlpha, 0, 0, 0)
        val middle = Color.argb(shadowAlpha / 4, 0, 0, 0)
        for (index in foldShadowColors.indices step 3) {
            foldShadowColors[index] = base
            foldShadowColors[index + 1] = middle
        }
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, fold.shadow.size, fold.shadow, 0, null, 0,
            foldShadowColors, 0, fold.shadowIndices, 0, fold.shadowIndices.size, foldShadowPaint)
        for (vertex in foldLighting.indices) foldLighting[vertex] = shading(fold.normals[vertex])
        setTexture(page)
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, fold.positions.size, fold.positions, 0, fold.texture, 0,
            foldLighting, 0, fold.frontIndices, 0, fold.frontIndices.size, texturePaint)
        texturePaint.colorFilter = underside(paper)
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, fold.positions.size, fold.positions, 0, fold.texture, 0,
            foldLighting, 0, fold.backIndices, 0, fold.backIndices.size, texturePaint)
        texturePaint.colorFilter = null
    }

    private fun setTexture(page: Bitmap) {
        if (shaderBitmap !== page) {
            texturePaint.shader = BitmapShader(page, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shaderBitmap = page
        }
    }

    private fun drawFaces(canvas: Canvas, page: Bitmap, mesh: PageCurlMesh, paper: Int) {
        setTexture(page)
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
        texturePaint.colorFilter = underside(paper)
        drawTriangles(canvas, positionCount, backTriangles, backCount)
        texturePaint.colorFilter = null
    }

    private fun underside(paper: Int): ColorMatrixColorFilter? {
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
        return undersideFilter
    }

    private fun drawTriangles(canvas: Canvas, positionCount: Int, triangles: ShortArray, count: Int) {
        if (count == 0) return
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, positionCount, positions, 0, textureCoords, 0,
            lighting, 0, triangles, 0, count, texturePaint
        )
    }

    private companion object {
        const val EDGE_COLUMNS = 96
        const val CORNER_COLUMNS = 64
    }
}
