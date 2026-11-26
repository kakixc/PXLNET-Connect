package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class PxlAuthClient(
    private val baseUrl: String = BuildConfig.PXL_API_BASE_URL.trimEnd('/'),
    private val fallbackUrl: String = BuildConfig.PXL_API_FALLBACK_URL.trimEnd('/'),
    private val userAgent: String = HTTPClient.userAgent,
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
        val hasSubscription: Boolean?,
        val verifiedEmail: String?,
        val hasTelegramIdentity: Boolean,
    )

    data class EmailChallenge(val id: String, val expiresAt: Long)

    class ApiError(
        val status: Int,
        val code: String?,
        val retryAfterSeconds: Long?,
    ) : IllegalStateException("PXLNET API: HTTP $status")

    fun startEmail(email: String): EmailChallenge {
        val root = emailRequest("start", setOf(202), buildJsonObject { put("email", email) })
        check(root["status"]?.jsonPrimitive?.content == "code_sent")
        val id = requireNotNull(root["challenge_id"]?.jsonPrimitive?.contentOrNull)
        check(id.isNotBlank())
        return EmailChallenge(id, root.getValue("expires_at").jsonPrimitive.content.toLong())
    }

    fun verifyEmail(email: String, challengeId: String, code: String): String {
        val root = emailRequest("verify", setOf(200), buildJsonObject {
            put("email", email)
            put("challenge_id", challengeId)
            put("code", code)
        })
        check(root["status"]?.jsonPrimitive?.content == "authenticated")
        check(!root["pxl_user_id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank())
        return requireNotNull(root["access_token"]?.jsonPrimitive?.contentOrNull)
            .also { check(it.isNotBlank()) }
    }

    // An ambiguous network failure may have already issued a code or consumed it.
    private fun emailRequest(action: String, statuses: Set<Int>, body: JsonObject) =
        requestAt(baseUrl, "POST", "/api/v1/auth/email/$action", null, statuses, body).jsonObject

    fun linkTelegram(accessToken: String, code: String, persistReplacement: (String) -> Unit): String {
        val result = requestAt(
            baseUrl, "POST", "/api/v1/identities/telegram/link/confirm", accessToken,
            setOf(200), buildJsonObject { put("code", code) },
        ).jsonObject
        return when (result["status"]?.jsonPrimitive?.contentOrNull) {
            "linked", "already_linked" -> accessToken
            "merged" -> {
                check(!result["pxl_user_id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank())
                check(result["token_type"]?.jsonPrimitive?.contentOrNull.equals("Bearer", ignoreCase = true))
                val replacement = requireNotNull(result["access_token"]?.jsonPrimitive?.contentOrNull)
                check(replacement.isNotBlank() && replacement != accessToken)
                persistReplacement(replacement)
                replacement
            }
            else -> error("Unexpected Telegram link response")
        }
    }

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
            displayName = root["full_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            username = root["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            subscriptionActive = root["subscription_active"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
            subscriptionExpiresAt = root["subscription_expires_at"]?.jsonPrimitive?.contentOrNull,
            hasSubscription = root["has_subscription"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            verifiedEmail = root["email"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { root["email_verified"]?.jsonPrimitive?.content == "true" },
            hasTelegramIdentity = root["telegram_id"]?.jsonPrimitive?.contentOrNull != null,
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
    ) = try {
        requestAt(baseUrl, method, path, bearerToken, acceptedStatuses)
    } catch (e: IOException) {
        if (baseUrl == fallbackUrl) throw e
        requestAt(fallbackUrl, method, path, bearerToken, acceptedStatuses)
    }

    private class ApiUnavailableException(status: Int) : IOException("PXLNET API: HTTP $status")

    private fun requestAt(
        endpoint: String,
        method: String,
        path: String,
        bearerToken: String?,
        acceptedStatuses: Set<Int>,
        payload: JsonObject? = null,
    ) = (URL(endpoint + path).openConnection() as HttpURLConnection).run {
        requestMethod = method
        instanceFollowRedirects = false
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", userAgent)
        bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (method == "POST") {
                val bytes = payload?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
                if (payload != null) {
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    outputStream.use { it.write(bytes) }
                }
            }
            val status = responseCode
            val body = (if (status in acceptedStatuses) inputStream else errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in acceptedStatuses) {
                if (payload == null && status in 500..599) throw ApiUnavailableException(status)
                val code = runCatching {
                    json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                val retryAfter = getHeaderField("Retry-After")?.trim()
                    ?.toLongOrNull()?.takeIf { it >= 0 }
                throw ApiError(status, code, retryAfter)
            }
            if (body.isBlank()) json.parseToJsonElement("{}") else json.parseToJsonElement(body)
        } finally {
            disconnect()
        }
    }
}
