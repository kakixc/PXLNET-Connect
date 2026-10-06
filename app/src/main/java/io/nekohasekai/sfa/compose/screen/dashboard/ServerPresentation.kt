package io.nekohasekai.sfa.compose.screen.dashboard

/** Presentation only: the original outbound tag must remain unchanged for sing-box selectors. */
internal enum class ServerRegion { AUTO, GERMANY, FINLAND, OTHER }

internal fun serverRegion(tag: String): ServerRegion {
    val lower = tag.lowercase()
    return when {
        tag.equals("AUTO", ignoreCase = true) -> ServerRegion.AUTO
        "🇩🇪" in tag || "germany" in lower || "deutsch" in lower || "герман" in lower -> ServerRegion.GERMANY
        "🇫🇮" in tag || "finland" in lower || "finn" in lower || "финлянд" in lower -> ServerRegion.FINLAND
        else -> ServerRegion.OTHER
    }
}

internal fun cleanServerTag(tag: String): String =
    tag.replace(Regex("^[\\p{So}\\p{Cf}\\s]+"), "").trim()

internal fun serverProtocol(tag: String, type: String = ""): String = when {
    "vless" in type.lowercase() || "vless" in tag.lowercase() ->
        if ("reality" in tag.lowercase()) "VLESS · Reality" else "VLESS"
    "hysteria2" in type.lowercase() || "hysteria2" in tag.lowercase() || type.equals("hy2", true) -> "Hysteria2"
    else -> type.takeIf(String::isNotBlank).orEmpty()
}
