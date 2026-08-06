package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PxlMascotSettings {
    private val mutableEnabled = MutableStateFlow(Settings.pxlnetMascotEnabled)
    val enabled = mutableEnabled.asStateFlow()
    private val mutableAnimationsEnabled = MutableStateFlow(Settings.pxlnetMascotAnimations)
    val animationsEnabled = mutableAnimationsEnabled.asStateFlow()
    private val mutableTipsEnabled = MutableStateFlow(Settings.pxlnetMascotTips)
    val tipsEnabled = mutableTipsEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        Settings.pxlnetMascotEnabled = enabled
        mutableEnabled.value = enabled
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        Settings.pxlnetMascotAnimations = enabled
        mutableAnimationsEnabled.value = enabled
    }

    fun setTipsEnabled(enabled: Boolean) {
        Settings.pxlnetMascotTips = enabled
        mutableTipsEnabled.value = enabled
    }
}
