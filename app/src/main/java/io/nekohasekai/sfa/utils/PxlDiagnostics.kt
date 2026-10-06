package io.nekohasekai.sfa.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PxlSupportReport(
    val summary: String,
    val logs: String,
)

object PxlDiagnostics {
    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
    )

    suspend fun inspect(context: Context): PxlSupportReport = withContext(Dispatchers.IO) {
        val appLogs = runCatching {
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "--pid=${android.os.Process.myPid()}",
                "-t",
                "1200",
                "-v",
                "threadtime",
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().use { it.readText().takeLast(250_000) }
        }.getOrDefault("")
        val probeLog = PxlProbeDiagnostics.read(context)
        val logs = if (probeLog.isBlank()) appLogs else buildString {
            appendLine("PXLNET VPN-server probe (categories only):")
            appendLine(probeLog)
            appendLine()
            append(appLogs)
        }

        PxlSupportReport(
            summary = context.getString(detectProblemResource(logs)),
            logs = logs,
        )
    }

    fun shareWithTelegram(context: Context, logs: String, summary: String? = null) {
        val diagnostics = buildString {
            appendLine("PXLNET Connect ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Sensitive values: removed locally before sharing")
            if (!summary.isNullOrBlank()) appendLine("Quick check: $summary")
            appendLine()
            append(PxlDiagnosticRedactor.redact(logs).ifBlank { "No recent application logs." })
        }

        val directory = File(context.cacheDir, "logs").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(directory, "pxlnet_diagnostics_$timestamp.txt")
        file.writeText(diagnostics)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.pxlnet_diagnostics_share_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        telegramPackages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
            ?.let { intent.setPackage(it) }

        val target = if (intent.`package` == null) {
            Intent.createChooser(intent, context.getString(R.string.pxlnet_diagnostics_share_title))
        } else {
            intent
        }
        context.startActivity(target)
    }

    internal fun detectProblemResource(logs: String): Int {
        val text = logs.lowercase(Locale.ROOT)
        val hasAppEvents = logs.lineSequence().any { line ->
            line.isNotBlank() && !line.trimStart().startsWith("--------- beginning of")
        }
        return when {
            !hasAppEvents -> R.string.pxlnet_diagnostics_no_logs
            "decode config" in text || "invalid character" in text ->
                R.string.pxlnet_diagnostics_bad_subscription
            "x509" in text || "certificate" in text ->
                R.string.pxlnet_diagnostics_tls
            "rule-set" in text && ("download" in text || "initialize" in text) ->
                R.string.pxlnet_diagnostics_rules
            "network is unreachable" in text || "no route to host" in text ->
                R.string.pxlnet_diagnostics_network
            "connection refused" in text ->
                R.string.pxlnet_diagnostics_refused
            "timeout" in text || "deadline exceeded" in text || "i/o timeout" in text ->
                R.string.pxlnet_diagnostics_timeout
            "permission denied" in text ->
                R.string.pxlnet_diagnostics_permission
            "error" in text || "failed" in text || "fatal" in text ->
                R.string.pxlnet_diagnostics_error
            else -> R.string.pxlnet_diagnostics_no_clear_error
        }
    }
}
