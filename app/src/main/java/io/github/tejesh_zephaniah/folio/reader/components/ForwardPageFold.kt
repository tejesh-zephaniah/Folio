package io.github.tejesh_zephaniah.folio.reader.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The bend is deepest at the grip. Each row contains the exact front/back boundary. */
internal class ForwardPageFold {
    val positions = FloatArray((ROWS + 1) * ROW_VERTICES * 2)
    val texture = FloatArray(positions.size)
    val normals = FloatArray(positions.size / 2)
    val frontIndices = stripIndices(ROW_VERTICES, 0, SILHOUETTE)
    val backIndices = stripIndices(ROW_VERTICES, SILHOUETTE, ROW_VERTICES - 1)
    val shadow = FloatArray((ROWS + 1) * 6)
    val shadowIndices = stripIndices(3, 0, 2)

    fun shape(
        width: Float,
        height: Float,
        pull: Float,
        dragX: Float,
        dragY: Float,
        grabX: Float,
        grabY: Float,
        textureWidth: Int,
        textureHeight: Int
    ) {
        val contactY = grabY + dragY / height * 0.18f
        val anchor = ((contactY - 0.13f) / 0.74f).coerceIn(0f, 1f)
        val cornerBias = 1f - 2f * anchor
        val slope = -dragY / maxOf(dragX, width * 0.027f) - 0.38f * cornerBias * cornerBias * cornerBias
        val flexible = ((2f * width - pull) / width).coerceIn(0f, 1f)
        val tilt = 1.25f * slope / sqrt(1f + slope * slope) * flexible * flexible
        val nx = 1f / sqrt(1f + tilt * tilt)
        val ny = nx * tilt
        val normalPull = pull / nx
        val radius = minOf(normalPull * 0.24f, width * (0.065f + 0.025f * abs(tilt) + 0.018f * flexible))
        val arc = PI.toFloat() * radius
        val spine = maxOf(0f, ny * height) - (pull - width).coerceAtLeast(0f)
        val interiorShift = minOf(normalPull * 0.44f, width * 0.18f) * (1f - grabX) * flexible
        val crease = maxOf(
            nx * width + ny * anchor * height - (normalPull + arc) / 2f - interiorShift,
            spine
        )
        // Let the small lift bow around the grip, then relax the crease as the page rolls over.
        val roll = ((pull / width - 0.42f) / 0.65f).coerceIn(0f, 1f)
        val bow = 0.82f - 0.55f * roll * roll * (3f - 2f * roll)
        val flex = minOf(normalPull * 0.29f, width * 0.12f) * flexible * bow
        val spread = 0.23f + (1f - grabX) * 0.11f
        val shadowWidth = (pull * 0.25f).coerceIn(1f, width * 0.14f)

        for (row in 0..ROWS) {
            val v = row.toFloat() / ROWS
            val y = v * height
            val distance = v - anchor
            val squared = distance * distance
            val rowCrease = crease + flex * squared / (squared + spread)
            val rowOffset = y * ny
            val rowEntry = rowCrease - rowOffset
            for (col in 0 until ROW_VERTICES) {
                val step = (col - 1).coerceIn(0, ARC_STEPS)
                val sampleX = (rowEntry + arc * step / ARC_STEPS) / nx
                val x = when (col) {
                    0 -> 0f
                    ROW_VERTICES - 1 -> width
                    else -> sampleX.coerceIn(0f, width)
                }
                val past = nx * x - rowEntry
                val vertex = row * ROW_VERTICES + col
                val index = vertex * 2
                val projection = when {
                    past <= 0f -> {
                        normals[vertex] = 1f
                        0f
                    }
                    past >= arc -> {
                        normals[vertex] = -1f
                        2f * past - arc
                    }
                    else -> {
                        // Most vertices are on precomputed angles. Only clipped sheet edges need trig.
                        val exact = col in 1..ARC_STEPS + 1 && x == sampleX
                        val sine = if (exact) arcSines[step] else sin(past / radius)
                        normals[vertex] = if (exact) arcCosines[step] else cos(past / radius)
                        past - radius * sine
                    }
                }
                positions[index] = x - nx * projection
                positions[index + 1] = y - ny * projection
                texture[index] = x / width * textureWidth
                texture[index + 1] = v * textureHeight
            }
            val crestX = (rowEntry + arc / 2f) / nx
            val crestDisplacement = arc / 2f - radius
            val shadowX = crestX - nx * crestDisplacement
            val shadowY = y - ny * crestDisplacement
            for (col in 0..2) {
                val distanceFromCrest = shadowWidth * col * 0.5f
                val index = row * 6 + col * 2
                shadow[index] = shadowX + nx * distanceFromCrest
                shadow[index + 1] = shadowY + ny * distanceFromCrest
            }
        }
    }

    private fun stripIndices(rowVertices: Int, start: Int, end: Int): ShortArray {
        val indices = ShortArray(ROWS * (end - start) * 6)
        var next = 0
        for (row in 0 until ROWS) {
            for (col in start until end) {
                val top = row * rowVertices + col
                val bottom = top + rowVertices
                indices[next++] = top.toShort()
                indices[next++] = bottom.toShort()
                indices[next++] = (top + 1).toShort()
                indices[next++] = (top + 1).toShort()
                indices[next++] = bottom.toShort()
                indices[next++] = (bottom + 1).toShort()
            }
        }
        return indices
    }

    private companion object {
        const val ROWS = 22
        const val ARC_STEPS = 18
        const val ROW_VERTICES = ARC_STEPS + 3
        const val SILHOUETTE = ARC_STEPS / 2 + 1
        val arcSines = FloatArray(ARC_STEPS + 1) { sin(PI * it / ARC_STEPS).toFloat() }
        val arcCosines = FloatArray(ARC_STEPS + 1) { cos(PI * it / ARC_STEPS).toFloat() }
    }
}
