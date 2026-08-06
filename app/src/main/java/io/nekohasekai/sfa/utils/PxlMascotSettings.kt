package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PxlMascotSettings {
    private val mutableEnabled = MutableStateFlow(Settings.pxlnetMascotEnabled)
    val enabled = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        Settings.pxlnetMascotEnabled = enabled
        mutableEnabled.value = enabled
    }
}
