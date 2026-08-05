package io.nekohasekai.sfa.utils

import android.content.Context

object PxlLocalPreferences {
    private const val FILE_NAME = "pxlnet_local"
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_UPDATE_DEFAULTS_INITIALIZED = "update_defaults_initialized"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isGuardEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_GUARD_ENABLED, true)

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_GUARD_ENABLED, enabled).apply()
    }

    fun shouldShowOnboarding(context: Context): Boolean =
        !preferences(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun finishOnboarding(context: Context) {
        preferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    /** Returns true once, so branded update defaults do not overwrite a later user choice. */
    fun initializeUpdateDefaults(context: Context): Boolean {
        val preferences = preferences(context)
        if (preferences.getBoolean(KEY_UPDATE_DEFAULTS_INITIALIZED, false)) return false
        preferences.edit().putBoolean(KEY_UPDATE_DEFAULTS_INITIALIZED, true).apply()
        return true
    }
}
