package io.nekohasekai.sfa.xray

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator-only pre-connect gate; host fixture and credentials are synthetic. */
@RunWith(AndroidJUnit4::class)
class PxlXrayProbeServiceTest {
    @Test fun missingVpnConsentFailsClosed() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("Local REALITY fixture is absent", canReachFixture())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("This test requires VPN consent to be absent", VpnService.prepare(context) != null)
        val connected = CountDownLatch(1)
        var remote: IXrayProbe? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = IXrayProbe.Stub.asInterface(binder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { remote = null }
        }
        assertTrue(context.bindService(Intent(context, PxlXrayProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            assertEquals(-1, requireNotNull(remote).measureXhttp(config, "synthetic-xhttp", 5000))
        } finally {
            context.unbindService(connection)
        }
    }

    @Test fun preparedAppMeasuresRealityEndpointWithoutStartingTun() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assumeTrue("Local REALITY fixture is absent", canReachFixture())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull("Grant VPN consent only on the emulator", VpnService.prepare(context))

        val connected = CountDownLatch(1)
        var remote: IXrayProbe? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = IXrayProbe.Stub.asInterface(binder)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                remote = null
            }
        }
        assertTrue(context.bindService(Intent(context, PxlXrayProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val probe = requireNotNull(remote)
            val measured = probe.measureXhttp(config, "synthetic-xhttp", 5000)
            assertTrue("Expected protected XHTTP endpoint response before VPN start; got $measured", measured > 0)
            val wrongPath = config.replace("/pxlnet-local-test", "/pxlnet-invalid-probe")
            assertEquals(-1, probe.measureXhttp(wrongPath, "synthetic-xhttp", 5000))
        } finally {
            context.unbindService(connection)
        }
    }

    private fun canReachFixture(): Boolean = runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress("10.0.2.2", 19180), 500) }
        true
    }.getOrDefault(false)

    private val config = """{
      "outbounds": [{
        "tag": "synthetic-xhttp",
        "protocol": "vless",
        "settings": {"vnext": [{"address": "10.0.2.2", "port": 19180,
          "users": [{"id": "11111111-1111-4111-8111-111111111111", "encryption": "none"}]}]},
        "streamSettings": {"network": "xhttp", "security": "reality",
          "realitySettings": {"serverName": "localhost", "fingerprint": "chrome",
            "password": "5b5J3-FtVeyXc5vrQmI-6YTK8hoKRkRsJRD0s8YAlng", "shortId": ""},
          "xhttpSettings": {"path": "/pxlnet-local-test", "mode": "stream-one"}}
      }]
    }""".trimIndent()
}
