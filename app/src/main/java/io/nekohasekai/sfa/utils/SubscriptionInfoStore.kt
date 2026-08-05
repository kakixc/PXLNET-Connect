package io.nekohasekai.sfa.utils

import android.content.Context
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

object SubscriptionInfoStore {
    private const val PREFERENCES = "pxlnet_subscription_info"

    data class Info(
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expire: Long = 0,
    )

    fun save(context: Context, profileId: Long, header: String?) {
        if (header.isNullOrBlank()) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(profileId.toString(), header)
            .apply()
    }

    fun read(context: Context, profileId: Long): Info? {
        if (profileId <= 0) return null
        val header =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(profileId.toString(), null)
                ?: return null
        val values =
            header.split(';')
                .map(String::trim)
                .mapNotNull { field ->
                    val parts = field.split('=', limit = 2)
                    if (parts.size == 2) parts[0].lowercase() to parts[1].toLongOrNull() else null
                }.toMap()
        return Info(
            upload = values["upload"] ?: 0,
            download = values["download"] ?: 0,
            total = values["total"] ?: 0,
            expire = values["expire"] ?: 0,
        )
    }

    fun summary(context: Context, profileId: Long): String? {
        val info = read(context, profileId) ?: return null
        val parts = mutableListOf<String>()
        if (info.total > 0) {
            parts += "Использовано ${formatBytes(info.upload + info.download)} из ${formatBytes(info.total)}"
        }
        if (info.expire > 0) {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.expire * 1000))
            parts += "активна до $date"
        }
        return parts.joinToString(" · ").takeIf(String::isNotBlank)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val unit = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val suffix = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")[unit]
        val value = bytes / 1024.0.pow(unit.toDouble())
        return if (value >= 10) "%.0f %s".format(value, suffix) else "%.1f %s".format(value, suffix)
    }
}
