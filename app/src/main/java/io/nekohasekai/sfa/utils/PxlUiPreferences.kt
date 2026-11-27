package io.nekohasekai.sfa.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import io.nekohasekai.sfa.BuildConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences for app appearance and the home widget, never VPN service or profiles. */
object PxlUiPreferences {
    enum class InterfaceTheme {
        CLASSIC, FOCUS, TRIAL;

        val available: Boolean get() = this != TRIAL || BuildConfig.FOCUS_LAB

        companion object {
            internal fun fromPersisted(
                value: String?,
                focusLab: Boolean = BuildConfig.FOCUS_LAB,
                defaultToConsole: Boolean = true,
            ): InterfaceTheme =
                entries.firstOrNull { it.name == value && (it != TRIAL || focusLab) }
                    ?: if (focusLab && defaultToConsole) TRIAL else CLASSIC
        }
    }
    enum class ThemeIntroduction { OFFER, DONE }

    internal data class ThemeDefaults(val theme: InterfaceTheme, val introduction: ThemeIntroduction)

    internal fun themeDefaults(upgrade: Boolean, explicit: String?, focusLab: Boolean): ThemeDefaults {
        val hasChoice = InterfaceTheme.entries.any { it.name == explicit && (it != InterfaceTheme.TRIAL || focusLab) }
        return ThemeDefaults(
            InterfaceTheme.fromPersisted(null, focusLab, defaultToConsole = !upgrade),
            if (upgrade && !hasChoice && focusLab) ThemeIntroduction.OFFER else ThemeIntroduction.DONE,
        )
    }
    enum class FocusDetails { ROUTING, SUBSCRIPTION, HIDDEN }

    data class FocusAppearance(
        val mascot: Boolean = true,
        val details: FocusDetails = FocusDetails.ROUTING,
        val floating: Boolean = true,
        val showSpeed: Boolean = true,
        val showNavLabels: Boolean = false,
    )

    private val mutableInterfaceTheme = MutableStateFlow(InterfaceTheme.fromPersisted(null))
    val interfaceTheme = mutableInterfaceTheme.asStateFlow()
    private val mutableThemeIntroduction = MutableStateFlow(ThemeIntroduction.DONE)
    val themeIntroduction = mutableThemeIntroduction.asStateFlow()
    private val mutableThemeIntroductionReplay = MutableStateFlow(false)
    val themeIntroductionReplay = mutableThemeIntroductionReplay.asStateFlow()

    fun replayThemeIntroduction() {
        mutableThemeIntroductionReplay.value = true
    }
    private val mutableFocusAppearance = MutableStateFlow(FocusAppearance())
    val focusAppearance = mutableFocusAppearance.asStateFlow()

    fun setInterfaceTheme(context: Context, theme: InterfaceTheme) =
        saveThemeIntroduction(context, ThemeIntroduction.DONE, theme)

    fun saveThemeIntroduction(context: Context, introduction: ThemeIntroduction, theme: InterfaceTheme? = null) {
        if (theme != null && !theme.available) return
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            .edit().apply {
                putString("theme_introduction", introduction.name)
                if (theme != null) putString("interface_theme", theme.name)
            }.apply()
        if (theme != null) mutableInterfaceTheme.value = theme
        mutableThemeIntroduction.value = introduction
        mutableThemeIntroductionReplay.value = false
    }

    fun isFocusTileHidden(context: Context): Boolean =
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).getBoolean("focus_tile_hidden", false)

    fun hideFocusTile(context: Context) {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit()
            .putBoolean("focus_tile_hidden", true).apply()
    }

    fun setFocusAppearance(context: Context, appearance: FocusAppearance) {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit()
            .putBoolean("focus_mascot", appearance.mascot)
            .putString("focus_details", appearance.details.name)
            .putBoolean("focus_floating", appearance.floating)
            .putBoolean("focus_speed", appearance.showSpeed)
            .putBoolean("focus_tab_labels", appearance.showNavLabels)
            .apply()
        mutableFocusAppearance.value = appearance
    }

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
        val explicitTheme = preferences.getString("interface_theme", null)
        var defaultTheme = preferences.getString("interface_theme_default", null)
        var introduction = preferences.getString("theme_introduction", null)?.let {
            if (it == ThemeIntroduction.OFFER.name) ThemeIntroduction.OFFER else ThemeIntroduction.DONE
        }
        if (defaultTheme == null) {
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val defaults = themeDefaults(packageInfo.lastUpdateTime > packageInfo.firstInstallTime, explicitTheme, BuildConfig.FOCUS_LAB)
            defaultTheme = defaults.theme.name
            introduction = introduction ?: defaults.introduction
            preferences.edit().putString("interface_theme_default", defaultTheme)
                .putString("theme_introduction", introduction.name).apply()
        }
        mutableInterfaceTheme.value = InterfaceTheme.fromPersisted(explicitTheme ?: defaultTheme)
        mutableThemeIntroduction.value = introduction ?: ThemeIntroduction.DONE
        mutableFocusAppearance.value = FocusAppearance(
            mascot = preferences.getBoolean("focus_mascot", true),
            details = FocusDetails.entries.firstOrNull {
                it.name == preferences.getString("focus_details", null)
            } ?: FocusDetails.ROUTING,
            floating = preferences.getBoolean("focus_floating", true),
            showSpeed = preferences.getBoolean("focus_speed", true),
            showNavLabels = preferences.getBoolean("focus_tab_labels", false),
        )
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
