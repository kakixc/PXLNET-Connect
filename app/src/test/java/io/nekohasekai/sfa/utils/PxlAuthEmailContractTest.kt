package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.compose.screen.dashboard.clipboardCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PxlAuthEmailContractTest {
    @Test
    fun emailChallengeUsesJsonAndVerifiedBearerLoadsAccountAndSubscription() = withFakeApi { client, requests ->
        val email = "test@example.invalid"
        val challenge = client.startEmail(email)
        assertEquals("otp-test", challenge.id)
        assertEquals(1_800_000_600L, challenge.expiresAt)
        val start = requests.takeRequest()
        assertEquals("/api/v1/auth/email/start", start.path)
        assertEquals("POST", start.method)
        assertNull(start.bearer)
        assertEquals(email, start.field("email"))

        val token = client.verifyEmail(email, challenge.id, "012345")
        val verify = requests.takeRequest()
        assertEquals("/api/v1/auth/email/verify", verify.path)
        assertNull(verify.bearer)
        assertEquals(email, verify.field("email"))
        assertEquals("otp-test", verify.field("challenge_id"))
        assertEquals("012345", verify.field("code"))

        val account = client.account(token)
        assertTrue(account.subscriptionActive)
        assertTrue(account.hasTelegramIdentity)
        assertNull(account.verifiedEmail)
        assertEquals("Bearer fake-session", requests.takeRequest().bearer)
        assertEquals("https://example.invalid/test-subscription", client.subscriptionUrl(token))
        assertEquals("Bearer fake-session", requests.takeRequest().bearer)
        client.logout(token)
        assertEquals("/api/v1/auth/logout", requests.takeRequest().path)
    }

    @Test
    fun mapsRateLimitsRejectedCodesDeliveryFailuresAndUnlinkedSubscription() = withFakeApi { client, requests ->
        val rateLimit = assertThrows(PxlAuthClient.ApiError::class.java) {
            client.startEmail("limited@example.invalid")
        }
        assertEquals(429, rateLimit.status)
        assertEquals(73L, rateLimit.retryAfterSeconds)
        val rejected = assertThrows(PxlAuthClient.ApiError::class.java) {
            client.verifyEmail("test@example.invalid", "otp-test", "999999")
        }
        assertEquals("invalid_or_expired_code", rejected.code)
        val unavailable = assertThrows(PxlAuthClient.ApiError::class.java) {
            client.startEmail("offline@example.invalid")
        }
        assertEquals("email_delivery_unavailable", unavailable.code)

        val account = client.account("guest-session")
        assertFalse(account.subscriptionActive)
        assertFalse(account.hasTelegramIdentity)
        assertEquals(false, account.hasSubscription)
        val unlinked = assertThrows(PxlAuthClient.ApiError::class.java) {
            client.subscriptionUrl("guest-session")
        }
        assertEquals("subscription_not_linked", unlinked.code)
        assertEquals(5, requests.size)
    }

    @Test
    fun emailVerificationRejectsBearerWithoutPxlIdentity() = withFakeApi { client, _ ->
        assertThrows(IllegalStateException::class.java) {
            client.verifyEmail("test@example.invalid", "otp-test", "111111")
        }
    }

    @Test
    fun linksTelegramWithEmailBearerAndReportsConflicts() = withFakeApi { client, requests ->
        assertEquals("fake-session", client.linkTelegram("fake-session", "test-telegram-code") {
            error("Linking must preserve the existing token")
        })
        val link = requests.takeRequest()
        assertEquals("/api/v1/identities/telegram/link/confirm", link.path)
        assertEquals("POST", link.method)
        assertEquals("Bearer fake-session", link.bearer)
        assertEquals("test-telegram-code", link.field("code"))
        val conflict = assertThrows(PxlAuthClient.ApiError::class.java) {
            client.linkTelegram("fake-session", "conflicting-code") {
                error("A conflict must preserve the existing token")
            }
        }
        assertEquals(409, conflict.status)
        assertEquals("account_conflict", conflict.code)
        assertEquals(1, requests.size)
    }

    @Test
    fun mergePersistsNewBearerBeforeRefreshingAccountAndSubscription() = withFakeApi { client, requests ->
        var saved = "fake-session"
        val token = client.linkTelegram(saved, "merge-telegram-code") { saved = it }
        assertEquals("replace-test-session", saved)
        assertEquals(saved, token)
        assertEquals("Bearer fake-session", requests.takeRequest().bearer)
        client.account(token)
        client.subscriptionUrl(token)
        assertEquals("Bearer replace-test-session", requests.takeRequest().bearer)
        assertEquals("Bearer replace-test-session", requests.takeRequest().bearer)
    }

    @Test
    fun alreadyLinkedKeepsOriginalBearerAndMalformedMergeDoesNotReplaceIt() = withFakeApi { client, _ ->
        fun unexpectedReplacement(@Suppress("UNUSED_PARAMETER") token: String): Nothing =
            error("Unexpected token rotation")
        assertEquals("fake-session", client.linkTelegram("fake-session", "already-linked", ::unexpectedReplacement))
        assertThrows(IllegalArgumentException::class.java) {
            client.linkTelegram("fake-session", "malformed-merge", ::unexpectedReplacement)
        }
    }

    @Test
    fun pasteAcceptsOneSixDigitCodeAndKeepsLeadingZero() {
        assertEquals("012345", clipboardCode("Код: 012345."))
        assertNull(clipboardCode("7012345"))
        assertNull(clipboardCode("012345 или 123456"))
    }

    private data class Request(val path: String, val method: String, val bearer: String?, val body: String) {
        fun field(name: String) = Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content
    }

    private fun LinkedBlockingQueue<Request>.takeRequest(): Request = checkNotNull(poll(2, TimeUnit.SECONDS))

    private fun withFakeApi(block: (PxlAuthClient, LinkedBlockingQueue<Request>) -> Unit) {
        val requests = LinkedBlockingQueue<Request>()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket -> handleRequest(socket, requests) }
                }
            } catch (closed: SocketException) {
                if (!server.isClosed) throw closed
            }
        }
        serverThread.start()
        val endpoint = "http://127.0.0.1:${server.localPort}"
        try {
            block(PxlAuthClient(endpoint, endpoint, "PXLNET-Test"), requests)
        } finally {
            server.close()
            serverThread.join(1000)
        }
    }

    private fun handleRequest(socket: Socket, requests: LinkedBlockingQueue<Request>) {
        socket.soTimeout = 2000
        val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
        val requestLine = checkNotNull(input.readLine()).split(' ')
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val length = headers["content-length"]?.toInt() ?: 0
        val body = CharArray(length)
        var count = 0
        while (count < length) {
            val received = input.read(body, count, length - count)
            check(received > 0)
            count += received
        }
        val request = Request(requestLine[1], requestLine[0], headers["authorization"], String(body))
        requests.add(request)
        when (request.path) {
            "/api/v1/auth/email/start" -> when (request.field("email")) {
                "limited@example.invalid" -> {
                    socket.respond(429, """{"error":"too_many_requests"}""", "Retry-After: 73\r\n")
                }
                "offline@example.invalid" -> socket.respond(503, """{"error":"email_delivery_unavailable"}""")
                else -> socket.respond(202, """{"status":"code_sent","challenge_id":"otp-test","expires_at":1800000600}""")
            }
            "/api/v1/auth/email/verify" -> when (request.field("code")) {
                "012345" -> socket.respond(200, """{"status":"authenticated","pxl_user_id":"pxl_test","access_token":"fake-session","token_type":"Bearer"}""")
                "111111" -> socket.respond(200, """{"status":"authenticated","access_token":"fake-session","token_type":"Bearer"}""")
                else -> socket.respond(400, """{"error":"invalid_or_expired_code"}""")
            }
            "/api/v1/me" -> if (request.bearer == "Bearer guest-session") {
                socket.respond(200, """{"telegram_id":null,"has_subscription":false,"subscription_active":false}""")
            } else {
                socket.respond(200, """{"telegram_id":123,"has_subscription":true,"subscription_active":true}""")
            }
            "/api/v1/subscription" -> if (request.bearer == "Bearer guest-session") {
                socket.respond(404, """{"error":"subscription_not_linked"}""")
            } else {
                socket.respond(200, """{"url":"https://example.invalid/test-subscription"}""")
            }
            "/api/v1/auth/logout" -> socket.respond(204)
            "/api/v1/identities/telegram/link/confirm" -> when (request.field("code")) {
                "test-telegram-code" -> socket.respond(200, """{"status":"linked"}""")
                "already-linked" -> socket.respond(200, """{"status":"already_linked"}""")
                "merge-telegram-code" -> socket.respond(
                    200, """{"status":"merged","pxl_user_id":"pxl_existing","access_token":"replace-test-session","token_type":"Bearer","expires_at":1800000600}""",
                )
                "malformed-merge" -> socket.respond(200, """{"status":"merged","pxl_user_id":"pxl_existing","token_type":"Bearer"}""")
                else -> socket.respond(409, """{"error":"account_conflict"}""")
            }
            else -> socket.respond(404)
        }
    }

    private fun Socket.respond(status: Int, body: String? = null, extraHeaders: String = "") {
        val bytes = body.orEmpty().toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status Test\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n${extraHeaders}\r\n"
        getOutputStream().apply {
            write(headers.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }
}
