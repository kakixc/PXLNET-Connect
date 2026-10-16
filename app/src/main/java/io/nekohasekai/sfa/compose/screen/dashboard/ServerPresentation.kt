package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.compose.model.Group

/** Presentation only: the original outbound tag must remain unchanged for sing-box selectors. */
internal enum class ServerRegion { AUTO, GERMANY, FINLAND, OTHER }

internal fun serverRegion(tag: String): ServerRegion {
    val lower = tag.lowercase()
    return when {
        tag.equals("AUTO", ignoreCase = true) ||
            tag.equals("Fast", ignoreCase = true) ||
            tag.equals("Stable", ignoreCase = true) ||
            tag.equals("Anti-Block", ignoreCase = true) -> ServerRegion.AUTO
        "🇩🇪" in tag || "germany" in lower || "deutsch" in lower || "герман" in lower -> ServerRegion.GERMANY
        "🇫🇮" in tag || "finland" in lower || "finn" in lower || "финлянд" in lower -> ServerRegion.FINLAND
        else -> ServerRegion.OTHER
    }
}

internal fun cleanServerTag(tag: String): String =
    tag.replace(Regex("^[\\p{So}\\p{Cf}\\s]+"), "").trim()

/** Follow the live selector/urltest choice; a configured single-choice selector is deterministic. */
internal fun selectedVpnServerNode(
    groups: List<Group>,
    selectedTag: String,
    singleChoiceTargets: Map<String, String> = emptyMap(),
    extraNodeTags: Set<String> = emptySet(),
): String? {
    val nodeTags = groups.flatMap { it.items }
        .filter { it.type.equals("vless", true) || it.type.equals("hysteria2", true) || it.tag in extraNodeTags }
        .mapTo(mutableSetOf()) { it.tag }
    val visited = mutableSetOf<String>()
    var tag = selectedTag
    while (visited.add(tag)) {
        if (tag in nodeTags) return tag
        val liveChoice = groups.firstOrNull { it.tag == tag }?.selected?.takeIf(String::isNotBlank)
        tag = liveChoice ?: singleChoiceTargets[tag] ?: return null
    }
    return null
}

internal fun serverProtocol(tag: String, type: String = ""): String = when {
    serverRegion(tag) == ServerRegion.AUTO -> ""
    "vless" in type.lowercase() || "vless" in tag.lowercase() ->
        if ("reality" in type.lowercase() || "reality" in tag.lowercase()) "VLESS · Reality" else "VLESS"
    "hysteria2" in type.lowercase() || "hysteria2" in tag.lowercase() || type.equals("hy2", true) -> "Hysteria2"
    else -> type.takeIf(String::isNotBlank).orEmpty()
}

/** AUTO's pre-connect label is the best observed candidate, never a claimed selector choice. */
internal fun bestMeasuredServerDelay(manualTags: List<String>, measured: Map<String, Int?>): Int? =
    manualTags.mapNotNull { tag -> measured[tag]?.takeIf { it > 0 } }.minOrNull()

/** Opening the picker is read-only; only its explicit action starts a bulk probe. */
internal fun serverProbeTargets(
    pickerOpen: Boolean,
    measureAllRequested: Boolean,
    homeServerMode: Boolean,
    manualTags: List<String>,
    autoTags: List<String>,
    selectedTag: String?,
): List<String> = when {
    pickerOpen && measureAllRequested -> (manualTags + autoTags).distinct()
    pickerOpen -> emptyList()
    homeServerMode -> listOfNotNull(selectedTag)
    else -> emptyList()
}

internal fun autoBestMeasurementLoading(
    measureAllRequested: Boolean,
    vpnStopped: Boolean,
    bestDelay: Int?,
    anyManualPending: Boolean,
): Boolean = measureAllRequested && vpnStopped && bestDelay == null && anyManualPending
