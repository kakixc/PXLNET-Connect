package io.nekohasekai.sfa.utils

import android.content.Context
import android.text.format.Formatter
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.StartServiceCoordinator
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.widget.PxlConnectWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _summaryRevision = MutableStateFlow(0)
    val summaryRevision = _summaryRevision.asStateFlow()
    private const val PREFERENCES = "pxlnet_subscription_info"
    private const val ACCOUNT_EXPIRY = "account_expiry"
    private const val SKIPPED_XHTTP_SUFFIX = "_skipped_xhttp"
    private const val SMART_STATUS_SUFFIX = "_smart_status"

    enum class SmartStatus { ACTIVE, EXPIRED, PAUSED, BLOCKED, REVOKED, UNKNOWN }

    /**
     * The locally verifiable access state of one saved VPN profile.  UNKNOWN deliberately means
     * "do not block": an unavailable account service must not take an existing guest or Legacy
     * profile away from its owner.
     */
    enum class ConnectionAccess { ALLOWED, SMART_INACTIVE, LEGACY_EXPIRED }

    internal fun isPxlManagedInactive(access: ConnectionAccess): Boolean =
        access == ConnectionAccess.SMART_INACTIVE

    internal fun parseSmartStatus(header: String?): SmartStatus? = when (header?.trim()?.lowercase()) {
        null, "" -> null
        "active" -> SmartStatus.ACTIVE
        "expired" -> SmartStatus.EXPIRED
        "paused" -> SmartStatus.PAUSED
        "blocked" -> SmartStatus.BLOCKED
        "revoked" -> SmartStatus.REVOKED
        else -> SmartStatus.UNKNOWN
    }

    internal fun isExplicitInactiveSmartStatus(header: String?): Boolean =
        parseSmartStatus(header) in setOf(SmartStatus.EXPIRED, SmartStatus.PAUSED, SmartStatus.BLOCKED, SmartStatus.REVOKED)

    /** A successful server response may revoke access even when its config cannot be imported. */
    fun recordExplicitInactiveResponse(context: Context, profileId: Long, header: String?): Boolean {
        if (profileId <= 0 || !isExplicitInactiveSmartStatus(header)) return false
        saveSmartStatus(context, profileId, header)
        return true
    }

    fun saveSmartStatus(context: Context, profileId: Long, header: String?) {
        val status = parseSmartStatus(header)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            if (status == null) remove("$profileId$SMART_STATUS_SUFFIX")
            else putString("$profileId$SMART_STATUS_SUFFIX", status.name)
        }.apply()
        _summaryRevision.value += 1
        PxlConnectWidgetProvider.updateAll(context)
        if (profileId == Settings.selectedProfile &&
            status in setOf(SmartStatus.EXPIRED, SmartStatus.PAUSED, SmartStatus.BLOCKED, SmartStatus.REVOKED) &&
            StartServiceCoordinator.currentStatus() == Status.Started
        ) {
            BoxService.stop()
        }
    }

    /** Foreground service checks again soon, and exactly at a nearer saved expiry. */
    internal fun nextAccessCheckDelayMillis(expirySeconds: Long, nowSeconds: Long): Long =
        if (expirySeconds > nowSeconds) (expirySeconds - nowSeconds).coerceAtMost(30) * 1000
        else 30_000

    fun smartStatus(context: Context, profileId: Long): SmartStatus? {
        if (profileId <= 0) return null
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString("$profileId$SMART_STATUS_SUFFIX", null)
        return stored?.let { runCatching { SmartStatus.valueOf(it) }.getOrNull() }
    }

    internal fun connectionAccess(
        smartStatus: SmartStatus?,
        legacyExpiry: Long,
        nowSeconds: Long,
    ): ConnectionAccess = when {
        smartStatus in setOf(SmartStatus.EXPIRED, SmartStatus.PAUSED, SmartStatus.BLOCKED, SmartStatus.REVOKED) ->
            ConnectionAccess.SMART_INACTIVE
        // The profile-local expiry remains definite evidence even if an older Smart ACTIVE header
        // has not been refreshed yet. A Smart UNKNOWN response is deliberately non-authoritative:
        // preserve the existing guest/Legacy profile until a definite status arrives. Do not use
        // account expiry: it may describe a Telegram account while this selected profile is independent.
        smartStatus != SmartStatus.UNKNOWN && legacyExpiry > 0 && legacyExpiry <= nowSeconds -> ConnectionAccess.LEGACY_EXPIRED
        else -> ConnectionAccess.ALLOWED
    }

    fun connectionAccess(context: Context, profileId: Long): ConnectionAccess {
        if (profileId <= 0) return ConnectionAccess.ALLOWED
        return connectionAccess(
            smartStatus(context, profileId),
            read(context, profileId)?.expire ?: 0,
            System.currentTimeMillis() / 1000,
        )
    }

    data class Info(
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expire: Long = 0,
    )

    internal fun storedHeaderAfterAcceptedRefresh(header: String?): String? = header?.takeIf(String::isNotBlank)

    internal fun profileMetadataKeys(profileId: Long): List<String> = listOf(
        profileId.toString(),
        "$profileId$SMART_STATUS_SUFFIX",
        "$profileId$SKIPPED_XHTTP_SUFFIX",
    )

    /** Clears metadata bound to a deleted profile ID before that ID can be reused. */
    fun clearProfile(context: Context, profileId: Long) {
        if (profileId <= 0) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            profileMetadataKeys(profileId).forEach(::remove)
        }.apply()
        _summaryRevision.value += 1
        PxlConnectWidgetProvider.updateAll(context)
    }

    fun save(context: Context, profileId: Long, header: String?) {
        if (profileId <= 0) return
        val storedHeader = storedHeaderAfterAcceptedRefresh(header)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            if (storedHeader == null) remove(profileId.toString())
            else putString(profileId.toString(), storedHeader)
        }.apply()
        _summaryRevision.value += 1
        PxlConnectWidgetProvider.updateAll(context)
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
        val smartStatus = smartStatus(context, profileId)
        if (smartStatus != null && smartStatus != SmartStatus.ACTIVE) {
            val label = when (smartStatus) {
                SmartStatus.EXPIRED -> R.string.pxlnet_smart_status_expired
                SmartStatus.PAUSED -> R.string.pxlnet_smart_status_paused
                SmartStatus.BLOCKED -> R.string.pxlnet_smart_status_blocked
                SmartStatus.REVOKED -> R.string.pxlnet_smart_status_revoked
                else -> R.string.pxlnet_smart_status_unknown
            }
            return context.getString(label)
        }
        val info = read(context, profileId)
            ?: return if (smartStatus == SmartStatus.ACTIVE) {
                context.getString(R.string.pxlnet_smart_status_active_no_details)
            } else null
        val detail = summary(
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
        return if (smartStatus == SmartStatus.ACTIVE) {
            context.getString(R.string.pxlnet_smart_status_active, detail.orEmpty())
        } else detail
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
