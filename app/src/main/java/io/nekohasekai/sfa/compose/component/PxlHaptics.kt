package io.nekohasekai.sfa.compose.component

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import io.nekohasekai.sfa.utils.PxlUiPreferences

/** Semantic feedback with a broadly supported fallback, respecting app and system settings. */
class PxlHaptics internal constructor(
    private val view: View,
    private val enabled: Boolean,
) {
    fun toggle(checked: Boolean) {
        if (!enabled) return
        val kind = if (Build.VERSION.SDK_INT >= 34) {
            if (checked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (!view.performHapticFeedback(kind) && kind != HapticFeedbackConstants.VIRTUAL_KEY) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun sliderDetent() {
        if (!enabled) return
        // Only two detent crossings exist here; VIRTUAL_KEY remains perceptible on basic motors.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

@Composable
fun rememberPxlHaptics(): PxlHaptics {
    val view = LocalView.current
    val enabled by PxlUiPreferences.hapticsEnabled.collectAsState()
    return PxlHaptics(view, enabled)
}
