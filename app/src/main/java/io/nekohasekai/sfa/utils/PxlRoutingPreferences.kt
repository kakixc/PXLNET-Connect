package io.nekohasekai.sfa.utils

import android.content.Context

object PxlRoutingPreferences {
    private const val PREFERENCES = "pxlnet_routing"
    private const val SMART_ROUTING = "smart_routing"

    fun isSmartRouting(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(SMART_ROUTING, true)

    fun setSmartRouting(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SMART_ROUTING, enabled)
            .apply()
    }
}
