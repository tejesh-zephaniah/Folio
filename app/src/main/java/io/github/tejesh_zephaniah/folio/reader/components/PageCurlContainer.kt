package io.github.tejesh_zephaniah.folio.reader.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/**
 * Flat, reliable page container replacing the experimental 3D OpenGL page-curl.
 * Provides subtle horizontal slide transitions, left/right tap, and horizontal swipe page turns.
 */
@Composable
fun PageCurlContainer(
    currentPage: Int,
    totalPages: Int,
    pageBitmaps: Map<Int, Bitmap>,
    onPageChange: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var totalDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentPage, totalPages) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 50f
                        if (totalDrag < -threshold && currentPage < totalPages) {
                            onPageChange(currentPage + 1)
                        } else if (totalDrag > threshold && currentPage > 1) {
                            onPageChange(currentPage - 1)
                        }
                        totalDrag = 0f
                    }
                )
            }
            .pointerInput(currentPage, totalPages) {
                detectTapGestures { offset ->
                    val width = size.width
                    when {
                        offset.x < width * 0.22f -> {
                            if (currentPage > 1) onPageChange(currentPage - 1)
                        }
                        offset.x > width * 0.78f -> {
                            if (currentPage < totalPages) onPageChange(currentPage + 1)
                        }
                        else -> {
                            onCenterTap()
                        }
                    }
                }
            }
    ) {
        val currentBitmap = pageBitmaps[currentPage]
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                val animDuration = 180
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(animDuration)) { width -> (width * 0.25f).toInt() } togetherWith
                        slideOutHorizontally(animationSpec = tween(animDuration)) { width -> (-width * 0.25f).toInt() }
                } else {
                    slideInHorizontally(animationSpec = tween(animDuration)) { width -> (-width * 0.25f).toInt() } togetherWith
                        slideOutHorizontally(animationSpec = tween(animDuration)) { width -> (width * 0.25f).toInt() }
                }
            },
            label = "FlatPageTransition",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val bmp = pageBitmaps[page] ?: currentBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page $page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
