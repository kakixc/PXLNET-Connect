package io.nekohasekai.sfa.utils

import java.net.URI
import java.util.Base64

internal enum class ProfileLinkKind { Subscription, Server }

internal fun profileLinkKind(value: String): ProfileLinkKind? {
    val link = value.trim()
    val uri = runCatching { URI(link.replace(" ", "%20")) }.getOrNull() ?: return null
    if (uri.host.isNullOrBlank()) return null
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> ProfileLinkKind.Subscription
        "vless", "hysteria2", "hy2" -> ProfileLinkKind.Server
        else -> null
    }
}

internal fun importedProfileName(
    customName: String,
    profileTitle: String?,
    linkName: String?,
    link: String,
    fallback: String,
): String = visibleProfileName(customName)
    ?: visibleProfileName(decodeProfileTitle(profileTitle))
    ?: visibleProfileName(linkName)
    ?: runCatching { visibleProfileName(URI(link.replace(" ", "%20")).fragment) }.getOrNull()
    ?: fallback

private fun decodeProfileTitle(header: String?): String? {
    val value = header?.trim()?.takeIf { it.length <= 2048 } ?: return null
    if (!value.startsWith("base64:", ignoreCase = true)) return value
    val encoded = value.substringAfter(':').trim()
    return runCatching { Base64.getDecoder().decode(encoded).decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        ?: runCatching { Base64.getUrlDecoder().decode(encoded).decodeToString(throwOnInvalidSequence = true) }.getOrNull()
}

private fun visibleProfileName(name: String?): String? =
    name?.filterNot(Char::isISOControl)?.trim()?.take(120)?.takeIf(String::isNotBlank)
