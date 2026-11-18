package io.nekohasekai.sfa.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/** Checks the configured resolver with an actual DNS A query, not an HTTP reachability check. */
object PxlDnsProbe {
    private const val TIMEOUT_MS = 3_500

    fun check(endpoint: PxlDnsEndpoint): Long {
        val id = Random.nextInt(0, 65536)
        val request = query(id)
        val started = System.nanoTime()
        val response = when (endpoint.type) {
            "udp" -> udp(endpoint, request)
            "tls" -> tls(endpoint, request)
            "https" -> https(endpoint, request)
            else -> error("Unsupported DNS transport")
        }
        validateResponse(response, id)
        return ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
    }

    private fun query(id: Int): ByteArray = byteArrayOf(
        (id ushr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
        'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
        0, 1, 0, 1,
    )

    private fun udp(endpoint: PxlDnsEndpoint, request: ByteArray): ByteArray = DatagramSocket().use { socket ->
        socket.soTimeout = TIMEOUT_MS
        socket.connect(InetSocketAddress(endpoint.host, endpoint.port))
        socket.send(DatagramPacket(request, request.size))
        val bytes = ByteArray(4096)
        val packet = DatagramPacket(bytes, bytes.size)
        socket.receive(packet)
        bytes.copyOf(packet.length)
    }

    private fun tls(endpoint: PxlDnsEndpoint, request: ByteArray): ByteArray {
        return Socket().use { plain ->
            plain.connect(InetSocketAddress(endpoint.host, endpoint.port), TIMEOUT_MS)
            plain.soTimeout = TIMEOUT_MS
            (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, endpoint.host, endpoint.port, true).use { socket ->
                val tlsSocket = socket as SSLSocket
                tlsSocket.soTimeout = TIMEOUT_MS
                tlsSocket.sslParameters = tlsSocket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tlsSocket.startHandshake()
                DataOutputStream(tlsSocket.outputStream).apply {
                    writeShort(request.size)
                    write(request)
                    flush()
                }
                DataInputStream(tlsSocket.inputStream).let { input ->
                    val size = input.readUnsignedShort()
                    require(size in 12..4096) { "Invalid DNS response length" }
                    ByteArray(size).also { input.readFully(it) }
                }
            }
        }
    }

    private fun https(endpoint: PxlDnsEndpoint, request: ByteArray): ByteArray {
        val host = if (':' in endpoint.host) "[${endpoint.host}]" else endpoint.host
        val connection = URL("https://$host:${endpoint.port}${endpoint.path}").openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/dns-message")
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.doOutput = true
            connection.outputStream.use { it.write(request) }
            require(connection.responseCode == 200) { "DNS-over-HTTPS returned HTTP ${connection.responseCode}" }
            require(connection.contentType?.substringBefore(';')?.trim()?.equals("application/dns-message", true) == true) {
                "DNS-over-HTTPS returned an unexpected content type"
            }
            connection.inputStream.use { input ->
                val bytes = ByteArray(4096)
                var size = 0
                val expected = connection.contentLength.takeIf { it in 1..bytes.size } ?: bytes.size
                while (size < expected) {
                    val read = input.read(bytes, size, expected - size)
                    if (read < 0) break
                    size += read
                }
                require(size > 0) { "DNS-over-HTTPS returned no answer" }
                bytes.copyOf(size)
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun validateResponse(response: ByteArray, id: Int) {
        require(response.size > 33) { "DNS response is too short" }
        val responseId = ((response[0].toInt() and 0xff) shl 8) or (response[1].toInt() and 0xff)
        require(responseId == id) { "DNS response ID does not match" }
        val flags = ((response[2].toInt() and 0xff) shl 8) or (response[3].toInt() and 0xff)
        require(flags and 0x8000 != 0 && flags and 0x0200 == 0) { "Invalid or truncated DNS answer" }
        require(flags and 0x000f == 0) { "DNS server returned error ${flags and 0x000f}" }
        val answers = ((response[6].toInt() and 0xff) shl 8) or (response[7].toInt() and 0xff)
        require(answers > 0) { "DNS server returned no records" }
        val questions = ((response[4].toInt() and 0xff) shl 8) or (response[5].toInt() and 0xff)
        require(questions == 1) { "DNS response question does not match" }
    }
}
