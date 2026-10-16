package io.nekohasekai.sfa.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.xray.IXrayProbe
import io.nekohasekai.sfa.xray.PxlXrayProbeService
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Measures only the XHTTP endpoint named by the current, hash-verified profile sidecar. */
internal object PxlXhttpServerProbe {
    private const val MEASURE_TIMEOUT_MS = 5_000
    private const val BIND_TIMEOUT_MS = 2_000L

    suspend fun measure(context: Context, profileId: Long, outboundTag: String): Int? {
        if (profileId <= 0 || outboundTag.isBlank()) return null
        val config = withContext(Dispatchers.IO) {
            val profile = ProfileManager.get(profileId) ?: return@withContext null
            val configFile = File(profile.typed.path)
            val snapshot = PxlXhttpSidecar.read(configFile) ?: return@withContext null
            configForTag(snapshot.links, outboundTag)
        } ?: return null

        val appContext = context.applicationContext
        val connected = CompletableDeferred<IXrayProbe?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                connected.complete(IXrayProbe.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                connected.complete(null)
            }

            override fun onNullBinding(name: ComponentName) {
                connected.complete(null)
            }
        }
        val bound = runCatching {
            appContext.bindService(Intent(appContext, PxlXrayProbeService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) return null
        return try {
            val remote = withTimeoutOrNull(BIND_TIMEOUT_MS) { connected.await() } ?: return null
            val result = withContext(Dispatchers.IO) {
                runCatching { remote.measureXhttp(config, outboundTag, MEASURE_TIMEOUT_MS) }.getOrDefault(-1)
            }
            result.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        } finally {
            runCatching { appContext.unbindService(connection) }
        }
    }

    internal fun configForTag(links: List<String>, outboundTag: String): String? {
        val outbound = links.asSequence().mapNotNull { link ->
            runCatching { PxlXhttpConfig.outbound(link) }.getOrNull()
        }.firstOrNull { it["tag"]?.jsonPrimitive?.content == outboundTag } ?: return null
        return Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("outbounds", buildJsonArray { add(outbound) })
        })
    }
}
