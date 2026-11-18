package io.github.tejesh_zephaniah.folio.reader.components

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.opengl.GLSurfaceView
import kotlin.math.abs
import kotlin.math.max

internal enum class TurnDirection {
    FORWARD,
    BACKWARD
}

internal class PageCurlGLView(context: Context) : GLSurfaceView(context) {
    private val pageRenderer = PageCurlGLRenderer()
    private val edgeBand = 76f * resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var currentPage = 1
    private var totalPages = 1
    private var direction = TurnDirection.FORWARD
    private var corner = PageTurnCorner.BOTTOM_RIGHT
    private var cornerX = 0f
    private var cornerY = 0f
    private var directionSign = -1f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L
    private var velocityX = 0f
    private var committedTurns = 0
    private var isArmed = false
    private var hasMoved = false
    private var isDragging = false
    private var isSettling = false
    private var settleAnimator: ValueAnimator? = null

    var onPageChange: (Int) -> Unit = {}
    var onCenterTap: () -> Unit = {}

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(pageRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = true
        isFocusable = true
    }

    fun setPageData(bitmaps: Map<Int, Bitmap>, page: Int, pages: Int) {
        val pageCount = pages.coerceAtLeast(1)
        val safePage = page.coerceIn(1, pageCount)
        currentPage = safePage
        totalPages = pageCount
        queueEvent {
            pageRenderer.updatePageData(bitmaps, safePage, pageCount)
            requestRender()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) onResume() else onPause()
    }

    override fun onDetachedFromWindow() {
        settleAnimator?.cancel()
        onPause()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isSettling) return true
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                lastTime = event.eventTime
                velocityX = 0f
                committedTurns = 0
                isArmed = isNearPageEdge(downX, downY)
                hasMoved = false
                isDragging = false
                direction = if (downX < width / 2f) TurnDirection.BACKWARD else TurnDirection.FORWARD
                directionSign = if (direction == TurnDirection.FORWARD) -1f else 1f
                selectCorner(downX, downY)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (max(abs(event.x - downX), abs(event.y - downY)) >= touchSlop) {
                    hasMoved = true
                }
                if (isArmed) updateDrag(event.x, event.y, event.eventTime)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    updateDrag(event.x, event.y, event.eventTime)
                    settleDrag()
                } else if (!hasMoved) {
                    val widthPx = width.toFloat().coerceAtLeast(1f)
                    if (downX < widthPx * 0.22f && currentPage > 1) {
                        turnPageProgrammatically(TurnDirection.BACKWARD)
                    } else if (downX > widthPx * 0.78f && currentPage < totalPages) {
                        turnPageProgrammatically(TurnDirection.FORWARD)
                    } else {
                        performClick()
                        onCenterTap()
                    }
                }
                isArmed = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) animateTurn(commit = false, currentPointer())
                isArmed = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateDrag(x: Float, y: Float, eventTime: Long) {
        val deltaTime = (eventTime - lastTime).coerceAtLeast(1L)
        velocityX = (x - lastX) / deltaTime * 1000f
        lastX = x
        lastY = y
        lastTime = eventTime

        if (!isDragging) {
            if (max(abs(x - downX), abs(y - downY)) < touchSlop) return
            hasMoved = true
            if (!isArmed || !canTurn()) return
            isDragging = true
            queueEvent {
                pageRenderer.beginTurn(direction, corner, cornerX, cornerY)
                requestRender()
            }
        }

        val widthPx = width.toFloat().coerceAtLeast(1f)
        val moved = PageTurnProgress.distance(downX, x, direction)
        val completed = PageTurnProgress.completedTurns(moved, widthPx)
        while (committedTurns < completed && canTurn()) {
            val nextPage = pageAfterCurrent()
            currentPage = nextPage
            committedTurns++
            val canContinue = canTurn()
            queueEvent {
                pageRenderer.commitPage(nextPage)
                if (canContinue) {
                    pageRenderer.beginTurn(direction, corner, cornerX, cornerY)
                }
                requestRender()
            }
            onPageChange(nextPage)
        }

        val partialDistance = PageTurnProgress.remainder(moved, widthPx, committedTurns)
        val pointer = Pair(
            cornerX + directionSign * partialDistance,
            (cornerY + y - downY).coerceIn(-height * 0.15f, height * 1.15f)
        )
        queueEvent {
            pageRenderer.updateTurn(pointer.first, pointer.second)
            requestRender()
        }
    }

    fun turnPageProgrammatically(dir: TurnDirection) {
        if (isSettling || isDragging) return
        direction = dir
        directionSign = if (direction == TurnDirection.FORWARD) -1f else 1f
        if (!canTurn()) return

        corner = if (direction == TurnDirection.FORWARD) PageTurnCorner.BOTTOM_RIGHT else PageTurnCorner.BOTTOM_LEFT
        cornerX = if (direction == TurnDirection.FORWARD) width.toFloat() else 0f
        cornerY = height.toFloat()

        queueEvent {
            pageRenderer.beginTurn(direction, corner, cornerX, cornerY)
            requestRender()
        }

        val start = Pair(cornerX, cornerY)
        animateTurn(commit = true, start = start)
    }

    private fun settleDrag() {
        if (!isDragging) return
        isDragging = false
        val pointer = currentPointer()
        val widthPx = width.toFloat().coerceAtLeast(1f)
        val progress = abs(pointer.first - cornerX) / (widthPx * 0.85f)
        val velocityTowardTurn = velocityX * directionSign
        val shouldCommit = canTurn() &&
            (progress >= 0.28f || (progress >= 0.08f && velocityTowardTurn >= 650f * resources.displayMetrics.density))
        animateTurn(shouldCommit, pointer)
    }

    private fun animateTurn(commit: Boolean, start: Pair<Float, Float>) {
        if (isSettling) return
        isSettling = true
        var wasCancelled = false
        val endX = if (commit) {
            cornerX + directionSign * width * 1.12f
        } else {
            cornerX
        }
        val endY = if (commit) start.second else cornerY
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (commit) 230L else 270L
            interpolator = DecelerateInterpolator(1.7f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val x = start.first + (endX - start.first) * fraction
                val y = start.second + (endY - start.second) * fraction
                queueEvent {
                    pageRenderer.updateTurn(x, y)
                    requestRender()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (wasCancelled) return
                    if (commit && canTurn()) {
                        val nextPage = pageAfterCurrent()
                        currentPage = nextPage
                        queueEvent {
                            pageRenderer.commitPage(nextPage)
                            requestRender()
                        }
                        onPageChange(nextPage)
                    } else {
                        queueEvent {
                            pageRenderer.cancelTurn()
                            requestRender()
                        }
                    }
                    isSettling = false
                    isArmed = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                    isSettling = false
                }
            })
        }
        settleAnimator?.cancel()
        settleAnimator = animator
        animator.start()
    }

    private fun currentPointer(): Pair<Float, Float> {
        val widthPx = width.toFloat().coerceAtLeast(1f)
        val moved = PageTurnProgress.distance(downX, lastX, direction)
        val partialDistance = PageTurnProgress.remainder(moved, widthPx, committedTurns)
        return Pair(
            cornerX + directionSign * partialDistance,
            (cornerY + lastY - downY).coerceIn(-height * 0.15f, height * 1.15f)
        )
    }

    private fun selectCorner(x: Float, y: Float) {
        val leftSide = direction == TurnDirection.BACKWARD
        val topCorner = y <= height / 2f
        corner = when {
            leftSide && topCorner -> PageTurnCorner.TOP_LEFT
            leftSide -> PageTurnCorner.BOTTOM_LEFT
            topCorner -> PageTurnCorner.TOP_RIGHT
            else -> PageTurnCorner.BOTTOM_RIGHT
        }
        cornerX = if (leftSide) 0f else width.toFloat()
        cornerY = if (topCorner) 0f else height.toFloat()
    }

    private fun isNearPageEdge(x: Float, y: Float): Boolean =
        x <= edgeBand || x >= width - edgeBand || y <= edgeBand || y >= height - edgeBand

    private fun canTurn(): Boolean = when (direction) {
        TurnDirection.FORWARD -> currentPage < totalPages
        TurnDirection.BACKWARD -> currentPage > 1
    }

    private fun pageAfterCurrent(): Int =
        if (direction == TurnDirection.FORWARD) currentPage + 1 else currentPage - 1
}
