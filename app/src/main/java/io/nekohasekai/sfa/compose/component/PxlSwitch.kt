package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    val haptics = rememberPxlHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { value ->
                haptics.toggle(value)
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
