package io.github.tejesh_zephaniah.folio

import io.github.tejesh_zephaniah.folio.reader.components.PageCurlGeometry
import io.github.tejesh_zephaniah.folio.reader.components.PageTurnCorner
import io.github.tejesh_zephaniah.folio.reader.components.PageTurnProgress
import io.github.tejesh_zephaniah.folio.reader.components.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PageCurlGeometryTest {
    @Test
    fun boundSheetKeepsItsSpineAndEdgesAlignedDuringTheDrag() {
        val width = 400f
        val height = 800f
        val columns = 96
        val stride = PageCurlGeometry.FLOATS_PER_VERTEX
        for (fromRight in listOf(true, false)) {
            for (distance in listOf(0f, 25f, 200f, width)) {
                val mesh = PageCurlGeometry.boundPage(width, height, distance, fromRight, columns).mesh
                val spine = if (fromRight) 0 else columns * stride
                val free = if (fromRight) columns * stride else 0
                assertEquals(if (fromRight) -1f else 1f, mesh.vertices[spine], 0.0001f)
                val freeX = (mesh.vertices[free] + 1f) * width / 2f
                assertEquals(if (fromRight) width - distance else distance, freeX, 0.001f)
                for (column in 0..columns) {
                    val top = column * stride
                    val bottom = top + (columns + 1) * stride
                    assertEquals(mesh.vertices[top], mesh.vertices[bottom], 0f)
                    assertEquals(1f, mesh.vertices[top + 1], 0f)
                    assertEquals(-1f, mesh.vertices[bottom + 1], 0f)
                    if (column == 0) continue
                    val previous = top - stride
                    val dx = (mesh.vertices[top] - mesh.vertices[previous]) * width / 2f
                    val dz = (mesh.vertices[top + 2] - mesh.vertices[previous + 2]) * height
                    assertTrue(hypot(dx, dz) <= width / columns + 0.001f)
                }
            }
        }
    }

    @Test
    fun forwardCornerCurlPeelsFromTheGrabbedCornerAndClearsThePage() {
        val width = 400f
        val height = 800f
        val columns = 96
        val top = PageCurlGeometry.boundPage(width, height, 180f, true, columns, PageTurnCorner.TOP_RIGHT)
        val bottom = PageCurlGeometry.boundPage(width, height, 180f, true, columns, PageTurnCorner.BOTTOM_RIGHT)
        assertTrue(top.shadowEdge < top.bottomShadowEdge)
        assertEquals(top.shadowEdge, bottom.bottomShadowEdge, 0.001f)
        assertEquals(top.bottomShadowEdge, bottom.shadowEdge, 0.001f)
        for (corner in listOf(PageTurnCorner.TOP_RIGHT, PageTurnCorner.BOTTOM_RIGHT)) {
            val mesh = PageCurlGeometry.boundPage(width, height, 2 * width, true, columns, corner).mesh
            assertTrue(mesh.vertices.all(Float::isFinite))
            assertTrue(mesh.vertices.indices.step(PageCurlGeometry.FLOATS_PER_VERTEX).all { mesh.vertices[it] <= -1f })
        }
    }

    @Test
    fun boundSheetLeavesTheScreenAtTheEndOfOneTurn() {
        for (fromRight in listOf(true, false)) {
            val mesh = PageCurlGeometry.boundPage(400f, 800f, 800f, fromRight, 96).mesh
            for (index in mesh.vertices.indices step PageCurlGeometry.FLOATS_PER_VERTEX) {
                val x = mesh.vertices[index]
                assertTrue(if (fromRight) x <= -1f else x >= 1f)
                assertTrue(mesh.vertices[index + 8] in 0f..1f)
            }
        }
    }

    @Test
    fun unpulledPageRemainsFlatAndFillsTheViewport() {
        val mesh = PageCurlGeometry.create(
            width = 400f,
            height = 800f,
            pointer = Pair(400f, 0f),
            corner = PageTurnCorner.TOP_RIGHT
        )
        val xCoordinates = mesh.vertices.indices
            .filter { it % 9 == 0 }
            .map { mesh.vertices[it] }
        val yCoordinates = mesh.vertices.indices
            .filter { it % 9 == 1 }
            .map { mesh.vertices[it] }

        assertEquals(-1f, xCoordinates.minOrNull()!!, 0.0001f)
        assertEquals(1f, xCoordinates.maxOrNull()!!, 0.0001f)
        assertEquals(-1f, yCoordinates.minOrNull()!!, 0.0001f)
        assertEquals(1f, yCoordinates.maxOrNull()!!, 0.0001f)
        assertTrue(mesh.vertices.all(Float::isFinite))
    }

    @Test
    fun grabbedCornerTracksTwoDimensionalPointerForEveryCorner() {
        val cases = listOf(
            Triple(PageTurnCorner.TOP_LEFT, Pair(80f, 100f), 0 to 0),
            Triple(PageTurnCorner.TOP_RIGHT, Pair(320f, 100f), 32 to 0),
            Triple(PageTurnCorner.BOTTOM_LEFT, Pair(80f, 700f), 0 to 48),
            Triple(PageTurnCorner.BOTTOM_RIGHT, Pair(320f, 700f), 32 to 48)
        )

        cases.forEach { (corner, pointer, gridPoint) ->
            val mesh = PageCurlGeometry.create(400f, 800f, pointer, corner)
            val vertexIndex = (gridPoint.second * (mesh.columns + 1) + gridPoint.first) * 9
            val screenX = (mesh.vertices[vertexIndex] + 1f) * 200f
            val screenY = (0.5f - mesh.vertices[vertexIndex + 1] / 2f) * 800f

            assertEquals(pointer.first, screenX, 1f)
            assertEquals(pointer.second, screenY, 1f)
            assertTrue(mesh.vertices[vertexIndex + 2] < 0f)
            assertTrue(mesh.vertices.all(Float::isFinite))
        }
    }

    @Test
    fun forwardAndBackwardProgressAreSymmetricAndIgnoreVerticalMovement() {
        val forwardDistance = PageTurnProgress.distance(
            startX = 380f,
            currentX = 220f,
            direction = TurnDirection.FORWARD
        )
        val backwardDistance = PageTurnProgress.distance(
            startX = 20f,
            currentX = 180f,
            direction = TurnDirection.BACKWARD
        )
        val verticalOnlyForward = PageTurnProgress.distance(
            startX = 380f,
            currentX = 380f,
            direction = TurnDirection.FORWARD
        )
        val verticalOnlyBackward = PageTurnProgress.distance(
            startX = 20f,
            currentX = 20f,
            direction = TurnDirection.BACKWARD
        )

        assertEquals(forwardDistance, backwardDistance, 0.001f)
        assertEquals(160f, forwardDistance, 0.001f)
        assertEquals(0f, verticalOnlyForward, 0f)
        assertEquals(0f, verticalOnlyBackward, 0f)
    }

    @Test
    fun aLongHeldDragCanGlideIntoTheNextPageTurn() {
        val moved = PageTurnProgress.distance(
            startX = 380f,
            currentX = 0f,
            direction = TurnDirection.FORWARD
        )
        val completed = PageTurnProgress.completedTurns(moved, width = 400f)
        val remainder = PageTurnProgress.remainder(moved, width = 400f, completedTurns = completed)

        assertEquals(1, completed)
        assertEquals(120f, remainder, 0.001f)
        assertTrue(remainder / (400f * 0.9f) >= 0.32f)
    }

    @Test
    fun mirroredCornerDragsProduceMirroredPageGeometry() {
        val width = 400f
        val height = 800f
        val left = PageCurlGeometry.create(
            width,
            height,
            Pair(80f, 100f),
            PageTurnCorner.TOP_LEFT
        )
        val right = PageCurlGeometry.create(
            width,
            height,
            Pair(320f, 100f),
            PageTurnCorner.TOP_RIGHT
        )

        for (row in 0..left.rows) {
            for (column in 0..left.columns) {
                val leftIndex = (row * (left.columns + 1) + column) * 9
                val rightIndex = (row * (right.columns + 1) + (right.columns - column)) * 9
                val leftX = (left.vertices[leftIndex] + 1f) * width / 2f
                val rightX = (right.vertices[rightIndex] + 1f) * width / 2f
                val leftY = (0.5f - left.vertices[leftIndex + 1] / 2f) * height
                val rightY = (0.5f - right.vertices[rightIndex + 1] / 2f) * height

                assertTrue(abs((width - leftX) - rightX) < 1f)
                assertTrue(abs(leftY - rightY) < 1f)
            }
        }
    }
}
