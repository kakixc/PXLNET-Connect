package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlLocalPreferences
import io.nekohasekai.sfa.utils.PxlGuard
import io.nekohasekai.sfa.utils.PxlQuickTile
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.delay

private val PxlGreen = Color(0xFF277A4A)

private data class ServerChoice(
    val tag: String,
    val type: String,
    val delay: Int? = null,
)

private enum class ConnectionHealth {
    Disconnected,
    Transitioning,
    Checking,
    Online,
    Offline,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
    groupsViewModel: GroupsViewModel? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val resolvedGroupsViewModel = groupsViewModel ?: viewModel<GroupsViewModel>()
    val groupsState by resolvedGroupsViewModel.uiState.collectAsState()
    var showServerPicker by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(PxlLocalPreferences.shouldShowOnboarding(context)) }
    var showAlwaysOnHelp by remember { mutableStateOf(false) }
    var showAccountHelp by remember { mutableStateOf(false) }
    var showGuestBanner by remember { mutableStateOf(true) }
    var guardEnabled by remember { mutableStateOf(PxlLocalPreferences.isGuardEnabled(context)) }
    var lastGuardSource by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serviceStatus) {
        resolvedGroupsViewModel.updateServiceStatus(serviceStatus)
    }
    LaunchedEffect(uiState.telegramLoginError) {
        uiState.telegramLoginError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(uiState.telegramAccountName) {
        if (uiState.telegramAccountName != null) showAccountHelp = false
    }
    val selector = groupsState.groups.firstOrNull { it.tag == "PXLNET" }
        ?: groupsState.groups.firstOrNull { it.selectable }
    val serverChoices = selector?.items
        ?.map { ServerChoice(it.tag, it.type, it.urlTestDelay) }
        ?.takeIf { it.isNotEmpty() }
        ?: uiState.availableServerTags.map { ServerChoice(it, protocolName(it)) }
    val selectedServer = selector?.selected ?: uiState.preferredServerTag
    val selectedItem = serverChoices.firstOrNull { it.tag == selectedServer }
    val hasProfile = uiState.selectedProfileId > 0
    val isTransitioning = serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    var healthCheckTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(serviceStatus, selector?.tag, selectedServer, selectedItem?.delay) {
        healthCheckTimedOut = false
        if (serviceStatus == Status.Started && selector != null && (selectedItem?.delay ?: 0) <= 0) {
            delay(600)
            resolvedGroupsViewModel.urlTest(selector.tag)
            delay(7_000)
            healthCheckTimedOut = true
        }
    }

    val connectionHealth = when {
        isTransitioning -> ConnectionHealth.Transitioning
        serviceStatus != Status.Started -> ConnectionHealth.Disconnected
        (selectedItem?.delay ?: 0) > 0 -> ConnectionHealth.Online
        healthCheckTimedOut -> ConnectionHealth.Offline
        else -> ConnectionHealth.Checking
    }

    LaunchedEffect(connectionHealth, guardEnabled, selector?.tag, selectedServer, serverChoices) {
        if (connectionHealth == ConnectionHealth.Online) {
            lastGuardSource = null
        }
        if (
            connectionHealth == ConnectionHealth.Offline &&
            guardEnabled &&
            selector != null &&
            lastGuardSource != selectedServer
        ) {
            lastGuardSource = selectedServer
            val fallbackTag = PxlGuard.selectFallback(
                selectedServer,
                serverChoices.map { it.tag to it.delay },
            )
            val fallback = serverChoices.firstOrNull { it.tag == fallbackTag }
            if (fallback != null) {
                resolvedGroupsViewModel.selectGroupItem(selector.tag, fallback.tag, closeConnections = true)
                viewModel.selectPreferredServer(fallback.tag)
                Toast.makeText(
                    context,
                    "PXL Guard переключил на ${serverTitle(fallback.tag)}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val importFromClipboard = {
        val value = clipboard.getText()?.text?.trim().orEmpty()
        if (value.startsWith("https://") || value.startsWith("http://")) {
            onOpenNewProfile(NewProfileArgs(importName = "PXLNET", importUrl = value))
        } else {
            Toast.makeText(context, "В буфере нет ссылки подписки", Toast.LENGTH_SHORT).show()
        }
    }

    OverrideTopBar {
        PxlRootTopBar(
            title = "PXLNET Connect",
            subtitle = BuildConfig.VERSION_NAME,
            actions = {
                IconButton(onClick = { showOnboarding = true }) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Как подключиться")
                }
                IconButton(onClick = { onOpenNewProfile(NewProfileArgs()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить подписку")
                }
            },
        )
    }

    if (showOnboarding) {
        PxlOnboardingSheet(
            smartRouting = uiState.smartRoutingEnabled,
            onSmartRoutingChanged = viewModel::setSmartRouting,
            onTelegramLogin = viewModel::startTelegramLogin,
            onImportClipboard = importFromClipboard,
            onOpenVpnSettings = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
            },
            onFinish = {
                PxlLocalPreferences.finishOnboarding(context)
                showOnboarding = false
            },
        )
    }

    if (showAccountHelp) {
        AccountLoginDialog(
            pending = uiState.telegramLoginPending,
            serviceAvailable = uiState.accountServiceAvailable,
            onDismiss = { showAccountHelp = false },
            onLogin = viewModel::startTelegramLogin,
        )
    }

    if (showAlwaysOnHelp) {
        AlwaysOnVpnDialog(
            onDismiss = { showAlwaysOnHelp = false },
            onOpenSettings = {
                showAlwaysOnHelp = false
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
            },
        )
    }

    if (showServerPicker && serverChoices.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showServerPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Состояние PXLNET", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (connectionHealth) {
                            ConnectionHealth.Online -> "Подключение работает"
                            ConnectionHealth.Offline -> "Выбранный сервер не отвечает"
                            ConnectionHealth.Checking -> "Проверяем доступность"
                            ConnectionHealth.Transitioning -> "Меняем состояние VPN"
                            ConnectionHealth.Disconnected -> "VPN выключен · можно проверить задержку"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { selector?.let { resolvedGroupsViewModel.urlTest(it.tag) } },
                    enabled = selector != null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Проверить пинг")
                }
            }
            serverChoices.forEachIndexed { index, item ->
                ServerPickerRow(
                    item = item,
                    selected = item.tag == selectedServer,
                    onClick = {
                        selector?.let { resolvedGroupsViewModel.selectGroupItem(it.tag, item.tag) }
                        viewModel.selectPreferredServer(item.tag)
                        showServerPicker = false
                    },
                )
                if (index != serverChoices.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when (uiState.accountServiceAvailable) {
                                true -> PxlGreen
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            CircleShape,
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Аккаунты и подписки", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (uiState.accountServiceAvailable) {
                            true -> "Сервис доступен"
                            false -> "Сервис сейчас не отвечает · гостевой режим работает"
                            null -> "Проверяем сервис"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Техработы", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Объявления публикуются в @pxlnet_bot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Default.OpenInNew, contentDescription = "Открыть Telegram")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SubscriptionCard(
                profileName = uiState.selectedProfileName,
                summary = SubscriptionInfoStore.summary(context, uiState.selectedProfileId),
                updating = uiState.updatingProfileId != null,
                onRefresh = {
                    uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }?.let(viewModel::updateProfile)
                },
            )
        }

        if (uiState.telegramAccountName != null) {
            item {
                TelegramAccountCard(
                    accountName = uiState.telegramAccountName.orEmpty(),
                    username = uiState.telegramUsername,
                    subscriptionActive = uiState.telegramSubscriptionActive,
                    subscriptionExpiresAt = uiState.telegramSubscriptionExpiresAt,
                    refreshing = uiState.telegramLoginPending,
                    onRefresh = viewModel::refreshTelegramAccount,
                    onRenew = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                    onLogout = viewModel::logoutTelegram,
                )
            }
        } else if (showGuestBanner) {
            item {
                GuestAccountBanner(
                    onClose = { showGuestBanner = false },
                    onLearnMore = { showAccountHelp = true },
                )
            }
        }

        item {
            ConnectionControl(
                serviceStatus = serviceStatus,
                health = connectionHealth,
                delay = selectedItem?.delay,
                enabled = hasProfile && !isTransitioning,
                onClick = viewModel::toggleService,
            )
        }

        if (!hasProfile) {
            item {
                Text(
                    "Сначала добавьте ссылку подписки — серверы появятся автоматически.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }

        item {
            ServerCard(
                serverTag = selectedServer,
                delay = selectedItem?.delay,
                enabled = serverChoices.isNotEmpty(),
                onClick = { if (serverChoices.isNotEmpty()) showServerPicker = true },
            )
        }

        item {
            RoutingCard(
                smartRouting = uiState.smartRoutingEnabled,
                updating = uiState.updatingProfileId != null,
                onChanged = viewModel::setSmartRouting,
            )
        }

        item {
            SafetyCard(
                guardEnabled = guardEnabled,
                onGuardChanged = {
                    guardEnabled = it
                    PxlLocalPreferences.setGuardEnabled(context, it)
                },
                onAlwaysOnHelp = { showAlwaysOnHelp = true },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = importFromClipboard,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Из буфера")
                }
                OutlinedButton(
                    onClick = { onOpenNewProfile(NewProfileArgs()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Ссылка / CFG")
                }
            }
        }

        item {
            QuickTileCard(
                onAdd = {
                    PxlQuickTile.requestAdd(context) { result ->
                        val message = when (result) {
                            PxlQuickTile.Result.Added -> "Плитка PXLNET добавлена"
                            PxlQuickTile.Result.AlreadyAdded -> "Плитка PXLNET уже добавлена"
                            PxlQuickTile.Result.OpenQuickSettings -> "Откройте редактирование шторки и добавьте PXLNET VPN"
                            PxlQuickTile.Result.Rejected -> "Плитка не добавлена"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }

        if (serviceStatus == Status.Started) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard("Приём", uiState.downlink, Modifier.weight(1f))
                    MetricCard("Отдача", uiState.uplink, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GuestAccountBanner(onClose: () -> Unit, onLearnMore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Войдите в аккаунт для полного функционала. Это просто!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Без входа VPN продолжит работать по обычной ссылке подписки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }
            TextButton(onClick = onLearnMore) {
                Text("Узнать, как войти")
            }
        }
    }
}

@Composable
private fun TelegramAccountCard(
    accountName: String,
    username: String?,
    subscriptionActive: Boolean,
    subscriptionExpiresAt: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRenew: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        accountName.ifBlank { username?.let { "@$it" } ?: "Аккаунт PXLNET" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (subscriptionActive) {
                            "Подписка активна${formatSubscriptionExpiry(subscriptionExpiresAt)?.let { " до $it" }.orEmpty()}"
                        } else {
                            "Подписка не активна"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subscriptionActive) PxlGreen else MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить аккаунт и подписку")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onLogout) { Text("Выйти") }
                TextButton(onClick = onRenew) { Text(if (subscriptionActive) "Продлить" else "Активировать") }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    profileName: String?,
    summary: String?,
    updating: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.pxlnet_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    profileName ?: "Подписка не добавлена",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary ?: if (profileName == null) "Добавьте ссылку или CFG" else "Подписка активна",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profileName != null) {
                IconButton(onClick = onRefresh, enabled = !updating) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить подписку")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionControl(
    serviceStatus: Status,
    health: ConnectionHealth,
    delay: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when (health) {
        ConnectionHealth.Online -> "Защищено · ${delay} мс"
        ConnectionHealth.Checking -> "Проверяем интернет…"
        ConnectionHealth.Offline -> "VPN запущен, доступа нет"
        ConnectionHealth.Transitioning -> if (serviceStatus == Status.Starting) "Подключаемся…" else "Отключаемся…"
        ConnectionHealth.Disconnected -> "Подключиться"
    }
    val containerColor = when (health) {
        ConnectionHealth.Online -> PxlGreen
        ConnectionHealth.Offline -> MaterialTheme.colorScheme.errorContainer
        ConnectionHealth.Checking, ConnectionHealth.Transitioning -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionHealth.Disconnected -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (health) {
        ConnectionHealth.Online -> Color.White
        ConnectionHealth.Offline -> MaterialTheme.colorScheme.onErrorContainer
        ConnectionHealth.Checking, ConnectionHealth.Transitioning -> MaterialTheme.colorScheme.onSecondaryContainer
        ConnectionHealth.Disconnected -> MaterialTheme.colorScheme.onPrimary
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(164.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            if (serviceStatus == Status.Starting || serviceStatus == Status.Stopping) {
                CircularProgressIndicator(
                    color = contentColor,
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    if (health == ConnectionHealth.Offline) Icons.Default.CloudOff else Icons.Default.PowerSettingsNew,
                    contentDescription = label,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (health == ConnectionHealth.Offline) {
            Text(
                "Откройте список и выберите другой сервер",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QuickTileCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.DashboardCustomize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Плитка в шторке", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Включайте VPN без открытия приложения",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(8.dp)) {
                Text("Добавить")
            }
        }
    }
}

@Composable
private fun ServerCard(serverTag: String, delay: Int?, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(serverFlag(serverTag), style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text("Сервер", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(serverTitle(serverTag), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Text(formatDelay(delay), style = MaterialTheme.typography.labelMedium, color = delayColor(delay))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun RoutingCard(smartRouting: Boolean, updating: Boolean, onChanged: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Smart Routing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    if (smartRouting) "Локальная сеть и российские домены — напрямую" else "Весь интернет через VPN, локальная сеть доступна",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = smartRouting, onCheckedChange = onChanged, enabled = !updating)
        }
    }
}

@Composable
private fun SafetyCard(
    guardEnabled: Boolean,
    onGuardChanged: (Boolean) -> Unit,
    onAlwaysOnHelp: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("PXL Guard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Переключит на самый быстрый рабочий сервер при сбое",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = guardEnabled, onCheckedChange = onGuardChanged)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAlwaysOnHelp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Always-on VPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Автозапуск и блокировка трафика без VPN настраиваются в Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Открыть инструкцию")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PxlOnboardingSheet(
    smartRouting: Boolean,
    onSmartRoutingChanged: (Boolean) -> Unit,
    onTelegramLogin: () -> Unit,
    onImportClipboard: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val titles = listOf(
        "Добавьте подписку",
        "Выберите режим",
        "Разрешите VPN",
        "Подключитесь",
    )
    ModalBottomSheet(
        onDismissRequest = onFinish,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Шаг ${step + 1} из ${titles.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(titles[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            when (step) {
                0 -> {
                    Text(
                        "Войдите через @pxlnet_bot, чтобы приложение само получало статус и синхронизировало подписку. Либо продолжите в гостевом режиме по ссылке.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onTelegramLogin,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Войти")
                        }
                        OutlinedButton(
                            onClick = onImportClipboard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Из буфера")
                        }
                    }
                }
                1 -> {
                    Text(
                        "Smart Routing оставляет локальную сеть и российские ресурсы напрямую. Полный туннель отправляет остальной трафик через VPN.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSmartRoutingChanged(true) },
                            modifier = Modifier.weight(1f),
                            enabled = !smartRouting,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Smart Routing")
                        }
                        OutlinedButton(
                            onClick = { onSmartRoutingChanged(false) },
                            modifier = Modifier.weight(1f),
                            enabled = smartRouting,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Полный туннель")
                        }
                    }
                }
                2 -> {
                    Text(
                        "При первом нажатии «Подключиться» Android покажет системный запрос. Разрешите PXLNET Connect создать VPN-подключение.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenVpnSettings, shape = RoundedCornerShape(10.dp)) {
                        Text("Открыть настройки VPN")
                    }
                }
                else -> {
                    Text(
                        "Нажмите большую кнопку. Когда проверка покажет задержку, соединение работает. PXL Guard автоматически сменит отказавший сервер.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (step > 0) step-- else onFinish() }) {
                    Text(if (step > 0) "Назад" else "Пропустить")
                }
                Button(
                    onClick = { if (step < titles.lastIndex) step++ else onFinish() },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (step < titles.lastIndex) "Далее" else "Готово")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccountLoginDialog(
    pending: Boolean,
    serviceAvailable: Boolean?,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Аккаунт PXLNET") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Приложение создаст одноразовую сессию и откроет @pxlnet_bot.")
                Text("2. Подтвердите вход в Telegram.")
                Text("3. Вернитесь в PXLNET Connect — статус и действующая подписка загрузятся автоматически.")
                Text(
                    "Пароль Telegram и токен бота приложению не передаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (serviceAvailable == false) {
                    Text(
                        "Сервис аккаунтов сейчас не отвечает. Можно закрыть окно и пользоваться VPN по ссылке подписки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (pending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Ожидаем подтверждение в Telegram…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLogin, enabled = !pending) {
                Text("Войти через Telegram")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}

@Composable
private fun AlwaysOnVpnDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Always-on VPN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Откройте системные настройки VPN.")
                Text("2. Нажмите настройки рядом с PXLNET Connect.")
                Text("3. Включите «Постоянная VPN».")
                Text("4. Для строгой защиты включите «Блокировать соединения без VPN».")
                Text(
                    "Важно: при недоступности всех серверов строгая блокировка полностью отключит интернет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Открыть настройки") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun ServerPickerRow(item: ServerChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(serverFlag(item.tag), style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(serverTitle(item.tag), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(item.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDelay(item.delay), color = delayColor(item.delay), style = MaterialTheme.typography.labelMedium)
        if (selected) {
            Box(Modifier.size(8.dp).background(PxlGreen, CircleShape))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun serverTitle(tag: String): String = when {
    tag.equals("AUTO", true) -> "Автовыбор"
    tag.contains("germany", true) || tag.contains("deutsch", true) -> "Германия · ${protocolName(tag)}"
    tag.contains("finland", true) || tag.contains("finn", true) -> "Финляндия · ${protocolName(tag)}"
    else -> tag
}

private fun protocolName(tag: String): String = when {
    tag.contains("hysteria", true) -> "Hysteria2"
    tag.contains("vless", true) -> "VLESS"
    else -> tag
}

private fun formatSubscriptionExpiry(value: String?): String? {
    val parts = value?.take(10)?.split('-') ?: return null
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}

private fun serverFlag(tag: String): String = when {
    tag.equals("AUTO", true) -> "A"
    tag.contains("germany", true) || tag.contains("deutsch", true) -> "🇩🇪"
    tag.contains("finland", true) || tag.contains("finn", true) -> "🇫🇮"
    else -> "P"
}

private fun formatDelay(delay: Int?): String = if (delay != null && delay > 0) "$delay мс" else "—"

@Composable
private fun delayColor(delay: Int?): Color = when {
    delay == null || delay <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    delay < 150 -> PxlGreen
    delay < 300 -> Color(0xFF9A6B16)
    else -> Color(0xFF9B3D33)
}
