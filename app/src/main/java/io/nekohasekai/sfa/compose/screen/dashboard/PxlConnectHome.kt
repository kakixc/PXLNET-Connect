package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlLocalPreferences
import io.nekohasekai.sfa.utils.PxlGuard
import io.nekohasekai.sfa.utils.PxlMascotSettings
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
    onOpenAppRouting: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
    groupsViewModel: GroupsViewModel? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val resolvedGroupsViewModel = groupsViewModel ?: viewModel<GroupsViewModel>()
    val groupsState by resolvedGroupsViewModel.uiState.collectAsState()
    val mascotEnabled by PxlMascotSettings.enabled.collectAsState()
    val mascotAnimationsEnabled by PxlMascotSettings.animationsEnabled.collectAsState()
    val mascotTipsEnabled by PxlMascotSettings.tipsEnabled.collectAsState()
    var showServerPicker by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(PxlLocalPreferences.shouldShowOnboarding(context)) }
    var showAccountHelp by remember { mutableStateOf(false) }
    var showGuestBanner by remember { mutableStateOf(true) }
    var guardEnabled by remember { mutableStateOf(PxlLocalPreferences.isGuardEnabled(context)) }
    var quickTileAdded by remember { mutableStateOf(PxlLocalPreferences.isQuickTileAdded(context)) }
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
            delay(5_000)
            if ((selectedItem?.delay ?: 0) <= 0) {
                resolvedGroupsViewModel.urlTest(selector.tag)
                delay(4_000)
            }
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
                    context.getString(R.string.pxlnet_guard_switched, serverTitle(context, fallback.tag)),
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
            Toast.makeText(context, R.string.pxlnet_clipboard_no_subscription, Toast.LENGTH_SHORT).show()
        }
    }

    OverrideTopBar {
        PxlRootTopBar(
            title = "PXLNET Connect",
            subtitle = BuildConfig.VERSION_NAME,
            actions = {
                IconButton(onClick = { showOnboarding = true }) {
                    Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.pxlnet_how_to_connect))
                }
                IconButton(onClick = { onOpenNewProfile(NewProfileArgs()) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pxlnet_add_subscription))
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
                    Text(stringResource(R.string.pxlnet_status_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (connectionHealth) {
                            ConnectionHealth.Online -> stringResource(R.string.pxlnet_connection_works)
                            ConnectionHealth.Offline -> stringResource(R.string.pxlnet_server_not_responding)
                            ConnectionHealth.Checking -> stringResource(R.string.pxlnet_checking_availability)
                            ConnectionHealth.Transitioning -> stringResource(R.string.pxlnet_vpn_changing)
                            ConnectionHealth.Disconnected -> stringResource(R.string.pxlnet_vpn_off_ping)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { selector?.let { resolvedGroupsViewModel.urlTest(it.tag) } },
                    enabled = selector != null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pxlnet_check_ping))
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
                accountName = uiState.telegramAccountName
                    ?: uiState.telegramUsername?.let { "@$it" },
                accountActive = uiState.telegramSubscriptionActive,
                accountExpiry = formatPxlAccountExpiry(uiState.telegramSubscriptionExpiresAt),
                updating = uiState.updatingProfileId != null,
                onRefresh = {
                    uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }?.let(viewModel::updateProfile)
                },
            )
        }

        if (uiState.telegramAccountName == null && showGuestBanner) {
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
                showMascot = mascotEnabled,
                animateMascot = mascotAnimationsEnabled,
                showMascotTips = mascotTipsEnabled,
                hasProfile = hasProfile,
                serverName = serverTitle(context, selectedServer),
                onClick = viewModel::toggleService,
            )
        }

        if (!hasProfile) {
            item {
                Text(
                    stringResource(R.string.pxlnet_add_subscription_first),
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
                appRoutingEnabled = Settings.perAppProxyEnabled,
                onClick = onOpenAppRouting,
            )
        }

        item {
            GuardCard(
                guardEnabled = guardEnabled,
                onGuardChanged = {
                    guardEnabled = it
                    PxlLocalPreferences.setGuardEnabled(context, it)
                },
            )
        }

        if (!hasProfile) {
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
                        Text(stringResource(R.string.pxlnet_from_clipboard))
                    }
                    OutlinedButton(
                        onClick = { onOpenNewProfile(NewProfileArgs()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.pxlnet_link_cfg))
                    }
                }
            }
        }

        if (!quickTileAdded) {
            item {
                QuickTileCard(
                    onAdd = {
                        PxlQuickTile.requestAdd(context) { result ->
                            if (result == PxlQuickTile.Result.Added || result == PxlQuickTile.Result.AlreadyAdded) {
                                quickTileAdded = true
                                PxlLocalPreferences.setQuickTileAdded(context, true)
                            }
                            val message = when (result) {
                                PxlQuickTile.Result.Added -> context.getString(R.string.pxlnet_tile_added)
                                PxlQuickTile.Result.AlreadyAdded -> context.getString(R.string.pxlnet_tile_already_added)
                                PxlQuickTile.Result.OpenQuickSettings -> context.getString(R.string.pxlnet_tile_add_manually)
                                PxlQuickTile.Result.Rejected -> context.getString(R.string.pxlnet_tile_not_added)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }

        if (serviceStatus == Status.Started) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(stringResource(R.string.pxlnet_download), uiState.downlink, Modifier.weight(1f))
                    MetricCard(stringResource(R.string.pxlnet_upload), uiState.uplink, Modifier.weight(1f))
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
                        stringResource(R.string.pxlnet_guest_banner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.pxlnet_guest_banner_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }
            TextButton(onClick = onLearnMore) {
                Text(stringResource(R.string.pxlnet_learn_login))
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    profileName: String?,
    summary: String?,
    accountName: String?,
    accountActive: Boolean,
    accountExpiry: String?,
    updating: Boolean,
    onRefresh: () -> Unit,
) {
    val compactSummary = when {
        accountName != null && accountActive && accountExpiry != null -> stringResource(
            R.string.pxlnet_home_account_until,
            accountName,
            accountExpiry,
        )
        accountName != null && accountActive -> stringResource(R.string.pxlnet_home_account_active, accountName)
        accountName != null -> stringResource(R.string.pxlnet_home_account_inactive, accountName)
        else -> summary ?: stringResource(
            if (profileName == null) R.string.pxlnet_add_link_hint else R.string.pxlnet_subscription_active,
        )
    }
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
                    profileName ?: stringResource(R.string.pxlnet_subscription_missing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    compactSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (profileName != null) {
                IconButton(onClick = onRefresh, enabled = !updating) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pxlnet_refresh_subscription))
                    }
                }
            }
        }
    }
}

@Composable
private fun PxlCatCharacter(health: ConnectionHealth, animationsEnabled: Boolean) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val face = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val transition = rememberInfiniteTransition(label = "pix-motion")
    val animatedBob by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pix-bob",
    )
    val animatedTail by transition.animateFloat(
        initialValue = -8f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pix-tail",
    )
    val bob = if (animationsEnabled && health != ConnectionHealth.Offline) animatedBob else 0f
    val tail = if (animationsEnabled) animatedTail else 0f
    Canvas(
        modifier = Modifier
            .size(width = 72.dp, height = 86.dp)
            .graphicsLayer { translationY = bob },
    ) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = ink,
            startAngle = -45f + tail,
            sweepAngle = 155f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.49f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.43f),
            style = Stroke(stroke * 4f, cap = StrokeCap.Round),
        )
        drawOval(
            color = face,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.43f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.50f, size.height * 0.48f),
        )
        drawOval(
            color = ink,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.43f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.50f, size.height * 0.48f),
            style = Stroke(stroke),
        )
        val earLeft = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.27f)
            lineTo(size.width * 0.24f, size.height * 0.04f)
            lineTo(size.width * 0.43f, size.height * 0.20f)
            close()
        }
        val earRight = Path().apply {
            moveTo(size.width * 0.57f, size.height * 0.20f)
            lineTo(size.width * 0.76f, size.height * 0.04f)
            lineTo(size.width * 0.80f, size.height * 0.27f)
            close()
        }
        drawPath(earLeft, face)
        drawPath(earRight, face)
        drawPath(earLeft, ink, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(earRight, ink, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawOval(
            color = face,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.46f),
        )
        drawOval(
            color = ink,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.46f),
            style = Stroke(stroke),
        )

        val leftEye = androidx.compose.ui.geometry.Offset(size.width * 0.37f, size.height * 0.35f)
        val rightEye = androidx.compose.ui.geometry.Offset(size.width * 0.63f, size.height * 0.35f)
        if (health == ConnectionHealth.Offline) {
            drawLine(ink, leftEye.copy(x = leftEye.x - stroke), leftEye.copy(x = leftEye.x + stroke), stroke, StrokeCap.Round)
            drawLine(ink, rightEye.copy(x = rightEye.x - stroke), rightEye.copy(x = rightEye.x + stroke), stroke, StrokeCap.Round)
        } else {
            drawCircle(ink, radius = stroke * 1.15f, center = leftEye)
            if (health == ConnectionHealth.Transitioning) {
                drawLine(ink, rightEye.copy(x = rightEye.x - stroke), rightEye.copy(x = rightEye.x + stroke), stroke, StrokeCap.Round)
            } else {
                drawCircle(ink, radius = stroke * 1.15f, center = rightEye)
            }
        }

        val nose = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.43f)
        drawCircle(ink, radius = stroke * 0.9f, center = nose)
        drawLine(ink, nose, nose.copy(y = nose.y + stroke * 2.2f), stroke, StrokeCap.Round)
        drawArc(
            color = ink,
            startAngle = 5f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.42f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.10f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = ink,
            startAngle = 5f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.42f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.10f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        listOf(0.43f, 0.48f).forEach { y ->
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * y),
                androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * (y - 0.02f)),
                stroke * 0.75f,
                StrokeCap.Round,
            )
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * (y - 0.02f)),
                androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * y),
                stroke * 0.75f,
                StrokeCap.Round,
            )
        }
        drawLine(
            ink,
            androidx.compose.ui.geometry.Offset(size.width * 0.37f, size.height * 0.89f),
            androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.97f),
            stroke * 2f,
            StrokeCap.Round,
        )
        drawLine(
            ink,
            androidx.compose.ui.geometry.Offset(size.width * 0.63f, size.height * 0.89f),
            androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.97f),
            stroke * 2f,
            StrokeCap.Round,
        )
        val pixel = size.width * 0.045f
        listOf(-1 to 0, 0 to 0, 1 to 0, -1 to 1, 1 to 1, 0 to 2).forEach { (x, y) ->
            drawRect(
                color = ink,
                topLeft = androidx.compose.ui.geometry.Offset(
                    size.width * 0.50f + x * pixel - pixel / 2,
                    size.height * 0.61f + y * pixel,
                ),
                size = androidx.compose.ui.geometry.Size(pixel, pixel),
            )
        }
    }
}

@Composable
private fun ConnectionControl(
    serviceStatus: Status,
    health: ConnectionHealth,
    delay: Int?,
    enabled: Boolean,
    showMascot: Boolean,
    animateMascot: Boolean,
    showMascotTips: Boolean,
    hasProfile: Boolean,
    serverName: String,
    onClick: () -> Unit,
) {
    val label = when (health) {
        ConnectionHealth.Online -> stringResource(R.string.pxlnet_protected_delay, delay ?: 0)
        ConnectionHealth.Checking -> stringResource(R.string.pxlnet_checking_internet)
        ConnectionHealth.Offline -> stringResource(R.string.pxlnet_vpn_no_access)
        ConnectionHealth.Transitioning -> stringResource(
            if (serviceStatus == Status.Starting) R.string.pxlnet_connecting else R.string.pxlnet_disconnecting,
        )
        ConnectionHealth.Disconnected -> stringResource(R.string.pxlnet_connect)
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
    val mascotMessage = when {
        !hasProfile -> stringResource(R.string.pxlnet_mascot_no_subscription)
        health == ConnectionHealth.Online -> stringResource(R.string.pxlnet_mascot_online, serverName, delay ?: 0)
        health == ConnectionHealth.Offline -> stringResource(R.string.pxlnet_mascot_offline)
        health == ConnectionHealth.Checking -> stringResource(R.string.pxlnet_mascot_checking)
        health == ConnectionHealth.Transitioning -> stringResource(R.string.pxlnet_mascot_transitioning)
        else -> stringResource(R.string.pxlnet_mascot_ready)
    }
    var mascotTipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(health, showMascotTips, hasProfile) {
        mascotTipVisible = false
        if (showMascot && showMascotTips) {
            delay(250)
            mascotTipVisible = true
            delay(4_000)
            mascotTipVisible = false
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
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
            if (showMascot) {
                Column(
                    modifier = Modifier.width(88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedVisibility(
                        visible = mascotTipVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                mascotMessage,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    PxlCatCharacter(health, animateMascot)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (health == ConnectionHealth.Offline) {
            Text(
                stringResource(R.string.pxlnet_choose_another_server),
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
                Text(stringResource(R.string.pxlnet_quick_tile_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.pxlnet_quick_tile_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.pxlnet_add))
            }
        }
    }
}

@Composable
private fun ServerCard(serverTag: String, delay: Int?, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
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
            ServerLocationIcon(serverTag)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pxlnet_server_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(serverTitle(context, serverTag), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Text(formatDelay(context, delay), style = MaterialTheme.typography.labelMedium, color = delayColor(delay))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun RoutingCard(
    smartRouting: Boolean,
    appRoutingEnabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
            Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.pxlnet_app_routing_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        appRoutingEnabled -> stringResource(R.string.pxlnet_route_selected_title)
                        smartRouting -> stringResource(R.string.pxlnet_route_smart_title)
                        else -> stringResource(R.string.pxlnet_route_all_title)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun GuardCard(
    guardEnabled: Boolean,
    onGuardChanged: (Boolean) -> Unit,
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
                    stringResource(R.string.pxlnet_guard_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = guardEnabled, onCheckedChange = onGuardChanged)
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
        stringResource(R.string.pxlnet_onboarding_add),
        stringResource(R.string.pxlnet_onboarding_mode),
        stringResource(R.string.pxlnet_onboarding_permission),
        stringResource(R.string.pxlnet_onboarding_connect),
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
                stringResource(R.string.pxlnet_onboarding_step, step + 1, titles.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(titles[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            when (step) {
                0 -> {
                    Text(
                        stringResource(R.string.pxlnet_onboarding_subscription_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onTelegramLogin,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.pxlnet_login_telegram))
                        }
                        OutlinedButton(
                            onClick = onImportClipboard,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.pxlnet_from_clipboard))
                        }
                    }
                }
                1 -> {
                    Text(
                        stringResource(R.string.pxlnet_onboarding_routing_text),
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
                            Text(stringResource(R.string.pxlnet_full_tunnel))
                        }
                    }
                }
                2 -> {
                    Text(
                        stringResource(R.string.pxlnet_onboarding_permission_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenVpnSettings, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(R.string.pxlnet_open_vpn_settings))
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.pxlnet_onboarding_finish_text),
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
                    Text(stringResource(if (step > 0) R.string.pxlnet_back else R.string.pxlnet_skip))
                }
                Button(
                    onClick = { if (step < titles.lastIndex) step++ else onFinish() },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(if (step < titles.lastIndex) R.string.pxlnet_next else R.string.pxlnet_done))
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
        title = { Text(stringResource(R.string.pxlnet_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pxlnet_login_step_session))
                Text(stringResource(R.string.pxlnet_login_step_confirm))
                Text(stringResource(R.string.pxlnet_login_step_return))
                Text(
                    stringResource(R.string.pxlnet_login_security),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (serviceAvailable == false) {
                    Text(
                        stringResource(R.string.pxlnet_login_service_offline),
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
                        Text(stringResource(R.string.pxlnet_login_waiting_telegram))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLogin, enabled = !pending) {
                Text(stringResource(R.string.pxlnet_login_telegram))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pxlnet_later)) }
        },
    )
}

@Composable
private fun ServerPickerRow(item: ServerChoice, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerLocationIcon(item.tag)
        Column(modifier = Modifier.weight(1f)) {
            Text(serverTitle(context, item.tag), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(item.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDelay(context, item.delay), color = delayColor(item.delay), style = MaterialTheme.typography.labelMedium)
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

private fun serverTitle(context: Context, tag: String): String = when {
    tag.equals("AUTO", true) -> context.getString(R.string.pxlnet_server_auto)
    tag.contains("germany", true) || tag.contains("deutsch", true) ->
        context.getString(R.string.pxlnet_server_germany, protocolName(tag))
    tag.contains("finland", true) || tag.contains("finn", true) ->
        context.getString(R.string.pxlnet_server_finland, protocolName(tag))
    else -> tag
}

private fun protocolName(tag: String): String = when {
    tag.contains("hysteria", true) -> "Hysteria2"
    tag.contains("vless", true) -> "VLESS"
    else -> tag
}

@Composable
private fun ServerLocationIcon(tag: String) {
    when {
        tag.equals("AUTO", true) -> {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        tag.contains("germany", true) || tag.contains("deutsch", true) -> {
            Column(
                modifier = Modifier
                    .size(width = 32.dp, height = 22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
            ) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black))
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFDD0000)))
                Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFFFCE00)))
            }
        }
        tag.contains("finland", true) || tag.contains("finn", true) -> {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(5.dp)
                        .align(Alignment.Center)
                        .background(Color(0xFF003580)),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.Center)
                        .background(Color(0xFF003580)),
                )
            }
        }
        else -> {
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

private fun formatPxlAccountExpiry(value: String?): String? {
    val parts = value?.take(10)?.split('-') ?: return null
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}

private fun formatDelay(context: Context, delay: Int?): String =
    if (delay != null && delay > 0) context.getString(R.string.pxlnet_delay_ms, delay) else "—"

@Composable
private fun delayColor(delay: Int?): Color = when {
    delay == null || delay <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    delay < 150 -> PxlGreen
    delay < 300 -> Color(0xFF9A6B16)
    else -> Color(0xFF9B3D33)
}
