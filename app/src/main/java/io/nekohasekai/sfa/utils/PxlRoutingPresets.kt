package io.nekohasekai.sfa.utils

import android.content.Context
import io.nekohasekai.sfa.database.Settings

enum class PxlRoutingPreset(val key: String) {
    Full("full"),
    Smart("smart"),
    Games("games"),
    Video("video"),
    Banks("banks"),
    SelectedApps("selected"),
    ;

    companion object {
        fun fromKey(value: String): PxlRoutingPreset = entries.firstOrNull { it.key == value } ?: Smart
    }
}

data class PxlRoutingPresetState(
    val preset: PxlRoutingPreset,
    val smartRoutingEnabled: Boolean,
    val perAppProxyEnabled: Boolean,
    val perAppProxyManagedMode: Boolean,
    val perAppProxyMode: Int,
    val perAppProxyManagedModeType: Int,
    val perAppProxyManagedList: Set<String>,
)

object PxlRoutingPresets {
    private val gamePackages = setOf(
        "com.activision.callofduty.shooter",
        "com.dts.freefireth",
        "com.epicgames.fortnite",
        "com.mojang.minecraftpe",
        "com.mobile.legends",
        "com.pubg.imobile",
        "com.roblox.client",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.tencent.ig",
        "com.valvesoftware.android.steam.community",
    )
    private val videoPackages = setOf(
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.vk.vkvideo",
        "ru.kinopoisk",
        "tv.twitch.android.app",
    )
    private val bankPackages = setOf(
        "com.idamob.tinkoff.android",
        "ru.alfabank.mobile.android",
        "ru.gazprombank.android.mobilebank.app",
        "ru.raiffeisennews",
        "ru.rosbank.android",
        "ru.sberbankmobile",
        "ru.vtb24.mobilebanking.android",
        "ru.yoo.money",
    )

    fun matchedPackages(context: Context, preset: PxlRoutingPreset): Set<String> {
        val candidates = when (preset) {
            PxlRoutingPreset.Games -> gamePackages
            PxlRoutingPreset.Video -> videoPackages
            PxlRoutingPreset.Banks -> bankPackages
            else -> return emptySet()
        }
        val installed = runCatching {
            context.packageManager.getInstalledApplications(0).mapTo(mutableSetOf()) { it.packageName }
        }.getOrDefault(emptySet())
        return candidates.intersect(installed)
    }

    fun snapshot(context: Context) = PxlRoutingPresetState(
        preset = PxlRoutingPreset.fromKey(Settings.pxlnetRoutingPreset),
        smartRoutingEnabled = PxlRoutingPreferences.isSmartRouting(context),
        perAppProxyEnabled = Settings.perAppProxyEnabled,
        perAppProxyManagedMode = Settings.perAppProxyManagedMode,
        perAppProxyMode = Settings.perAppProxyMode,
        perAppProxyManagedModeType = Settings.perAppProxyManagedModeType,
        perAppProxyManagedList = Settings.perAppProxyManagedList,
    )

    fun stateFor(
        current: PxlRoutingPresetState,
        preset: PxlRoutingPreset,
        packages: Set<String> = emptySet(),
    ): PxlRoutingPresetState = when (preset) {
        PxlRoutingPreset.Full, PxlRoutingPreset.Smart -> current.copy(
            preset = preset,
            smartRoutingEnabled = preset == PxlRoutingPreset.Smart,
            perAppProxyEnabled = false,
            perAppProxyManagedMode = false,
        )
        PxlRoutingPreset.SelectedApps -> current.copy(
            preset = preset,
            smartRoutingEnabled = false,
            perAppProxyEnabled = true,
            perAppProxyManagedMode = false,
            perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE,
        )
        PxlRoutingPreset.Games, PxlRoutingPreset.Video -> current.copy(
            preset = preset,
            smartRoutingEnabled = false,
            perAppProxyEnabled = packages.isNotEmpty(),
            perAppProxyManagedMode = true,
            perAppProxyManagedModeType = Settings.PER_APP_PROXY_INCLUDE,
            perAppProxyManagedList = packages,
        )
        PxlRoutingPreset.Banks -> current.copy(
            preset = preset,
            smartRoutingEnabled = false,
            perAppProxyEnabled = packages.isNotEmpty(),
            perAppProxyManagedMode = true,
            perAppProxyManagedModeType = Settings.PER_APP_PROXY_EXCLUDE,
            perAppProxyManagedList = packages,
        )
    }

    fun apply(context: Context, preset: PxlRoutingPreset, packages: Set<String> = matchedPackages(context, preset)) {
        restore(context, stateFor(snapshot(context), preset, packages))
    }

    fun restore(context: Context, state: PxlRoutingPresetState) {
        Settings.pxlnetRoutingPreset = state.preset.key
        Settings.perAppProxyEnabled = state.perAppProxyEnabled
        Settings.perAppProxyManagedMode = state.perAppProxyManagedMode
        Settings.perAppProxyMode = state.perAppProxyMode
        Settings.perAppProxyManagedModeType = state.perAppProxyManagedModeType
        Settings.perAppProxyManagedList = state.perAppProxyManagedList
        PxlRoutingPreferences.setSmartRouting(context, state.smartRoutingEnabled)
    }

    fun copyPresetToManualSelection() {
        if (!Settings.perAppProxyManagedMode) return
        Settings.perAppProxyList = Settings.perAppProxyManagedList
        Settings.perAppProxyMode = Settings.perAppProxyManagedModeType
        Settings.perAppProxyManagedMode = false
        Settings.pxlnetRoutingPreset = PxlRoutingPreset.SelectedApps.key
    }
}
