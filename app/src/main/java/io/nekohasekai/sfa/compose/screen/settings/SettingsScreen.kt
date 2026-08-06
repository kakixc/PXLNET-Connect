package io.nekohasekai.sfa.compose.screen.settings

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
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.utils.PxlDeveloperMode
import io.nekohasekai.sfa.utils.PxlDiagnostics
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlMascotSettings
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
    val scope = rememberCoroutineScope()
    var showAlwaysOnHelp by remember { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<PxlSupportReport?>(null) }
    var checkingDiagnostics by remember { mutableStateOf(false) }
    val developerMode by PxlDeveloperMode.enabled.collectAsState()
    val mascotEnabled by PxlMascotSettings.enabled.collectAsState()
    val mascotAnimationsEnabled by PxlMascotSettings.animationsEnabled.collectAsState()
    val mascotTipsEnabled by PxlMascotSettings.tipsEnabled.collectAsState()
    val subscriptionRemindersEnabled by PxlSubscriptionReminderSettings.enabled.collectAsState()
    val hasUpdate by UpdateState.hasUpdate

    if (showAlwaysOnHelp) {
        AlertDialog(
            onDismissRequest = { showAlwaysOnHelp = false },
            title = { Text("Always-on VPN") },
            text = {
                Column {
                    Text("Откройте VPN → PXLNET Connect и включите «Постоянная VPN».")
                    Spacer(Modifier.height(8.dp))
                    Text("Опция «Блокировать соединения без VPN» не даст приложениям выйти в интернет при обрыве туннеля.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAlwaysOnHelp = false
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    },
                ) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showAlwaysOnHelp = false }) { Text("Закрыть") }
            },
        )
    }

    diagnosticReport?.let { report ->
        AlertDialog(
            onDismissRequest = { diagnosticReport = null },
            title = { Text("Результат проверки") },
            text = {
                Column {
                    Text(report.summary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Перед отправкой приложение удалит UUID, токены, IP-адреса и адреса серверов.",
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
                ) { Text("Отправить отчёт") }
            },
            dismissButton = {
                TextButton(onClick = { diagnosticReport = null }) { Text("Закрыть") }
            },
        )
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "Безопасность PXLNET",
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
                headlineContent = { Text("Always-on VPN и блокировка трафика") },
                supportingContent = { Text("Системная защита Android при перезапуске и обрыве") },
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
        }

        Text(
            text = "Помощь",
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
                headlineContent = { Text("Диагностика и помощь") },
                supportingContent = {
                    Text(
                        if (checkingDiagnostics) {
                            "Проверяем последние события…"
                        } else {
                            "Найти типовую ошибку и подготовить отчёт для @pxlnet_bot"
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
                            diagnosticReport = PxlDiagnostics.inspect()
                            checkingDiagnostics = false
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        // General Settings Group
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
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.title_app_settings),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (hasUpdate) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable { navController.navigate("settings/app") },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.pxlnet_mascot_setting_title)) },
                    supportingContent = { Text(stringResource(R.string.pxlnet_mascot_setting_description)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = mascotEnabled,
                            onCheckedChange = PxlMascotSettings::setEnabled,
                        )
                    },
                    modifier = Modifier.clickable { PxlMascotSettings.setEnabled(!mascotEnabled) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                if (mascotEnabled) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pxlnet_mascot_animations_title)) },
                        supportingContent = { Text(stringResource(R.string.pxlnet_mascot_animations_description)) },
                        leadingContent = {
                            Icon(Icons.Outlined.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = mascotAnimationsEnabled,
                                onCheckedChange = PxlMascotSettings::setAnimationsEnabled,
                            )
                        },
                        modifier = Modifier.clickable {
                            PxlMascotSettings.setAnimationsEnabled(!mascotAnimationsEnabled)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pxlnet_mascot_tips_title)) },
                        supportingContent = { Text(stringResource(R.string.pxlnet_mascot_tips_description)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = mascotTipsEnabled,
                                onCheckedChange = PxlMascotSettings::setTipsEnabled,
                            )
                        },
                        modifier = Modifier.clickable { PxlMascotSettings.setTipsEnabled(!mascotTipsEnabled) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }

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
                        PxlSubscriptionReminderSettings.setEnabled(context, !subscriptionRemindersEnabled)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                ListItem(
                    headlineContent = { Text("Режим разработчика") },
                    supportingContent = { Text("Показывает Логи, Инструменты и расширенные настройки") },
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
                        .clickable { PxlDeveloperMode.setEnabled(!developerMode) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                if (developerMode) {
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
                            "Новости · @pxlnet",
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
                            "Скачать PXLNET Connect",
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
                            "Исходный код PXLNET",
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
                            "Поддержка · @pxlnet_bot",
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
                    headlineContent = { Text("Основа и лицензия") },
                    supportingContent = {
                        Text("Форк sing-box for Android · UX вдохновлён Happ · GPLv3")
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
