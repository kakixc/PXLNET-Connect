package io.nekohasekai.sfa.utils

/** Text that is safe to copy or export; raw VPN configuration is never included. */
object PxlCrashSupportReport {
    fun format(version: String, android: String, device: String, metadata: String, crashLog: String): String =
        buildString {
            appendLine("Отправьте @kaktusi_top")
            appendLine("Лог: PXLNET Connect")
            appendLine("Версия: $version")
            appendLine("Android: $android")
            appendLine("Устройство: $device")
            appendLine()
            appendLine("Технические подробности (секреты скрыты):")
            appendLine(PxlDiagnosticRedactor.redact(metadata.take(8_000)).ifBlank { "Метаданные недоступны" })
            appendLine()
            append(PxlDiagnosticRedactor.redact(crashLog.take(40_000)).ifBlank { "Журнал краша недоступен" })
        }
}
