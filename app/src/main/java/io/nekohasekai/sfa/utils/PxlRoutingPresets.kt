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

    fun apply(context: Context, preset: PxlRoutingPreset, packages: Set<String> = matchedPackages(context, preset)) {
        Settings.pxlnetRoutingPreset = preset.key
        when (preset) {
            PxlRoutingPreset.Full, PxlRoutingPreset.Smart -> {
                Settings.perAppProxyEnabled = false
                Settings.perAppProxyManagedMode = false
            }
            PxlRoutingPreset.SelectedApps -> {
                Settings.perAppProxyEnabled = true
                Settings.perAppProxyManagedMode = false
                Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
            }
            PxlRoutingPreset.Games, PxlRoutingPreset.Video -> {
                Settings.perAppProxyEnabled = packages.isNotEmpty()
                Settings.perAppProxyManagedMode = true
                Settings.perAppProxyManagedModeType = Settings.PER_APP_PROXY_INCLUDE
                Settings.perAppProxyManagedList = packages
            }
            PxlRoutingPreset.Banks -> {
                Settings.perAppProxyEnabled = packages.isNotEmpty()
                Settings.perAppProxyManagedMode = true
                Settings.perAppProxyManagedModeType = Settings.PER_APP_PROXY_EXCLUDE
                Settings.perAppProxyManagedList = packages
            }
        }
        PxlRoutingPreferences.setSmartRouting(context, preset == PxlRoutingPreset.Smart)
    }

    fun copyPresetToManualSelection() {
        if (!Settings.perAppProxyManagedMode) return
        Settings.perAppProxyList = Settings.perAppProxyManagedList
        Settings.perAppProxyMode = Settings.perAppProxyManagedModeType
        Settings.perAppProxyManagedMode = false
        Settings.pxlnetRoutingPreset = PxlRoutingPreset.SelectedApps.key
    }
}
