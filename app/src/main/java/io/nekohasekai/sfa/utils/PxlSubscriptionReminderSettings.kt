package io.nekohasekai.sfa.utils

import android.content.Context
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PxlSubscriptionReminderSettings {
    private val mutableEnabled = MutableStateFlow(Settings.pxlnetSubscriptionReminders)
    val enabled = mutableEnabled.asStateFlow()

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.pxlnetSubscriptionReminders = enabled
        mutableEnabled.value = enabled
        if (enabled) {
            PxlSubscriptionReminderWork.schedule(context)
        } else {
            PxlSubscriptionReminderWork.cancel(context)
        }
    }
}
