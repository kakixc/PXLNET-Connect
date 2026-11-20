package io.nekohasekai.sfa.compose.component

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min

/** A compact loading shape for shared UI, including the older Compose Legacy build. */
@Composable
fun PxlShapeLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val phase = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "pxl-loading-shape")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
            label = "pxl-loading-morph",
        )
        animatedPhase
    } else 0f
    Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val side = min(this.size.width, this.size.height)
        val morph = ((1f - cos(phase * Math.PI * 2).toFloat()) / 2f)
        val width = side * (0.68f + 0.16f * morph)
        val height = side * (0.68f - 0.12f * morph)
        val corner = side * (0.2f + 0.2f * morph)
        rotate(phase * 360f) {
            drawRoundRect(
                color = color,
                topLeft = Offset((this.size.width - width) / 2f, (this.size.height - height) / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(corner),
            )
        }
    }
}

/** Indeterminate connection motion with a loop that also works on the legacy Material library. */
@Composable
fun ConnectLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    val motion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val phase = if (motion) {
        val transition = rememberInfiniteTransition(label = "connect-loading")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "connect-shape-cycle",
        )
        value
    } else 0f
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val segment = phase.toInt().coerceAtMost(2)
        val blend = FastOutSlowInEasing.transform((phase - segment).coerceIn(0f, 1f))
        val radius = size.minDimension * 0.40f
        val points = List(36) { index ->
            val angle = (2.0 * Math.PI * index / 36).toFloat()
            fun silhouette(frame: Int): Float = when (frame) {
                1 -> 0.92f + 0.11f * cos(angle * 4f)
                2 -> 0.84f + 0.16f * cos(angle * 6f)
                else -> 1f
            }
            val start = silhouette(segment)
            val distance = radius * (start + (silhouette(segment + 1) - start) * blend)
            center + Offset(kotlin.math.cos(angle) * distance, kotlin.math.sin(angle) * distance)
        }
        val path = Path().apply {
            val start = (points.last() + points.first()) / 2f
            moveTo(start.x, start.y)
            points.forEachIndexed { index, point ->
                val next = (point + points[(index + 1) % points.size]) / 2f
                quadraticTo(point.x, point.y, next.x, next.y)
            }
            close()
        }
        rotate(phase * 120f) { drawPath(path, color) }
    }
}
