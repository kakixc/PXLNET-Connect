package io.nekohasekai.sfa.utils

import android.content.Context

object PxlLocalPreferences {
    private const val FILE_NAME = "pxlnet_local"
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_UPDATE_DEFAULTS_INITIALIZED = "update_defaults_initialized"
    private const val KEY_QUICK_TILE_ADDED = "quick_tile_added"
    private const val KEY_COSMOS_UNLOCKED = "cosmos_unlocked"
    private const val KEY_LATENCY_SOURCE = "latency_source"

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

    /** Reopens the guide only. Profiles, account state and VPN settings stay untouched. */
    fun requestOnboarding(context: Context) {
        preferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETE, false).apply()
    }

    fun isQuickTileAdded(context: Context): Boolean =
        preferences(context).getBoolean(KEY_QUICK_TILE_ADDED, false)

    fun setQuickTileAdded(context: Context, added: Boolean) {
        preferences(context).edit().putBoolean(KEY_QUICK_TILE_ADDED, added).apply()
    }

    fun isCosmosUnlocked(context: Context): Boolean =
        preferences(context).getBoolean(KEY_COSMOS_UNLOCKED, false)

    fun unlockCosmos(context: Context) {
        preferences(context).edit().putBoolean(KEY_COSMOS_UNLOCKED, true).apply()
    }

    /** Keep the existing website test as the default until native probes pass device QA. */
    fun latencySource(context: Context): PxlLatencySource =
        PxlLatencySource.fromPersisted(preferences(context).getString(KEY_LATENCY_SOURCE, null))

    fun setLatencySource(context: Context, source: PxlLatencySource) {
        preferences(context).edit().putString(KEY_LATENCY_SOURCE, source.persistedValue).apply()
    }

    /** Returns true once, so branded update defaults do not overwrite a later user choice. */
    fun initializeUpdateDefaults(context: Context): Boolean {
        val preferences = preferences(context)
        if (preferences.getBoolean(KEY_UPDATE_DEFAULTS_INITIALIZED, false)) return false
        preferences.edit().putBoolean(KEY_UPDATE_DEFAULTS_INITIALIZED, true).apply()
        return true
    }
}
