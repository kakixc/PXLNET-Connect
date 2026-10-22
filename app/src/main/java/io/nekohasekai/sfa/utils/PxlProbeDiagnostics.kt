package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records only a bounded failure category, never a server address or credential. */
object PxlProbeDiagnostics {
    private const val FILE_NAME = "pxlnet_probe_diagnostics.txt"

    fun category(message: String): String {
        val text = message.lowercase(Locale.ROOT)
        return when {
            "not supported" in text || "unsupported" in text -> "unsupported"
            "outbound not found" in text || "decode config" in text -> "configuration"
            "authenticate" in text || "unauthorized" in text || "auth failed" in text -> "authentication"
            "x509" in text || "certificate" in text || "tls" in text -> "tls"
            "lookup" in text || "resolve" in text || "dns" in text -> "dns"
            "deadline" in text || "timeout" in text -> "timeout"
            "refused" in text || "unreachable" in text || "network" in text -> "network"
            else -> "other"
        }
    }

    fun record(context: Context, stage: String, reason: String) {
        val category = category(reason)
        Log.w("PxlProbe", "$stage: $category")
        runCatching {
            synchronized(this) {
                val file = File(context.filesDir, FILE_NAME)
                val entries = if (file.isFile) file.readLines().takeLast(15) else emptyList()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText((entries + "$timestamp $stage: $category").joinToString("\n"))
            }
        }
    }

    fun read(context: Context): String = runCatching {
        File(context.filesDir, FILE_NAME).takeIf(File::isFile)?.readText().orEmpty()
    }.getOrDefault("")
}
