package io.nekohasekai.sfa.compose.component

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
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
    Canvas(modifier.size(size)) {
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
