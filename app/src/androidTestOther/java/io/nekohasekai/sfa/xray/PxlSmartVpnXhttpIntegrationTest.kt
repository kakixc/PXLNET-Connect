package io.nekohasekai.sfa.xray

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.nekohasekai.sfa.aidl.IService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.StartServiceCoordinator
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.PxlXhttpConfig
import io.nekohasekai.sfa.utils.PxlXhttpRuntimePreparer
import io.nekohasekai.sfa.utils.PxlXhttpSidecar
import io.nekohasekai.sfa.utils.PxlXhttpServerProbe
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only product VPN test. All credentials and servers are synthetic and local. */
@RunWith(AndroidJUnit4::class)
class PxlSmartVpnXhttpIntegrationTest {
    private val endpoint = URL("http://192.0.2.123:19082/?pxlnet-vpn-xhttp-test=1")
    private val link = "vless://11111111-1111-4111-8111-111111111111@10.0.2.2:19080" +
        "?type=xhttp&security=reality&pbk=5b5J3-FtVeyXc5vrQmI-6YTK8hoKRkRsJRD0s8YAlng" +
        "&sni=localhost&fp=chrome&path=%2Fpxlnet-local-test&mode=auto#Local-test"

    @Test
    fun selectedSmartXhttpCarriesTrafficThroughRealVpnTun() = productVpnCarriesXhttp(smart = true)

    @Test
    fun selectedLegacyXhttpCarriesTrafficThroughRealVpnTun() = productVpnCarriesXhttp(smart = false)

    @Test
    fun legacyXhttpOnlySubscriptionCarriesTrafficThroughRealVpnTun() =
        productVpnCarriesXhttp(smart = false, xhttpOnly = true)

    private fun productVpnCarriesXhttp(smart: Boolean, xhttpOnly: Boolean = false) {
        assumeTrue("Only Android Emulator may create the technical profile", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("Host REALITY/XHTTP fixture is absent", canConnect(19080) && canConnect(19082) && canConnect(19083))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Do not disturb existing emulator profiles", runBlocking { ProfileManager.list().isEmpty() })
        assertNull("Grant VPN consent only on the emulator before this test", VpnService.prepare(context))
        assertFalse("Direct route must not reach the test target", canFetch(endpoint, 1_000))

        val oldSelectedProfile = Settings.selectedProfile
        val oldServiceMode = Settings.serviceMode
        val configFile = File(context.filesDir, "pxlnet-local-xhttp-vpn-test.json")
        var profile: Profile? = null
        var bound = false
        var service: IService? = null
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IService.Stub.asInterface(binder)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        try {
            val ordinaryConfig = if (xhttpOnly) PxlSubscriptionConverter.convert(
                link,
                smartRouting = false,
            ).config else if (smart) PxlSubscriptionConverter.convert(
                """{"outbounds":[{"type":"hysteria2","tag":"fallback-test","server":"10.0.2.2","server_port":19999,"password":"local-test","tls":{"enabled":true,"insecure":true}},{"type":"selector","tag":"AUTO","outbounds":["fallback-test"],"default":"fallback-test"}],"route":{"final":"AUTO"}}""",
                smartRouting = false,
                subscriptionFormat = "singbox",
            ).config else PxlSubscriptionConverter.convert(
                "hysteria2://local-test@10.0.2.2:19999?insecure=1&sni=localhost#fallback-test",
                smartRouting = false,
            ).config
            // The test-only destination is reserved and must be sent through PXLNET,
            // not the product's usual private-address DIRECT exception.
            val root = Json.parseToJsonElement(ordinaryConfig).jsonObject
            val route = root.getValue("route").jsonObject
            val rules = route.getValue("rules").jsonArray.filterNot {
                it.jsonObject["ip_is_private"]?.jsonPrimitive?.content == "true"
            }
            configFile.writeText(Json.encodeToString(
                JsonObject.serializer(),
                JsonObject(root + ("route" to JsonObject(route + ("rules" to JsonArray(rules))))),
            ))
            Libbox.checkConfig(configFile.readText())
            val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
            assertTrue(PxlXhttpSidecar.write(
                configFile,
                listOf(link),
                if (smart) HTTPClient.SmartRevisions("local-config", "local-health", "local-policy", "local-compat") else null,
                preferredTag = tag,
            ))
            assertNotNull("Synthetic profile must prepare an XHTTP companion", PxlXhttpRuntimePreparer.prepare(configFile, 19090))
            profile = runBlocking {
                ProfileManager.create(Profile(name = "PXLNET local XHTTP VPN test", typed = TypedProfile().apply {
                    path = configFile.absolutePath
                }))
            }
            Settings.selectedProfile = requireNotNull(profile).id
            runBlocking { Settings.rebuildServiceMode() }
            assertEquals(ServiceMode.VPN, Settings.serviceMode)
            assertTrue("XHTTP server must be measurable before VPN start",
                (runBlocking { PxlXhttpServerProbe.measure(context, requireNotNull(profile).id, tag) } ?: 0) > 0)

            bound = context.bindService(Intent(context, VPNService::class.java).setAction(Action.SERVICE), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            assertNotNull(service)
            assertTrue(StartServiceCoordinator.start(StartServiceCoordinator.Source.HOME))
            assertTrue("Product VPN service did not publish Started", await(30_000) {
                service?.status == Status.Started.ordinal
            })
            assertEquals("The HTTP response must travel through VPN/TUN and XHTTP", 200, fetch(endpoint, 8_000))
            if (smart) {
                assertTrue("XHTTP endpoint must remain measurable during VPN",
                    (runBlocking { PxlXhttpServerProbe.measure(context, requireNotNull(profile).id, tag) } ?: 0) > 0)
                assertEquals("Probe must not replace the active VPN", Status.Started.ordinal, service?.status)
                assertEquals("VPN egress must still work after probing", 200, fetch(endpoint, 8_000))
            }
        } finally {
            BoxService.stop()
            if (bound) {
                await(10_000) { service?.status == Status.Stopped.ordinal }
                context.unbindService(connection)
            }
            context.stopService(Intent(context, VPNService::class.java))
            Settings.selectedProfile = oldSelectedProfile
            Settings.serviceMode = oldServiceMode
            profile?.let { runBlocking { ProfileManager.delete(it) } }
            PxlXhttpSidecar.remove(configFile)
            if (configFile.exists()) check(configFile.delete())
            check(runBlocking { ProfileManager.list().isEmpty() }) { "Temporary emulator profile was not removed" }
        }
    }

    private fun canFetch(url: URL, timeout: Int): Boolean = runCatching { fetch(url, timeout) == 200 }.getOrDefault(false)

    private fun fetch(url: URL, timeout: Int): Int {
        // A raw HTTP probe avoids changing the product's cleartext policy just for localhost QA.
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(url.host, url.port), timeout)
            socket.soTimeout = timeout
            socket.getOutputStream().write(
                "GET ${url.file} HTTP/1.1\r\nHost: ${url.host}\r\nConnection: close\r\n\r\n".toByteArray(),
            )
            BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                ?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: -1
        }
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("10.0.2.2", port), 3_000) }
        true
    }.onFailure { Log.w("PxlVpnXhttpTest", "Fixture port $port unavailable: ${it.javaClass.simpleName}: ${it.message}") }
        .getOrDefault(false)

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
