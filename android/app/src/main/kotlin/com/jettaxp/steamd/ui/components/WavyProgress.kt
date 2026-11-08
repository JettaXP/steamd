package com.jettaxp.steamd.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Material 3 Expressive style linear progress: a moving wave for the done part, a flat track for the rest. */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.secondaryContainer
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    Canvas(modifier.height(10.dp)) {
        val stroke = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 32.dp.toPx()
        val gap = 4.dp.toPx()
        val cy = size.height / 2
        val end = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(stroke / 2)
        val path = Path()
        var x = stroke / 2
        path.moveTo(x, cy + amplitude * sin(2 * PI * (x / wavelength - phase)).toFloat())
        while (x < end) {
            x = (x + 2f).coerceAtMost(end)
            path.lineTo(x, cy + amplitude * sin(2 * PI * (x / wavelength - phase)).toFloat())
        }
        drawPath(path, active, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val trackStart = end + gap + stroke
        if (trackStart < size.width - stroke / 2) {
            drawLine(track, Offset(trackStart, cy), Offset(size.width - stroke / 2, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(active, radius = stroke / 2, center = Offset(size.width - stroke / 2, cy))
    }
}
