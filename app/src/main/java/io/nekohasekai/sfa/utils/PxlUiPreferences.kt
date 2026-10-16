package io.nekohasekai.sfa.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences for app appearance and the home widget, never VPN service or profiles. */
object PxlUiPreferences {
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

    private val mutableFloatingNavigation = MutableStateFlow(false)
    val floatingNavigation = mutableFloatingNavigation.asStateFlow()
    private val mutableNavigationBlur = MutableStateFlow(false)
    val navigationBlur = mutableNavigationBlur.asStateFlow()
    private val mutableFloatingNavigationLabels = MutableStateFlow(true)
    val floatingNavigationLabels = mutableFloatingNavigationLabels.asStateFlow()
    private val mutableWidgetColorStyle = MutableStateFlow(WidgetColorStyle.CLASSIC)
    val widgetColorStyle = mutableWidgetColorStyle.asStateFlow()

    fun initialize(context: Context) {
        val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        mutableFloatingNavigation.value = preferences.getBoolean(floatingNavigationKey, false)
        mutableNavigationBlur.value = preferences.getBoolean(navigationBlurKey, false)
        mutableFloatingNavigationLabels.value = preferences.getBoolean(floatingNavigationLabelsKey, true)
        mutableWidgetColorStyle.value = readWidgetColorStyle(context)
    }

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
