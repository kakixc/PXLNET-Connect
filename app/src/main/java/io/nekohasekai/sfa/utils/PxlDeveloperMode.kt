package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PxlDeveloperMode {
    private val mutableEnabled = MutableStateFlow(Settings.developerMode)
    val enabled = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        Settings.developerMode = enabled
        mutableEnabled.value = enabled
    }
}
