package io.nekohasekai.sfa.compose.component

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/** A non-consuming spring press treatment; the normal click handler keeps ownership of taps. */
fun Modifier.pxlPressMotion(enabled: Boolean = true, pressedScale: Float = 0.96f): Modifier = composed {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (motionEnabled && enabled && pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "pxl-press-scale",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    pressed = false
                }
            }
        }
}
