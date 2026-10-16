package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import io.nekohasekai.sfa.compose.component.PxlSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.component.LocalPxlTopBarInset
import io.nekohasekai.sfa.compose.component.LocalPxlFloatingNavInset
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.vendor.UpdateNotification
import io.nekohasekai.sfa.utils.PxlDeveloperMode
import io.nekohasekai.sfa.utils.PxlDiagnostics
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlLocalPreferences
import io.nekohasekai.sfa.utils.PxlSubscriptionReminderSettings
import io.nekohasekai.sfa.utils.PxlSupportReport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    OverrideTopBar {
        PxlRootTopBar(
            title = stringResource(R.string.title_settings),
        )
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var showAlwaysOnHelp by remember { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<PxlSupportReport?>(null) }
    var checkingDiagnostics by remember { mutableStateOf(false) }
    val developerMode by PxlDeveloperMode.enabled.collectAsState()
    val subscriptionRemindersEnabled by PxlSubscriptionReminderSettings.enabled.collectAsState()
    val hasUpdate by UpdateState.hasUpdate
    var guardEnabled by remember { mutableStateOf(PxlLocalPreferences.isGuardEnabled(context)) }
    val topBarInset = LocalPxlTopBarInset.current
    val floatingNavInset = LocalPxlFloatingNavInset.current
    var batteryOptimizationIgnored by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
    }
    fun refreshBatteryOptimization() {
        batteryOptimizationIgnored = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val batteryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshBatteryOptimization() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshBatteryOptimization() }

    if (showAlwaysOnHelp) {
        AlertDialog(
            onDismissRequest = { showAlwaysOnHelp = false },
            title = { Text(stringResource(R.string.pxlnet_settings_always_on_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pxlnet_settings_always_on_step))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pxlnet_settings_always_on_lockdown))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAlwaysOnHelp = false
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    },
                ) { Text(stringResource(R.string.pxlnet_settings_open_system)) }
            },
            dismissButton = {
                TextButton(onClick = { showAlwaysOnHelp = false }) { Text(stringResource(R.string.pxlnet_settings_close)) }
            },
        )
    }

    diagnosticReport?.let { report ->
        AlertDialog(
            onDismissRequest = { diagnosticReport = null },
            title = { Text(stringResource(R.string.pxlnet_settings_diagnostics_result)) },
            text = {
                Column {
                    Text(report.summary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pxlnet_settings_diagnostics_privacy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        diagnosticReport = null
                        PxlDiagnostics.shareWithTelegram(context, report.logs, report.summary)
                    },
                ) { Text(stringResource(R.string.pxlnet_settings_send_report)) }
            },
            dismissButton = {
                TextButton(onClick = { diagnosticReport = null }) { Text(stringResource(R.string.pxlnet_settings_close)) }
            },
        )
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = topBarInset + 8.dp, bottom = floatingNavInset + 8.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.title_app_settings)) },
                supportingContent = { Text(stringResource(R.string.pxlnet_app_settings_summary)) },
                leadingContent = {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    if (hasUpdate) Badge(containerColor = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { navController.navigate("settings/app") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        Text(
            text = stringResource(R.string.pxlnet_settings_connection),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_settings_subscriptions)) },
                supportingContent = { Text(stringResource(R.string.pxlnet_settings_subscriptions_summary)) },
                leadingContent = {
                    Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { navController.navigate("settings/subscriptions") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_settings_always_on_title)) },
                supportingContent = { Text(stringResource(R.string.pxlnet_settings_always_on_summary)) },
                leadingContent = {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showAlwaysOnHelp = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("PXL Guard") },
                supportingContent = { Text(stringResource(R.string.pxlnet_settings_guard_summary)) },
                leadingContent = {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Switch(
                        checked = guardEnabled,
                        onCheckedChange = {
                            guardEnabled = it
                            PxlLocalPreferences.setGuardEnabled(context, it)
                        },
                    )
                },
                modifier = Modifier.clickable {
                    guardEnabled = !guardEnabled
                    PxlLocalPreferences.setGuardEnabled(context, guardEnabled)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryOptimizationIgnored) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.request_background_permission)) },
                    supportingContent = {
                        Text(stringResource(R.string.pxlnet_battery_request_summary))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { batteryPermissionLauncher.launch(intent) }
                            .onFailure {
                                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            }
        }

        Text(
            text = stringResource(R.string.pxlnet_settings_routing),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_settings_app_rules)) },
                supportingContent = { Text(stringResource(R.string.pxlnet_settings_app_rules_summary)) },
                leadingContent = {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { navController.navigate("pxlnet/app_routing") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Text(
            text = stringResource(R.string.pxlnet_settings_help),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pxlnet_settings_diagnostics)) },
                supportingContent = {
                    Text(
                        if (checkingDiagnostics) {
                            stringResource(R.string.pxlnet_settings_diagnostics_checking)
                        } else {
                            stringResource(R.string.pxlnet_settings_diagnostics_summary)
                        },
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !checkingDiagnostics) {
                        scope.launch {
                            checkingDiagnostics = true
                            diagnosticReport = PxlDiagnostics.inspect(context)
                            checkingDiagnostics = false
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        // App settings contain appearance, updates and advanced preferences.
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.pxlnet_settings_updates),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.pxlnet_subscription_reminders_title)) },
                    supportingContent = { Text(stringResource(R.string.pxlnet_subscription_reminders_description)) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = subscriptionRemindersEnabled,
                            onCheckedChange = { PxlSubscriptionReminderSettings.setEnabled(context, it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PxlSubscriptionReminderSettings.setEnabled(context, !subscriptionRemindersEnabled)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                Text(
                    text = stringResource(R.string.pxlnet_settings_developer),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.pxlnet_settings_developer_mode)) },
                    supportingContent = { Text(stringResource(R.string.pxlnet_settings_developer_mode_summary)) },
                    leadingContent = {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(
                            checked = developerMode,
                            onCheckedChange = PxlDeveloperMode::setEnabled,
                        )
                    },
                    modifier =
                    Modifier
                        .then(
                            if (developerMode) {
                                Modifier
                            } else {
                                Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            },
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            PxlDeveloperMode.setEnabled(!developerMode)
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                if (developerMode) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pxlnet_update_test_notification_setting)) },
                        supportingContent = { Text(stringResource(R.string.pxlnet_update_test_notification_setting_summary)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            val shown = UpdateNotification.showTest(context)
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (shown) R.string.pxlnet_update_test_sent
                                    else R.string.pxlnet_update_test_permission_needed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.core),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clickable { navController.navigate("settings/core") },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.service),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable { navController.navigate("settings/service") },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.profile_override),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clickable { navController.navigate("settings/profile_override") },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.remote_control),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.SettingsRemote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clickable { navController.navigate("settings/remote_control") },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        // About Section
        Text(
            text = stringResource(R.string.about),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.pxlnet_settings_news),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { PxlLinks.open(context, PxlLinks.TELEGRAM_CHANNEL) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.pxlnet_settings_download),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse(PxlLinks.DOWNLOAD)
                            context.startActivity(intent)
                        },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.pxlnet_settings_source),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                    Modifier
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data =
                                android.net.Uri.parse(PxlLinks.GITHUB)
                            context.startActivity(intent)
                        },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.pxlnet_settings_support),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.SupportAgent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                    Modifier
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse(PxlLinks.TELEGRAM_BOT)
                            context.startActivity(intent)
                        },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.pxlnet_settings_credits)) },
                    supportingContent = {
                        Text(stringResource(R.string.pxlnet_settings_credits_summary))
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clickable { PxlLinks.open(context, PxlLinks.SING_BOX_ANDROID) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
