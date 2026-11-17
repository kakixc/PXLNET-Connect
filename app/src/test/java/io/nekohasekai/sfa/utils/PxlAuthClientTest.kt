package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class PxlAuthClientTest {
    @Test fun retriesUnavailablePrimaryAndPreservesBearerToken() {
        val primary = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val fallback = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val auth = AtomicReference<String>()
        val first = Thread {
            primary.accept().use { socket ->
                socket.getInputStream().bufferedReader().readLinesUntilEmpty()
                socket.getOutputStream().write("HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        }
        val second = Thread {
            fallback.accept().use { socket ->
                val headers = socket.getInputStream().bufferedReader().readLinesUntilEmpty()
                auth.set(headers.firstOrNull { it.startsWith("Authorization:", true) }?.substringAfter(':')?.trim())
                val body = """{"full_name":"Test","username":"test","subscription_active":true}"""
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray(),
                )
            }
        }
        first.start()
        second.start()
        try {
            val account = PxlAuthClient(
                "http://127.0.0.1:${primary.localPort}",
                "http://127.0.0.1:${fallback.localPort}",
                "PXLNET-Test",
            ).account("test-token")
            assertEquals("Test", account.displayName)
            assertTrue(account.subscriptionActive)
            assertEquals("Bearer test-token", auth.get())
        } finally {
            primary.close()
            fallback.close()
            first.join(1000)
            second.join(1000)
        }
    }

    private fun java.io.BufferedReader.readLinesUntilEmpty(): List<String> = buildList {
        while (true) {
            val line = readLine() ?: break
            if (line.isEmpty()) break
            add(line)
        }
    }
}
