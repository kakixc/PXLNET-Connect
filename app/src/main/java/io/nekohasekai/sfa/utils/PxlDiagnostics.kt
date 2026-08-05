package io.nekohasekai.sfa.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.nekohasekai.sfa.BuildConfig
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

    suspend fun inspect(): PxlSupportReport = withContext(Dispatchers.IO) {
        val logs = runCatching {
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

        PxlSupportReport(
            summary = detectProblem(logs),
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
            putExtra(Intent.EXTRA_TEXT, "Обезличенная диагностика PXLNET Connect. Получатель: @pxlnet_bot")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        telegramPackages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
            ?.let { intent.setPackage(it) }

        val target = if (intent.`package` == null) {
            Intent.createChooser(intent, "Отправить диагностику в @pxlnet_bot")
        } else {
            intent
        }
        context.startActivity(target)
    }

    internal fun detectProblem(logs: String): String {
        val text = logs.lowercase(Locale.ROOT)
        return when {
            logs.isBlank() -> "Явная ошибка не найдена. Запустите подключение ещё раз и повторите проверку."
            "decode config" in text || "invalid character" in text ->
                "Похоже, сервер вернул подписку в неподдерживаемом формате."
            "x509" in text || "certificate" in text ->
                "Обнаружена ошибка TLS-сертификата или подмены защищённого соединения."
            "rule-set" in text && ("download" in text || "initialize" in text) ->
                "Не удалось загрузить правила маршрутизации. Попробуйте другую сеть или временно отключите Smart Routing."
            "network is unreachable" in text || "no route to host" in text ->
                "Устройство не видит сеть или выбранный сервер недоступен."
            "connection refused" in text ->
                "Сервер отклонил подключение. Возможно, узел временно выключен."
            "timeout" in text || "deadline exceeded" in text || "i/o timeout" in text ->
                "Подключение превысило время ожидания. Проверьте сеть или выберите другой сервер."
            "permission denied" in text ->
                "Android или системная защита не дала приложению нужное разрешение."
            "error" in text || "failed" in text || "fatal" in text ->
                "Найдена техническая ошибка. Отправьте обезличенный отчёт поддержке."
            else -> "Явная ошибка не найдена. В отчёт будут добавлены последние технические события."
        }
    }
}
