package io.github.tejesh_zephaniah.folio.reader.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class PageTurnCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

internal object PageTurnProgress {
    const val TURN_DISTANCE_FRACTION = 0.65f

    fun distance(startX: Float, currentX: Float, direction: TurnDirection): Float {
        val directionSign = if (direction == TurnDirection.FORWARD) -1f else 1f
        return ((currentX - startX) * directionSign).coerceAtLeast(0f)
    }

    fun completedTurns(distance: Float, width: Float): Int {
        if (width <= 0f) return 0
        return floor(distance / (width * TURN_DISTANCE_FRACTION)).toInt()
    }

    fun remainder(distance: Float, width: Float, completedTurns: Int): Float =
        (distance - completedTurns * width * TURN_DISTANCE_FRACTION).coerceAtLeast(0f)
}

internal data class PageCurlMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
    val columns: Int,
    val rows: Int
)

internal data class BoundPageCurl(val mesh: PageCurlMesh, val shadowEdge: Float, val bottomShadowEdge: Float = shadowEdge)

internal object PageCurlGeometry {
    const val FLOATS_PER_VERTEX = 9

    fun boundPage(
        width: Float,
        height: Float,
        distance: Float,
        fromRight: Boolean,
        columns: Int,
        rightCorner: PageTurnCorner? = null
    ): BoundPageCurl {
        val pull = distance.coerceIn(0f, 2f * width)
        if (pull == 0f) {
            return BoundPageCurl(flatPage(width, height, columns), if (fromRight) width else 0f)
        }

        val corner = rightCorner.takeIf { fromRight }
        val rows = if (corner != null) 16 else 1
        val taper = 0.34f * ((2f * width - pull) / width).coerceIn(0f, 1f)
        val halfTurn = PI.toFloat()
        val vertices = FloatArray((columns + 1) * (rows + 1) * FLOATS_PER_VERTEX)
        var topEdge = 0f
        var bottomEdge = 0f
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            val awayFromCorner = when (corner) {
                PageTurnCorner.TOP_RIGHT -> v
                PageTurnCorner.BOTTOM_RIGHT -> 1f - v
                else -> 0f
            }
            // Vary the pull across the sheet while keeping the same bounded fold at each row.
            val rowPull = pull * (1f - taper * awayFromCorner)
            val radius = minOf(width * 0.09f, rowPull / 4f)
            val arcLength = halfTurn * radius
            val crease = width - (rowPull + arcLength) * 0.5f
            val edge = if (fromRight) crease + radius else width - crease - radius
            if (row == 0) topEdge = edge
            if (row == rows) bottomEdge = edge
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val pageX = (if (fromRight) u else 1f - u) * width
                val pastCrease = (pageX - crease).coerceAtLeast(0f)
                val angle = (pastCrease / radius).coerceAtMost(halfTurn)
                val projected = pageX - pastCrease + radius * sin(angle) -
                    (pastCrease - arcLength).coerceAtLeast(0f)
                val x = if (fromRight) projected else width - projected
                val index = (row * (columns + 1) + column) * FLOATS_PER_VERTEX
                vertices[index] = x / width * 2f - 1f
                vertices[index + 1] = 1f - 2f * v
                vertices[index + 2] = -radius * (1f - cos(angle)) / max(width, height)
                vertices[index + 5] = cos(angle)
                vertices[index + 6] = u
                vertices[index + 7] = v
                vertices[index + 8] = angle / halfTurn
            }
        }
        return BoundPageCurl(
            PageCurlMesh(vertices, createIndices(columns, rows), columns, rows),
            topEdge,
            bottomEdge
        )
    }


    fun create(
        width: Float,
        height: Float,
        pointer: Pair<Float, Float>,
        corner: PageTurnCorner,
        columns: Int = 32,
        rows: Int = 48,
        edgeAnchorY: Float? = null
    ): PageCurlMesh {
        require(width > 0f && height > 0f)
        require(columns >= 2 && rows >= 2)

        val safeWidth = width
        val safeHeight = height
        val cornerX = if (corner == PageTurnCorner.TOP_LEFT || corner == PageTurnCorner.BOTTOM_LEFT) {
            0f
        } else {
            safeWidth
        }
        val cornerY = edgeAnchorY ?: if (corner == PageTurnCorner.TOP_LEFT || corner == PageTurnCorner.TOP_RIGHT) {
            0f
        } else {
            safeHeight
        }

        val dx = pointer.first - cornerX
        val dy = pointer.second - cornerY
        val dragDistance = hypot(dx, dy)
        if (dragDistance < 0.5f) return flatPage(safeWidth, safeHeight, columns, rows)

        val maxPull = safeWidth * 0.9f
        val progress = (dragDistance / maxPull).coerceIn(0f, 1f)
        val turnAngle = (PI * sqrt(progress.toDouble())).toFloat()
        val denominator = 1f - cos(turnAngle)
        val pivot = if (denominator < 0.0001f) {
            Pair(cornerX, cornerY)
        } else {
            Pair(
                (pointer.first - cornerX * cos(turnAngle)) / denominator,
                (pointer.second - cornerY * cos(turnAngle)) / denominator
            )
        }

        val normalX = cornerX - pivot.first
        val normalY = cornerY - pivot.second
        val normalLength = hypot(normalX, normalY).coerceAtLeast(0.001f)
        val nx = normalX / normalLength
        val ny = normalY / normalLength
        val tangentX = -ny
        val tangentY = nx
        val foldWidth = max(minOf(safeWidth, safeHeight) * 0.08f, normalLength * 0.42f)
        val aspectScale = max(safeWidth, safeHeight)

        val vertices = FloatArray((columns + 1) * (rows + 1) * FLOATS_PER_VERTEX)
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            val pageY = v * safeHeight
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val pageX = u * safeWidth
                val relativeX = pageX - pivot.first
                val relativeY = pageY - pivot.second
                val normalDistance = relativeX * nx + relativeY * ny
                val tangentDistance = relativeX * tangentX + relativeY * tangentY
                val curve = 1.0f
                val bend = smoothStep(0f, foldWidth, normalDistance.coerceAtLeast(0f))
                val angle = turnAngle * bend
                val projectedNormal = normalDistance * cos(angle)
                val zPixels = normalDistance * sin(angle)
                val deformedX = pivot.first + tangentDistance * tangentX + projectedNormal * nx
                val deformedY = pivot.second + tangentDistance * tangentY + projectedNormal * ny

                val vertexIndex = (row * (columns + 1) + column) * FLOATS_PER_VERTEX
                vertices[vertexIndex] = ((deformedX / safeWidth) - 0.5f) * 2f
                vertices[vertexIndex + 1] = (0.5f - deformedY / safeHeight) * 2f
                vertices[vertexIndex + 2] = (-zPixels / aspectScale).coerceIn(-0.75f, 0.75f)
                vertices[vertexIndex + 6] = u
                vertices[vertexIndex + 7] = v
                vertices[vertexIndex + 8] = (angle / PI.toFloat()).coerceIn(0f, 1f)
            }
        }

        for (row in 0..rows) {
            for (column in 0..columns) {
                val index = (row * (columns + 1) + column) * FLOATS_PER_VERTEX
                val left = (column - 1).coerceAtLeast(0)
                val right = (column + 1).coerceAtMost(columns)
                val top = (row - 1).coerceAtLeast(0)
                val bottom = (row + 1).coerceAtMost(rows)
                val leftIndex = (row * (columns + 1) + left) * FLOATS_PER_VERTEX
                val rightIndex = (row * (columns + 1) + right) * FLOATS_PER_VERTEX
                val topIndex = (top * (columns + 1) + column) * FLOATS_PER_VERTEX
                val bottomIndex = (bottom * (columns + 1) + column) * FLOATS_PER_VERTEX
                val ax = vertices[bottomIndex] - vertices[topIndex]
                val ay = vertices[bottomIndex + 1] - vertices[topIndex + 1]
                val az = vertices[bottomIndex + 2] - vertices[topIndex + 2]
                val bx = vertices[rightIndex] - vertices[leftIndex]
                val by = vertices[rightIndex + 1] - vertices[leftIndex + 1]
                val bz = vertices[rightIndex + 2] - vertices[leftIndex + 2]
                val normalX = ay * bz - az * by
                val normalY = az * bx - ax * bz
                val normalZ = ax * by - ay * bx
                val length = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
                    .coerceAtLeast(0.0001f)
                vertices[index + 3] = normalX / length
                vertices[index + 4] = normalY / length
                vertices[index + 5] = normalZ / length
            }
        }

        val indices = createIndices(columns, rows)
        return PageCurlMesh(vertices, indices, columns, rows)
    }

    fun flatPage(width: Float, height: Float, columns: Int = 1, rows: Int = 1): PageCurlMesh {
        require(width > 0f && height > 0f)
        require(columns >= 1 && rows >= 1)

        val vertices = FloatArray((columns + 1) * (rows + 1) * FLOATS_PER_VERTEX)
        for (row in 0..rows) {
            val v = row.toFloat() / rows
            for (column in 0..columns) {
                val u = column.toFloat() / columns
                val index = (row * (columns + 1) + column) * FLOATS_PER_VERTEX
                vertices[index] = (u - 0.5f) * 2f
                vertices[index + 1] = (0.5f - v) * 2f
                vertices[index + 5] = 1f
                vertices[index + 6] = u
                vertices[index + 7] = v
            }
        }

        return PageCurlMesh(vertices, createIndices(columns, rows), columns, rows)
    }

    private fun createIndices(columns: Int, rows: Int): ShortArray {
        val indices = ShortArray(columns * rows * 6)
        var indexOffset = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val topLeft = (row * (columns + 1) + column).toShort()
                val topRight = (topLeft + 1).toShort()
                val bottomLeft = (topLeft + columns + 1).toShort()
                val bottomRight = (bottomLeft + 1).toShort()
                indices[indexOffset++] = topLeft
                indices[indexOffset++] = bottomLeft
                indices[indexOffset++] = topRight
                indices[indexOffset++] = topRight
                indices[indexOffset++] = bottomLeft
                indices[indexOffset++] = bottomRight
            }
        }
        return indices
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

}
