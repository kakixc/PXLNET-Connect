package io.nekohasekai.sfa.utils

import android.content.Context
import android.text.format.Formatter
import io.nekohasekai.sfa.R
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

object SubscriptionInfoStore {
    private const val PREFERENCES = "pxlnet_subscription_info"
    private const val ACCOUNT_EXPIRY = "account_expiry"
    private const val SKIPPED_XHTTP_SUFFIX = "_skipped_xhttp"

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

    fun saveSkippedXhttp(context: Context, profileId: Long, count: Int) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt("$profileId$SKIPPED_XHTTP_SUFFIX", count)
            .apply()
    }

    fun skippedXhttp(context: Context, profileId: Long): Int =
        if (profileId <= 0) 0 else context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt("$profileId$SKIPPED_XHTTP_SUFFIX", 0)

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

    fun saveAccountExpiry(context: Context, value: String?) {
        val epochSeconds = value?.let(::parseExpiry) ?: 0L
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putLong(ACCOUNT_EXPIRY, epochSeconds)
            .apply()
        if (epochSeconds > 0) PxlSubscriptionReminderWork.schedule(context)
    }

    fun clearAccountExpiry(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(ACCOUNT_EXPIRY)
            .apply()
    }

    fun effectiveExpiry(context: Context, profileId: Long): Long {
        val accountExpiry = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(ACCOUNT_EXPIRY, 0L)
        return accountExpiry.takeIf { it > 0 } ?: read(context, profileId)?.expire ?: 0L
    }

    fun summary(context: Context, profileId: Long): String? {
        val info = read(context, profileId) ?: return null
        return summary(
            info,
            System.currentTimeMillis() / 1000,
            usage = { used, total ->
                context.getString(
                    R.string.pxlnet_link_usage,
                    Formatter.formatShortFileSize(context, used),
                    Formatter.formatShortFileSize(context, total),
                )
            },
            future = { date -> context.getString(R.string.pxlnet_link_expiry_future, date) },
            expired = { date -> context.getString(R.string.pxlnet_link_expiry_expired, date) },
        )
    }

    internal fun summary(info: Info, nowSeconds: Long): String? = summary(
        info,
        nowSeconds,
        usage = { used, total -> "Использовано ${formatBytes(used)} из ${formatBytes(total)}" },
        future = { date -> "указан срок до $date" },
        expired = { date -> "указанный срок истёк $date" },
    )

    private fun summary(
        info: Info,
        nowSeconds: Long,
        usage: (Long, Long) -> String,
        future: (String) -> String,
        expired: (String) -> String,
    ): String? {
        val parts = mutableListOf<String>()
        if (info.total > 0) {
            parts += usage(info.upload + info.download, info.total)
        }
        if (info.expire > 0) {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.expire * 1000))
            parts += if (info.expire > nowSeconds) future(date) else expired(date)
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

    private fun parseExpiry(value: String): Long = runCatching {
        Instant.parse(value).epochSecond
    }.recoverCatching {
        OffsetDateTime.parse(value).toEpochSecond()
    }.recoverCatching {
        LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toEpochSecond()
    }.recoverCatching {
        LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    }.getOrDefault(0L)
}
