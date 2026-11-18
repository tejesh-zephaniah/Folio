package io.github.tejesh_zephaniah.folio.data.model

import kotlin.math.roundToInt

internal fun pagePercentage(page: Int, totalPages: Int): Int {
    if (totalPages <= 0) return 0
    return (page.coerceIn(0, totalPages) * 100L / totalPages).toInt()
}

internal fun bookPageFromLocator(position: Int?, totalProgression: Double?, totalPages: Int): Int? {
    if (position != null && position in 1..totalPages) return position
    if (totalPages <= 0 || totalProgression == null || !totalProgression.isFinite()) return null
    return 1 + (totalProgression.coerceIn(0.0, 1.0) * (totalPages - 1)).roundToInt()
}

internal fun bookmarkPageTitle(page: Int, totalPages: Int): String =
    "Page $page (${pagePercentage(page, totalPages)}%)"

fun mapPagePosition(page: Int, sourcePageCount: Int, targetPageCount: Int): Int {
    if (page <= 0) return 0
    if (targetPageCount <= 1) return 1
    if (sourcePageCount <= 1) return page.coerceIn(1, targetPageCount)

    val boundedPage = page.coerceIn(1, sourcePageCount)
    val progress = (boundedPage - 1).toFloat() / (sourcePageCount - 1)
    return (progress * (targetPageCount - 1)).roundToInt() + 1
}
