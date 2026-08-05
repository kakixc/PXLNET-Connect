package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class PxlAuthClient(
    private val baseUrl: String = BuildConfig.PXL_API_BASE_URL.trimEnd('/'),
) {
    data class LoginSession(
        val id: String,
        val secret: String,
        val botUrl: String,
        val expiresAt: Long,
        val pollAfterSeconds: Long,
    )

    data class LoginResult(
        val pending: Boolean,
        val accessToken: String? = null,
    )

    data class Account(
        val displayName: String,
        val username: String,
        val subscriptionActive: Boolean,
        val subscriptionExpiresAt: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun health(): Boolean {
        val root = request("GET", "/api/v1/health").jsonObject
        return root["status"]?.jsonPrimitive?.content == "ok"
    }

    fun createLoginSession(): LoginSession {
        val root = request("POST", "/api/v1/auth/session").jsonObject
        return LoginSession(
            id = root.getValue("session_id").jsonPrimitive.content,
            secret = root.getValue("session_secret").jsonPrimitive.content,
            botUrl = root.getValue("bot_url").jsonPrimitive.content,
            expiresAt = root.getValue("expires_at").jsonPrimitive.content.toLong(),
            pollAfterSeconds = root["poll_after_seconds"]?.jsonPrimitive?.content?.toLongOrNull() ?: 2,
        )
    }

    fun pollLoginSession(session: LoginSession): LoginResult {
        val root = request(
            "GET",
            "/api/v1/auth/session/${session.id}",
            session.secret,
            acceptedStatuses = setOf(200, 202),
        ).jsonObject
        return if (root["status"]?.jsonPrimitive?.content == "approved") {
            LoginResult(pending = false, accessToken = root.getValue("access_token").jsonPrimitive.content)
        } else {
            LoginResult(pending = true)
        }
    }

    fun account(accessToken: String): Account {
        val root = request("GET", "/api/v1/me", accessToken).jsonObject
        return Account(
            displayName = root["full_name"]?.jsonPrimitive?.content.orEmpty(),
            username = root["username"]?.jsonPrimitive?.content.orEmpty(),
            subscriptionActive = root["subscription_active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            subscriptionExpiresAt = root["subscription_expires_at"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun subscriptionUrl(accessToken: String): String {
        val root = request("GET", "/api/v1/subscription", accessToken).jsonObject
        return root.getValue("url").jsonPrimitive.content
    }

    fun logout(accessToken: String) {
        request("POST", "/api/v1/auth/logout", accessToken, acceptedStatuses = setOf(204))
    }

    private fun request(
        method: String,
        path: String,
        bearerToken: String? = null,
        acceptedStatuses: Set<Int> = setOf(200, 201),
    ) = (URL(baseUrl + path).openConnection() as HttpURLConnection).run {
        requestMethod = method
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", HTTPClient.userAgent)
        bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        if (method == "POST") {
            doOutput = true
            setFixedLengthStreamingMode(0)
        }
        try {
            val status = responseCode
            val body = (if (status in acceptedStatuses) inputStream else errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in acceptedStatuses) {
                throw IllegalStateException("PXLNET API: HTTP $status")
            }
            if (body.isBlank()) json.parseToJsonElement("{}") else json.parseToJsonElement(body)
        } finally {
            disconnect()
        }
    }
}
