package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.util.Locale

class HTTPClient : Closeable {
    data class Result(
        val content: String,
        val subscriptionUserInfo: String?,
    )

    companion object {
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
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setURL(url)
        val response = request.execute()
        return Result(
            content = response.content.unwrap,
            subscriptionUserInfo = response.getHeader("subscription-userinfo").takeIf(String::isNotBlank),
        )
    }

    override fun close() {
        client.close()
    }
}
