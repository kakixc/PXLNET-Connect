package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.util.Locale

class HTTPClient : Closeable {
    data class SmartRevisions(
        val config: String,
        val health: String,
        val policy: String,
        val compatibility: String,
    )

    data class Result(
        val content: String,
        val subscriptionUserInfo: String?,
        val subscriptionStatus: String?,
        val subscriptionFormat: String?,
        val omittedXhttpCount: Int = 0,
        val xhttpLinks: List<String> = emptyList(),
        val smartRevisions: SmartRevisions? = null,
    ) {
        override fun toString(): String =
            "HTTPClient.Result(contentLength=${content.length}, subscriptionStatus=$subscriptionStatus, " +
                "subscriptionFormat=$subscriptionFormat, xhttpLinkCount=${xhttpLinks.size})"
    }

    companion object {
        internal fun smartFormatUrl(url: String, status: String?, format: String?): String? {
            if (!status.equals("active", ignoreCase = true) ||
                format?.lowercase() !in setOf("raw", "base64")
            ) return null
            val fragmentStart = url.indexOf('#').let { if (it < 0) url.length else it }
            val requestUrl = url.substring(0, fragmentStart)
            val queryStart = requestUrl.indexOf('?')
            if (queryStart < 0) return "$requestUrl?format=singbox" + url.substring(fragmentStart)
            val parameters = requestUrl.substring(queryStart + 1).split('&').toMutableList()
            val formatIndices = parameters.indices.filter { index ->
                parameters[index].substringBefore('=').equals("format", ignoreCase = true)
            }
            if (formatIndices.size > 1) return null
            if (formatIndices.isEmpty()) parameters.add("format=singbox")
            else parameters[formatIndices.single()] = "format=singbox"
            return requestUrl.substring(0, queryStart + 1) + parameters.joinToString("&") + url.substring(fragmentStart)
        }

        internal fun rawFormatUrl(url: String, status: String?, format: String?): String? {
            if (!status.equals("active", ignoreCase = true) || !format.equals("singbox", ignoreCase = true)) return null
            val fragmentStart = url.indexOf('#').let { if (it < 0) url.length else it }
            val requestUrl = url.substring(0, fragmentStart)
            val queryStart = requestUrl.indexOf('?')
            if (queryStart < 0) return "$requestUrl?format=raw" + url.substring(fragmentStart)
            val parameters = requestUrl.substring(queryStart + 1).split('&').toMutableList()
            val formatIndices = parameters.indices.filter { index ->
                parameters[index].substringBefore('=').equals("format", ignoreCase = true)
            }
            if (formatIndices.size > 1) return null
            if (formatIndices.isEmpty()) parameters.add("format=raw")
            else parameters[formatIndices.single()] = "format=raw"
            return requestUrl.substring(0, queryStart + 1) + parameters.joinToString("&") + url.substring(fragmentStart)
        }

        internal fun isStructuredSmartResponse(response: Result?): Boolean =
            response?.subscriptionStatus.equals("active", ignoreCase = true) &&
                response?.subscriptionFormat.equals("singbox", ignoreCase = true)

        internal fun canPairSmartResponses(raw: Result, structured: Result?): Boolean =
            isStructuredSmartResponse(structured) && raw.subscriptionStatus.equals("active", true) &&
                raw.smartRevisions != null &&
                raw.smartRevisions == structured?.smartRevisions

        internal fun withPairedXhttpLinks(structured: Result, raw: Result): Result {
            if (!raw.subscriptionFormat.equals("raw", true) &&
                !raw.subscriptionFormat.equals("base64", true)
            ) return structured
            if (!canPairSmartResponses(raw, structured)) return structured
            val links = PxlSubscriptionConverter.xhttpLinks(raw.content)
            return structured.copy(omittedXhttpCount = links.size, xhttpLinks = links)
        }

        internal fun withLegacyXhttpLinks(response: Result): Result {
            // Smart raw responses with missing or mismatched revisions must still fail closed.
            if (response.subscriptionStatus != null || response.subscriptionFormat != null ||
                response.smartRevisions != null
            ) return response
            val links = PxlSubscriptionConverter.xhttpLinks(response.content)
            return response.copy(omittedXhttpCount = links.size, xhttpLinks = links)
        }

        val userAgent by lazy {
            var userAgent = "PXLNET-Connect/"
            userAgent += BuildConfig.VERSION_NAME
            userAgent += " ("
            userAgent += BuildConfig.VERSION_CODE
            userAgent += "; sing-box "
            userAgent += Libbox.version()
            userAgent += "; language "
            userAgent += Locale.getDefault().toLanguageTag().replace("-", "_")
            userAgent += ")"
            userAgent
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        return get(url).content
    }

    fun get(url: String): Result {
        val initial = request(url)
        if (isStructuredSmartResponse(initial)) {
            val rawUrl = rawFormatUrl(url, initial.subscriptionStatus, initial.subscriptionFormat)
                ?: return initial
            val raw = runCatching { request(rawUrl) }.getOrNull() ?: return initial
            return withPairedXhttpLinks(initial, raw)
        }
        val preferredUrl = smartFormatUrl(url, initial.subscriptionStatus, initial.subscriptionFormat)
            ?: return withLegacyXhttpLinks(initial)
        val structured = runCatching { request(preferredUrl) }.getOrNull()
        return if (canPairSmartResponses(initial, structured)) {
            withPairedXhttpLinks(structured!!, initial)
        } else initial
    }

    private fun request(url: String): Result {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setURL(url)
        val response = request.execute()
        val revisions = listOf("config", "health", "policy", "compatibility")
            .map { response.getHeader("x-pxlnet-$it-revision") }
            .takeIf { values -> values.all(String::isNotBlank) }
        return Result(
            content = response.content.unwrap,
            subscriptionUserInfo = response.getHeader("subscription-userinfo").takeIf(String::isNotBlank),
            subscriptionStatus = response.getHeader("x-pxlnet-subscription-status").takeIf(String::isNotBlank),
            subscriptionFormat = response.getHeader("x-pxlnet-format").takeIf(String::isNotBlank),
            smartRevisions = revisions?.let { SmartRevisions(it[0], it[1], it[2], it[3]) },
        )
    }

    override fun close() {
        client.close()
    }
}
