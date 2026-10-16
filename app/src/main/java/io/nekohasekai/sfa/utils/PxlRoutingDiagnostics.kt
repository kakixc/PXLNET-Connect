package io.nekohasekai.sfa.utils

import android.content.Context
import io.nekohasekai.sfa.database.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps only preset outcomes, never domains, app package names, server tags or subscription URLs. */
object PxlRoutingDiagnostics {
    private const val PREFERENCES = "pxlnet_routing_diagnostics"
    private const val EVENTS = "events"
    private const val MAX_EVENTS = 15

    enum class Outcome(val value: String) { Applied("applied"), Reverted("reverted") }

    fun record(context: Context, preset: PxlRoutingPreset, outcome: Outcome) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val event = "$timestamp ${preset.key} ${outcome.value}"
        synchronized(this) {
            val previous = preferences.getString(EVENTS, null).orEmpty().lineSequence()
                .filter(String::isNotBlank).toList().takeLast(MAX_EVENTS - 1)
            preferences.edit().putString(EVENTS, (previous + event).joinToString("\n")).apply()
        }
    }

    internal fun snapshot(
        preset: PxlRoutingPreset,
        smartRulesEnabled: Boolean,
        perAppEnabled: Boolean,
        managedMode: Boolean,
    ): String =
        "preset=${preset.key}, smart_rules=${if (smartRulesEnabled) "on" else "off"}, " +
            "per_app=${if (perAppEnabled) "on" else "off"}, " +
            "managed=${if (managedMode) "on" else "off"}"

    fun read(context: Context): String {
        val state = snapshot(
            PxlRoutingPreset.fromKey(Settings.pxlnetRoutingPreset),
            PxlRoutingPreferences.isSmartRouting(context),
            Settings.perAppProxyEnabled,
            Settings.perAppProxyManagedMode,
        )
        val events = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(EVENTS, null).orEmpty()
        return buildString {
            appendLine("Routing state (categories only):")
            appendLine(state)
            if (events.isNotBlank()) {
                appendLine("Recent preset changes:")
                append(events)
            }
        }
    }
}
