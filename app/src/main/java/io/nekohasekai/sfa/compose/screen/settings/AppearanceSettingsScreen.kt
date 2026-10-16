package io.nekohasekai.sfa.compose.screen.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.nekohasekai.sfa.compose.theme.AppAccent
import io.nekohasekai.sfa.compose.theme.AppThemeMode
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlMascotSettings
import io.nekohasekai.sfa.utils.PxlUiPreferences
import io.nekohasekai.sfa.utils.PxlUiPreferences.WidgetColorStyle
import io.nekohasekai.sfa.widget.PxlConnectWidgetProvider

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
    val mascotEnabled by PxlMascotSettings.enabled.collectAsState()
    val mascotAnimations by PxlMascotSettings.animationsEnabled.collectAsState()
    val mascotTips by PxlMascotSettings.tipsEnabled.collectAsState()
    var themeMode by remember { mutableStateOf(AppThemeMode.fromPersisted(Settings.themeMode)) }
    var accent by remember { mutableStateOf(AppAccent.fromPersisted(Settings.accent)) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showWidgetStyleDialog by remember { mutableStateOf(false) }

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = { Text("${themeMode.label()} · ${accent.label()}") },
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                modifier = Modifier.clickable { showThemeDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
    val haptics = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(it)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = toggle) },
        modifier = Modifier
            .clickable(enabled = enabled) { toggle(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
