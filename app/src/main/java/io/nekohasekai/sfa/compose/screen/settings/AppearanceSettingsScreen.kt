package io.nekohasekai.sfa.compose.screen.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.rememberPxlHaptics
import io.nekohasekai.sfa.compose.component.PxlSwitch as Switch
import io.nekohasekai.sfa.compose.theme.AppAccent
import io.nekohasekai.sfa.compose.theme.AppThemeMode
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlMascotSettings
import io.nekohasekai.sfa.utils.PxlUiPreferences
import io.nekohasekai.sfa.utils.PxlUiPreferences.FontMode
import io.nekohasekai.sfa.utils.PxlUiPreferences.WidgetColorStyle
import io.nekohasekai.sfa.widget.PxlConnectWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.pxlnet_settings_appearance)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val context = LocalContext.current
    val floatingNavigation by PxlUiPreferences.floatingNavigation.collectAsState()
    val floatingLabels by PxlUiPreferences.floatingNavigationLabels.collectAsState()
    val navigationBlur by PxlUiPreferences.navigationBlur.collectAsState()
    val widgetColorStyle by PxlUiPreferences.widgetColorStyle.collectAsState()
    val fontSelection by PxlUiPreferences.fontSelection.collectAsState()
    val fontWeightLevel by PxlUiPreferences.fontWeightLevel.collectAsState()
    val predictiveBack by PxlUiPreferences.predictiveBack.collectAsState()
    val hapticsEnabled by PxlUiPreferences.hapticsEnabled.collectAsState()
    val pxlHaptics = rememberPxlHaptics()
    val mascotEnabled by PxlMascotSettings.enabled.collectAsState()
    val mascotAnimations by PxlMascotSettings.animationsEnabled.collectAsState()
    val mascotTips by PxlMascotSettings.tipsEnabled.collectAsState()
    var themeMode by remember { mutableStateOf(AppThemeMode.fromPersisted(Settings.themeMode)) }
    var accent by remember { mutableStateOf(AppAccent.fromPersisted(Settings.accent)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showWidgetStyleDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var fontImportError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { PxlUiPreferences.importCustomFont(context, uri) }.isSuccess
            }
            if (imported) showFontDialog = false else fontImportError = true
        }
    }

    if (fontImportError) {
        AlertDialog(
            onDismissRequest = { fontImportError = false },
            title = { Text(stringResource(R.string.pxlnet_font_import_error_title)) },
            text = { Text(stringResource(R.string.pxlnet_font_import_error_message)) },
            confirmButton = {
                TextButton(onClick = { fontImportError = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(stringResource(R.string.pxlnet_font_title)) },
            text = {
                Column {
                    listOf(
                        FontMode.PXLNET to R.string.pxlnet_font_default,
                        FontMode.SYSTEM to R.string.pxlnet_font_system,
                        FontMode.CUSTOM to R.string.pxlnet_font_custom,
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (mode == FontMode.CUSTOM && fontSelection.customFileName == null) {
                                    fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                                } else {
                                    PxlUiPreferences.setFontMode(context, mode)
                                    showFontDialog = false
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = fontSelection.mode == mode, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (fontSelection.customFileName != null) {
                        TextButton(onClick = {
                            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                        }) { Text(stringResource(R.string.pxlnet_font_replace)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontDialog = false }) { Text(stringResource(R.string.pxlnet_widget_config_cancel)) }
            },
        )
    }

    if (showWidgetStyleDialog) {
        val haptics = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { showWidgetStyleDialog = false },
            title = { Text(stringResource(R.string.pxlnet_settings_widget_dynamic_color)) },
            text = {
                Column {
                    listOf(
                        WidgetColorStyle.CLASSIC to R.string.pxlnet_widget_config_classic,
                        WidgetColorStyle.MONET to R.string.pxlnet_widget_config_monet,
                        WidgetColorStyle.MONET_SOFT to R.string.pxlnet_widget_config_monet_soft,
                    ).forEach { (style, label) ->
                        val enabled = style == WidgetColorStyle.CLASSIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    PxlUiPreferences.setWidgetColorStyle(context, style)
                                    PxlConnectWidgetProvider.updateAll(context)
                                    showWidgetStyleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = widgetColorStyle == style, onClick = null, enabled = enabled)
                            Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetStyleDialog = false }) {
                    Text(stringResource(R.string.pxlnet_widget_config_cancel))
                }
            },
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentThemeMode = themeMode,
            currentAccent = accent,
            dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onThemeSelected = { selected ->
                themeMode = selected
                Settings.themeMode = selected.persistedValue
            },
            onAccentSelected = { selected ->
                accent = selected
                Settings.accent = selected.persistedValue
                Settings.dynamicColor = selected == AppAccent.WALLPAPER
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = { Text("${themeMode.label()} · ${accent.label()}") },
                leadingContent = { AppearanceSettingsIcon(Icons.Outlined.Palette) },
                modifier = Modifier.clickable { showThemeDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_predictive_back_title)) },
                supportingContent = { Text(stringResource(R.string.pxlnet_predictive_back_summary)) },
                leadingContent = { AppearanceSettingsIcon(Icons.Outlined.Animation) },
                trailingContent = {
                    Switch(
                        checked = predictiveBack,
                        onCheckedChange = { PxlUiPreferences.setPredictiveBack(context, it) },
                        enabled = Build.VERSION.SDK_INT >= 33,
                    )
                },
                modifier = Modifier.clickable(enabled = Build.VERSION.SDK_INT >= 33) {
                    PxlUiPreferences.setPredictiveBack(context, !predictiveBack)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_font_title)) },
                supportingContent = {
                    Text(when (fontSelection.mode) {
                        FontMode.PXLNET -> stringResource(R.string.pxlnet_font_default)
                        FontMode.SYSTEM -> stringResource(R.string.pxlnet_font_system)
                        FontMode.CUSTOM -> fontSelection.customLabel ?: stringResource(R.string.pxlnet_font_custom)
                    })
                },
                leadingContent = { AppearanceSettingsIcon(Icons.Outlined.TextFields) },
                modifier = Modifier.clickable { showFontDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (fontSelection.mode == FontMode.PXLNET) Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.pxlnet_font_weight_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(when (fontWeightLevel) {
                        0 -> R.string.pxlnet_font_weight_normal
                        1 -> R.string.pxlnet_font_weight_medium
                        else -> R.string.pxlnet_font_weight_bold
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fontWeightLevel.toFloat(),
                    onValueChange = {
                        val level = it.roundToInt()
                        if (level != fontWeightLevel) {
                            pxlHaptics.sliderDetent()
                            PxlUiPreferences.setFontWeightLevel(context, level)
                        }
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            AppearanceSwitchRow(
                title = stringResource(R.string.pxlnet_haptics_title),
                description = stringResource(R.string.pxlnet_haptics_summary),
                icon = Icons.Outlined.Animation,
                checked = hapticsEnabled,
                onChange = { PxlUiPreferences.setHapticsEnabled(context, it) },
            )
        }

        Text(
            stringResource(R.string.pxlnet_settings_widget_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_settings_widget_dynamic_color)) },
                supportingContent = {
                    Text(stringResource(when (widgetColorStyle) {
                        WidgetColorStyle.CLASSIC -> R.string.pxlnet_widget_config_classic
                        WidgetColorStyle.MONET -> R.string.pxlnet_widget_config_monet
                        WidgetColorStyle.MONET_SOFT -> R.string.pxlnet_widget_config_monet_soft
                    }))
                },
                leadingContent = { AppearanceSettingsIcon(Icons.Outlined.Palette) },
                modifier = Modifier.clickable { showWidgetStyleDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Text(
            stringResource(R.string.pxlnet_settings_floating_navigation),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            AppearanceSwitchRow(
                title = stringResource(R.string.pxlnet_settings_floating_navigation),
                description = stringResource(R.string.pxlnet_settings_floating_navigation_summary),
                icon = Icons.Outlined.Settings,
                checked = floatingNavigation,
                onChange = { PxlUiPreferences.setFloatingNavigation(context, it) },
            )
            if (floatingNavigation) {
                AppearanceSwitchRow(
                    title = stringResource(R.string.pxlnet_settings_floating_navigation_labels),
                    description = stringResource(R.string.pxlnet_settings_floating_navigation_labels_summary),
                    icon = Icons.Outlined.Settings,
                    checked = floatingLabels,
                    onChange = { PxlUiPreferences.setFloatingNavigationLabels(context, it) },
                )
            }
            AppearanceSwitchRow(
                title = stringResource(R.string.pxlnet_settings_navigation_blur),
                description = stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && floatingNavigation) {
                        R.string.pxlnet_settings_navigation_blur_summary
                    } else {
                        R.string.pxlnet_settings_navigation_blur_unavailable
                    },
                ),
                icon = Icons.Outlined.Animation,
                checked = navigationBlur,
                enabled = floatingNavigation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onChange = { PxlUiPreferences.setNavigationBlur(context, it) },
            )
        }

        Text(
            stringResource(R.string.pxlnet_mascot_setting_title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            AppearanceSwitchRow(
                title = stringResource(R.string.pxlnet_mascot_setting_title),
                description = stringResource(R.string.pxlnet_mascot_setting_description),
                icon = Icons.Outlined.Pets,
                checked = mascotEnabled,
                onChange = PxlMascotSettings::setEnabled,
            )
            if (mascotEnabled) {
                AppearanceSwitchRow(
                    title = stringResource(R.string.pxlnet_mascot_animations_title),
                    description = stringResource(R.string.pxlnet_mascot_animations_description),
                    icon = Icons.Outlined.Animation,
                    checked = mascotAnimations,
                    onChange = PxlMascotSettings::setAnimationsEnabled,
                )
                AppearanceSwitchRow(
                    title = stringResource(R.string.pxlnet_mascot_tips_title),
                    description = stringResource(R.string.pxlnet_mascot_tips_description),
                    icon = Icons.Outlined.ChatBubbleOutline,
                    checked = mascotTips,
                    onChange = PxlMascotSettings::setTipsEnabled,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSwitchRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val haptics = rememberPxlHaptics()
    val toggle: (Boolean) -> Unit = {
        haptics.toggle(it)
        onChange(it)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { AppearanceSettingsIcon(icon) },
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = onChange) },
        modifier = Modifier
            .clickable(enabled = enabled) { toggle(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AppearanceSettingsIcon(imageVector: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
