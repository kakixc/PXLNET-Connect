package io.nekohasekai.sfa.utils

import android.content.Context

/** Android 5 does not package the isolated Xray runtime. */
internal object PxlXhttpServerProbe {
    suspend fun measure(context: Context, profileId: Long, outboundTag: String): Int? = null
}
