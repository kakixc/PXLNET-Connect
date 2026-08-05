package io.nekohasekai.sfa.utils

object PxlDiagnosticRedactor {
    private val shareLink = Regex(
        "(?i)\\b(vless|hysteria2|hy2|trojan|ss|vmess)://[^\\s]+",
    )
    private val subscriptionUrl = Regex(
        "(?i)https?://[^\\s/]+(?::\\d+)?/sub/[^\\s/?#]+",
    )
    private val authorization = Regex(
        "(?i)\\b(authorization|bearer|access[_-]?token|refresh[_-]?token|password|passwd|secret)" +
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
        var result = shareLink.replace(text) { "${it.groupValues[1]}://[REDACTED]" }
        result = subscriptionUrl.replace(result, "https://[HOST]/sub/[REDACTED]")
        result = authorization.replace(result) { "${it.groupValues[1]}=[REDACTED]" }
        result = uuid.replace(result, "[UUID]")
        result = ipv4.replace(result, "[IP]")
        result = ipv6.replace(result, "[IPV6]")
        result = urlHost.replace(result) { "${it.groupValues[1]}[HOST]" }
        result = hostName.replace(result, "[HOST]")
        return longCredential.replace(result, "[TOKEN]")
    }
}
