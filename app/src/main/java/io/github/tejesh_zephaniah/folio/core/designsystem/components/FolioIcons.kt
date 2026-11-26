package io.github.tejesh_zephaniah.folio.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun FolioHomeIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // Roof peak
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.88f, h * 0.48f)
            lineTo(w * 0.88f, h * 0.85f)
            lineTo(w * 0.12f, h * 0.85f)
            lineTo(w * 0.12f, h * 0.48f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Door outline
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.4f, h * 0.55f),
            size = Size(w * 0.2f, h * 0.3f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

@Composable
fun FolioLibraryIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Book 1 (Left upright)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.18f, h * 0.68f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Book 2 (Center tall)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.40f, h * 0.10f),
            size = Size(w * 0.20f, h * 0.76f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Book 3 (Right tilted)
        val path = Path().apply {
            moveTo(w * 0.68f, h * 0.26f)
            lineTo(w * 0.86f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.86f)
            lineTo(w * 0.68f, h * 0.86f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun FolioExploreIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.41f
        drawCircle(tint, radius, center, style = Stroke(1.8.dp.toPx()))
        val needle = Path().apply {
            moveTo(center.x + radius * 0.57f, center.y - radius * 0.57f)
            lineTo(center.x + radius * 0.20f, center.y + radius * 0.20f)
            lineTo(center.x - radius * 0.57f, center.y + radius * 0.57f)
            lineTo(center.x - radius * 0.20f, center.y - radius * 0.20f)
            close()
        }
        drawPath(needle, tint)
    }
}

@Composable
fun FolioSearchIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val circleRadius = w * 0.32f
        val circleCenter = Offset(w * 0.42f, h * 0.42f)

        drawCircle(
            color = tint,
            radius = circleRadius,
            center = circleCenter,
            style = Stroke(width = 2.dp.toPx())
        )

        // Handle
        drawLine(
            color = tint,
            start = Offset(circleCenter.x + circleRadius * 0.7f, circleCenter.y + circleRadius * 0.7f),
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FolioCloseIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val inset = w * 0.28f

        drawLine(
            color = tint,
            start = Offset(inset, inset),
            end = Offset(w - inset, h - inset),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w - inset, inset),
            end = Offset(inset, h - inset),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FolioBookmarkIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    isBookmarked: Boolean = false
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.12f)
            lineTo(w * 0.78f, h * 0.12f)
            lineTo(w * 0.78f, h * 0.88f)
            lineTo(w * 0.50f, h * 0.65f)
            lineTo(w * 0.22f, h * 0.88f)
            close()
        }

        if (isBookmarked) {
            drawPath(path = path, color = tint)
        } else {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun FolioMoreDotsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dotRadius = (w * 0.08f).coerceIn(1.5.dp.toPx(), 2.2.dp.toPx())

        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.18f, h * 0.5f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.50f, h * 0.5f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.82f, h * 0.5f))
    }
}

@Composable
fun FolioFilterListIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        drawLine(tint, Offset(w * 0.2f, h * 0.30f), Offset(w * 0.8f, h * 0.30f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.50f), Offset(w * 0.72f, h * 0.50f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.70f), Offset(w * 0.62f, h * 0.70f), stroke, StrokeCap.Round)
    }
}

@Composable
fun FolioCheckmarkIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.82f, h * 0.28f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun FolioAddIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.22f),
            end = Offset(w * 0.5f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.22f, h * 0.5f),
            end = Offset(w * 0.78f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FolioBookBadgeIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()

        // Book body outline with left spine
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, h * 0.14f),
            size = Size(w * 0.60f, h * 0.72f),
            cornerRadius = CornerRadius(2.2.dp.toPx()),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Spine fold vertical line
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.14f),
            end = Offset(w * 0.34f, h * 0.86f),
            strokeWidth = stroke * 0.85f
        )
        // Two subtle title lines on cover
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.36f),
            end = Offset(w * 0.68f, h * 0.36f),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.50f),
            end = Offset(w * 0.62f, h * 0.50f),
            strokeWidth = stroke * 0.85f,
            cap = StrokeCap.Round
        )
    }
}


