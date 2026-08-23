package com.huh.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** Tintable vector recreation of the supplied ear/question-mark brand mark. */
@Composable
fun HuhMark(
    modifier: Modifier = Modifier,
    color: Color = HuhAccent,
) {
    Canvas(modifier) {
        val unit = minOf(size.width, size.height) / 108f
        val left = (size.width - 108f * unit) / 2f
        val top = (size.height - 108f * unit) / 2f
        val stroke = Stroke(
            width = 7.5f * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun path(block: Path.() -> Unit) = Path().apply(block)
        withTransform({ translate(left, top) }) {
            drawPath(
                path {
                    moveTo(41f * unit, 91f * unit)
                    cubicTo(22f * unit, 85f * unit, 12f * unit, 69f * unit, 14f * unit, 50f * unit)
                    cubicTo(16f * unit, 29f * unit, 33f * unit, 14f * unit, 54f * unit, 14f * unit)
                    cubicTo(77f * unit, 14f * unit, 94f * unit, 32f * unit, 94f * unit, 55f * unit)
                },
                color,
                style = stroke,
            )
            drawPath(
                path {
                    moveTo(52f * unit, 84f * unit)
                    cubicTo(34f * unit, 83f * unit, 23f * unit, 70f * unit, 23f * unit, 54f * unit)
                    cubicTo(23f * unit, 37f * unit, 36f * unit, 24f * unit, 53f * unit, 24f * unit)
                    cubicTo(71f * unit, 24f * unit, 84f * unit, 38f * unit, 84f * unit, 56f * unit)
                },
                color,
                style = stroke,
            )
            drawPath(
                path {
                    moveTo(39f * unit, 54f * unit)
                    cubicTo(39f * unit, 45f * unit, 45f * unit, 39f * unit, 54f * unit, 39f * unit)
                    cubicTo(64f * unit, 39f * unit, 71f * unit, 46f * unit, 71f * unit, 56f * unit)
                    cubicTo(71f * unit, 66f * unit, 64f * unit, 73f * unit, 54f * unit, 73f * unit)
                    lineTo(54f * unit, 83f * unit)
                },
                color,
                style = stroke,
            )
            drawCircle(color = color, radius = 4f * unit, center = Offset(54f * unit, 94f * unit))
        }
    }
}

@Composable
fun ListeningMark(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = HuhAccent,
) {
    val transition = rememberInfiniteTransition(label = "listening ripple")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ripple progress",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        if (active) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * (0.31f + pulse * 0.17f)
                drawCircle(
                    color = color.copy(alpha = (1f - pulse) * 0.28f),
                    radius = radius,
                    style = Stroke(width = size.minDimension * 0.025f),
                )
            }
        }
        HuhMark(Modifier.fillMaxSize(), color)
    }
}
