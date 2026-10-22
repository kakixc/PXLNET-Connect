package io.nekohasekai.sfa.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences for app appearance and the home widget, never VPN service or profiles. */
object PxlUiPreferences {
    enum class FontMode { PXLNET, SYSTEM, CUSTOM }

    data class FontSelection(val mode: FontMode, val customFileName: String? = null, val customLabel: String? = null)

    enum class WidgetColorStyle {
        CLASSIC, MONET, MONET_SOFT;

        companion object {
            fun fromPersisted(value: String?, legacyMonet: Boolean): WidgetColorStyle =
                entries.firstOrNull { it.name == value }
                    ?: if (legacyMonet) MONET else CLASSIC

            fun forAndroidVersion(style: WidgetColorStyle, sdk: Int): WidgetColorStyle =
                if (sdk >= 31) style else CLASSIC
        }
    }

    private const val fileName = "pxlnet_ui"
    private const val floatingNavigationKey = "floating_navigation"
    private const val navigationBlurKey = "navigation_blur"
    private const val floatingNavigationLabelsKey = "floating_navigation_labels"
    private const val widgetDynamicColorKey = "widget_dynamic_color"
    private const val widgetColorStyleKey = "widget_color_style"
    private const val fontModeKey = "font_mode"
    private const val customFontFileKey = "custom_font_file"
    private const val customFontLabelKey = "custom_font_label"
    private const val fontWeightLevelKey = "font_weight_level"
    private const val predictiveBackKey = "predictive_back"
    private const val hapticsEnabledKey = "haptics_enabled"
    private const val maxCustomFontBytes = 10L * 1024 * 1024

    private val mutableFloatingNavigation = MutableStateFlow(true)
    val floatingNavigation = mutableFloatingNavigation.asStateFlow()
    private val mutableNavigationBlur = MutableStateFlow(true)
    val navigationBlur = mutableNavigationBlur.asStateFlow()
    private val mutableFloatingNavigationLabels = MutableStateFlow(false)
    val floatingNavigationLabels = mutableFloatingNavigationLabels.asStateFlow()
    private val mutableWidgetColorStyle = MutableStateFlow(WidgetColorStyle.CLASSIC)
    val widgetColorStyle = mutableWidgetColorStyle.asStateFlow()
    private val mutableFontSelection = MutableStateFlow(FontSelection(FontMode.PXLNET))
    val fontSelection = mutableFontSelection.asStateFlow()
    private val mutableFontWeightLevel = MutableStateFlow(1)
    val fontWeightLevel = mutableFontWeightLevel.asStateFlow()
    private val mutablePredictiveBack = MutableStateFlow(true)
    val predictiveBack = mutablePredictiveBack.asStateFlow()
    private val mutableHapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled = mutableHapticsEnabled.asStateFlow()

    fun initialize(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        mutableFloatingNavigation.value = preferences.getBoolean(floatingNavigationKey, true)
        mutableNavigationBlur.value = preferences.getBoolean(navigationBlurKey, true)
        mutableFloatingNavigationLabels.value = preferences.getBoolean(floatingNavigationLabelsKey, false)
        mutableWidgetColorStyle.value = readWidgetColorStyle(context)
        val fileName = preferences.getString(customFontFileKey, null)
        val available = fileName != null && File(fontDirectory(context), fileName).isFile
        val mode = runCatching { FontMode.valueOf(preferences.getString(fontModeKey, FontMode.PXLNET.name)!!) }
            .getOrDefault(FontMode.PXLNET)
        mutableFontSelection.value = FontSelection(
            if (mode == FontMode.CUSTOM && !available) FontMode.PXLNET else mode,
            fileName.takeIf { available },
            preferences.getString(customFontLabelKey, null).takeIf { available },
        )
        mutableFontWeightLevel.value = preferences.getInt(fontWeightLevelKey, 1).coerceIn(0, 2)
        mutablePredictiveBack.value = preferences.getBoolean(predictiveBackKey, true)
        mutableHapticsEnabled.value = preferences.getBoolean(hapticsEnabledKey, true)
    }

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putBoolean(hapticsEnabledKey, enabled).apply()
        mutableHapticsEnabled.value = enabled
    }

    fun setPredictiveBack(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putBoolean(predictiveBackKey, enabled).apply()
        mutablePredictiveBack.value = enabled
    }

    fun setFontWeightLevel(context: Context, level: Int) {
        val validLevel = level.coerceIn(0, 2)
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putInt(fontWeightLevelKey, validLevel).apply()
        mutableFontWeightLevel.value = validLevel
    }

    fun setFontMode(context: Context, mode: FontMode) {
        val current = mutableFontSelection.value
        if (mode == FontMode.CUSTOM && current.customFileName == null) return
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putString(fontModeKey, mode.name).apply()
        mutableFontSelection.value = current.copy(mode = mode)
    }

    fun customFontFile(context: Context, selection: FontSelection): File? =
        selection.customFileName?.let { File(fontDirectory(context), it) }?.takeIf { it.isFile }

    /** Copies a SAF document into private storage; the URI itself is never persisted. Call off the main thread. */
    fun importCustomFont(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val label = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Custom font"
        require(label.endsWith(".ttf", true) || label.endsWith(".otf", true)) { "Unsupported font format" }
        val directory = fontDirectory(context).apply { mkdirs() }
        val newName = "font_${UUID.randomUUID()}.ttf"
        val destination = File(directory, newName)
        try {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxCustomFontBytes) { "Font file is too large" }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "Font file is empty" }
                }
            } ?: error("Cannot read font file")
            Typeface.createFromFile(destination)
            val oldName = mutableFontSelection.value.customFileName
            context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .edit()
                .putString(customFontFileKey, newName)
                .putString(customFontLabelKey, label.take(80))
                .putString(fontModeKey, FontMode.CUSTOM.name)
                .apply()
            mutableFontSelection.value = FontSelection(FontMode.CUSTOM, newName, label.take(80))
            if (oldName != null && oldName != newName) File(directory, oldName).delete()
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun fontDirectory(context: Context) = File(context.applicationContext.filesDir, "custom_fonts")

    fun setFloatingNavigation(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putBoolean(floatingNavigationKey, enabled).apply()
        mutableFloatingNavigation.value = enabled
    }

    fun setNavigationBlur(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putBoolean(navigationBlurKey, enabled).apply()
        mutableNavigationBlur.value = enabled
    }

    fun setFloatingNavigationLabels(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().putBoolean(floatingNavigationLabelsKey, enabled).apply()
        mutableFloatingNavigationLabels.value = enabled
    }

    fun readWidgetColorStyle(context: Context): WidgetColorStyle {
        val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        val stored = preferences.getString(widgetColorStyleKey, null)
        return WidgetColorStyle.fromPersisted(stored, preferences.getBoolean(widgetDynamicColorKey, false))
    }

    fun setWidgetColorStyle(context: Context, style: WidgetColorStyle) {
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit()
            .putString(widgetColorStyleKey, style.name)
            .putBoolean(widgetDynamicColorKey, style != WidgetColorStyle.CLASSIC)
            .apply()
        mutableWidgetColorStyle.value = style
    }
}
