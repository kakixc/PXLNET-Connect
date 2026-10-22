package io.nekohasekai.sfa.utils

object PxlDiagnosticRedactor {
    private val logTimestamp = Regex("(?m)^(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}) \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?")
    private val timestampPlaceholder = Regex("\\u0002(\\d+)\\u0003")
    private val shareLink = Regex(
        "(?i)\\b(vless|hysteria2|hy2|trojan|ss|vmess)://[^\\s]+",
    )
    private val subscriptionUrl = Regex(
        "(?i)https?://[^\\s/]+(?::\\d+)?/sub/[^\\s/?#]+",
    )
    private val authorization = Regex(
        "(?i)\\b(authorization|bearer|access[_-]?token|refresh[_-]?token|token|api[_-]?key|private[_-]?key|password|passwd|secret)" +
            "(\\s*[:=]\\s*|\\s+)[^\\s,;]+",
    )
    private val uuid = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b",
    )
    private val ipv4 = Regex(
        "(?<![0-9])(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})(?:\\.(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})){3}(?::[0-9]{1,5})?(?![0-9])",
    )
    private val ipv6 = Regex(
        "(?i)(?<![0-9a-f:])(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f:])",
    )
    private val urlHost = Regex("(?i)(https?://)([^/\\s:]+)(?::[0-9]{1,5})?")
    private val hostName = Regex(
        "(?i)(?<![@\\w.-])(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::[0-9]{1,5})?(?![\\w.-])",
    )
    private val longCredential = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])")

    fun redact(text: String): String {
        // The IPv6 pattern can otherwise mistake the clock portion of a
        // logcat/probe timestamp for an address. Preserve only known line prefixes.
        val timestamps = mutableListOf<String>()
        val preserved = logTimestamp.replace(text) { match ->
            timestamps += match.value
            "\u0002${timestamps.lastIndex}\u0003"
        }
        var result = shareLink.replace(preserved) { "${it.groupValues[1]}://[REDACTED]" }
        result = subscriptionUrl.replace(result, "https://[HOST]/sub/[REDACTED]")
        result = authorization.replace(result) { "${it.groupValues[1]}=[REDACTED]" }
        result = uuid.replace(result, "[UUID]")
        result = ipv4.replace(result, "[IP]")
        result = ipv6.replace(result, "[IPV6]")
        result = urlHost.replace(result) { "${it.groupValues[1]}[HOST]" }
        result = hostName.replace(result, "[HOST]")
        result = longCredential.replace(result, "[TOKEN]")
        return timestampPlaceholder.replace(result) { match ->
            timestamps.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: "[REDACTED]"
        }
    }
}
