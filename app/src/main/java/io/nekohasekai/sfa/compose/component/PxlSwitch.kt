package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Vibrates only for a user-triggered toggle, never when state is restored or updated externally. */
@Composable
fun PxlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { value ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                change(value)
            }
        },
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}
