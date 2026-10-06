package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import io.nekohasekai.sfa.compose.component.PxlSwitch as Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.component.PxlCatEasterEggDialog
import io.nekohasekai.sfa.compose.component.rememberPxlCatEasterEggState
import io.nekohasekai.sfa.compose.theme.performPxlConfirmation
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlLocalPreferences
import io.nekohasekai.sfa.utils.PxlLatencySource
import io.nekohasekai.sfa.utils.PxlServerLatencyProbe
import io.nekohasekai.sfa.utils.PxlGuard
import io.nekohasekai.sfa.utils.PxlMascotSettings
import io.nekohasekai.sfa.utils.PxlQuickTile
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val PxlGreen = Color(0xFF277A4A)

private data class ServerChoice(
    val tag: String,
    val type: String,
    val delay: Int? = null,
)

private sealed interface ServerProbeDisplay {
    data object Loading : ServerProbeDisplay
    data object Unavailable : ServerProbeDisplay
    data class Measured(val milliseconds: Int) : ServerProbeDisplay
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
    val catEasterEgg = rememberPxlCatEasterEggState()
    val hapticFeedback = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val resolvedGroupsViewModel = groupsViewModel ?: viewModel<GroupsViewModel>()
    val groupsState by resolvedGroupsViewModel.uiState.collectAsState()
    val mascotEnabled by PxlMascotSettings.enabled.collectAsState()
    val mascotAnimationsEnabled by PxlMascotSettings.animationsEnabled.collectAsState()
    val mascotTipsEnabled by PxlMascotSettings.tipsEnabled.collectAsState()
    var showServerPicker by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(PxlLocalPreferences.shouldShowOnboarding(context)) }
    var showAccountHelp by remember { mutableStateOf(false) }
    var showXhttpDetails by remember { mutableStateOf(false) }
    var showGuestBanner by remember { mutableStateOf(true) }
    var guardEnabled by remember { mutableStateOf(PxlLocalPreferences.isGuardEnabled(context)) }
    var quickTileAdded by remember { mutableStateOf(PxlLocalPreferences.isQuickTileAdded(context)) }
    var lastGuardSource by remember { mutableStateOf<String?>(null) }
    var pixTapCount by remember { mutableIntStateOf(0) }
    var latencySource by remember { mutableStateOf(PxlLocalPreferences.latencySource(context)) }
    var probeRevision by remember { mutableIntStateOf(0) }
    val serverProbeResults = remember { mutableStateMapOf<String, ServerProbeDisplay>() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        latencySource = PxlLocalPreferences.latencySource(context)
    }

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
        ?: uiState.availableServerTags.map { ServerChoice(it, type = "") }
    val automaticChoices = serverChoices.filter { serverRegion(it.tag) == ServerRegion.AUTO }
    val manualChoices = serverChoices.filter { serverRegion(it.tag) != ServerRegion.AUTO }
    val selectedServer = selector?.selected ?: uiState.preferredServerTag
    val selectedItem = serverChoices.firstOrNull { it.tag == selectedServer }
    val hasProfile = uiState.selectedProfileId > 0
    var healthCheckTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(serviceStatus, selectedServer, showServerPicker, latencySource, probeRevision, uiState.selectedProfileId, manualChoices.map { it.tag }) {
        serverProbeResults.clear()
        if (latencySource != PxlLatencySource.VPN_SERVER || !hasProfile ||
            serviceStatus !in setOf(Status.Started, Status.Stopped)) return@LaunchedEffect
        val tags = if (showServerPicker) manualChoices.map { it.tag } else listOfNotNull(
            selectedServer.takeIf { tag -> manualChoices.any { it.tag == tag } },
        )
        // A pre-connect probe creates a temporary outbound-only core. Keep it
        // sequential so opening the picker cannot allocate several cores.
        val limiter = Semaphore(if (serviceStatus == Status.Started) 2 else 1)
        coroutineScope {
            tags.distinct().forEach { tag ->
                serverProbeResults[tag] = ServerProbeDisplay.Loading
                launch {
                    val delay = limiter.withPermit {
                        if (serviceStatus == Status.Started) PxlServerLatencyProbe.measure(tag)
                        else PxlServerLatencyProbe.measureBeforeStart(uiState.selectedProfileId, tag)
                    }
                    serverProbeResults[tag] = delay?.let(ServerProbeDisplay::Measured) ?: ServerProbeDisplay.Unavailable
                }
            }
        }
    }

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

    val reachability = when {
        serviceStatus != Status.Started || selectedItem == null -> ConnectionReachability.Unknown
        (selectedItem.delay ?: 0) > 0 -> ConnectionReachability.Reachable
        healthCheckTimedOut -> ConnectionReachability.Unreachable
        else -> ConnectionReachability.Checking
    }
    val connectionState = connectionPresentationState(
        serviceStatus = serviceStatus,
        isSwitchingServer = uiState.switchingServerTag != null,
        reachability = if (uiState.serverSwitchFailed) ConnectionReachability.Unreachable else reachability,
    )
    val selectedProbe = serverProbeResults[selectedServer]
    val selectedDisplayedDelay = if (latencySource == PxlLatencySource.VPN_SERVER) {
        (selectedProbe as? ServerProbeDisplay.Measured)?.milliseconds
    } else {
        selectedItem?.delay
    }

    LaunchedEffect(reachability, guardEnabled, selector?.tag, selectedServer, serverChoices) {
        if (reachability == ConnectionReachability.Reachable) {
            lastGuardSource = null
        }
        if (
            reachability == ConnectionReachability.Unreachable &&
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
                    context.getString(R.string.pxlnet_guard_switched, serverTitle(context, fallback.tag, fallback.type)),
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
            onTitleClick = { catEasterEgg.tap(context) },
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

    PxlCatEasterEggDialog(catEasterEgg)

    if (showOnboarding) {
        PxlOnboardingSheet(
            smartRouting = uiState.smartRoutingEnabled,
            onSmartRoutingChanged = viewModel::setSmartRouting,
            onTelegramLogin = viewModel::startTelegramLogin,
            onImportClipboard = importFromClipboard,
            onAddLink = { onOpenNewProfile(NewProfileArgs()) },
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

    if (showXhttpDetails) {
        AlertDialog(
            onDismissRequest = { showXhttpDetails = false },
            title = { Text(stringResource(R.string.pxlnet_xhttp_details_title)) },
            text = { Text(stringResource(R.string.pxlnet_xhttp_nodes_skipped, uiState.skippedXhttpCount)) },
            confirmButton = {
                TextButton(onClick = { showXhttpDetails = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showServerPicker && serverChoices.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showServerPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
          Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pxlnet_choose_server_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (connectionState) {
                            ConnectionPresentationState.Connected -> when (reachability) {
                                ConnectionReachability.Reachable -> stringResource(R.string.pxlnet_connection_works)
                                ConnectionReachability.Checking -> stringResource(R.string.pxlnet_checking_availability)
                                else -> stringResource(R.string.pxlnet_vpn_changing)
                            }
                            ConnectionPresentationState.Error -> stringResource(R.string.pxlnet_server_not_responding)
                            ConnectionPresentationState.Switching -> stringResource(R.string.pxlnet_vpn_changing)
                            ConnectionPresentationState.Connecting,
                            ConnectionPresentationState.Disconnecting -> stringResource(R.string.pxlnet_vpn_changing)
                            ConnectionPresentationState.Disconnected -> stringResource(R.string.pxlnet_vpn_off_ping)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        if (latencySource == PxlLatencySource.VPN_SERVER) {
                            probeRevision++
                        } else {
                            selector?.let { resolvedGroupsViewModel.urlTest(it.tag) }
                        }
                    },
                    enabled = if (latencySource == PxlLatencySource.VPN_SERVER) {
                        serviceStatus in setOf(Status.Started, Status.Stopped) && hasProfile && manualChoices.isNotEmpty()
                    } else {
                        selector != null
                    },
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(
                            if (latencySource == PxlLatencySource.VPN_SERVER) {
                                R.string.pxlnet_check_server_delay
                            } else {
                                R.string.pxlnet_check_website_delay
                            },
                        ),
                    )
                }
            }
            Text(
                stringResource(
                    if (latencySource == PxlLatencySource.VPN_SERVER) {
                        R.string.pxlnet_latency_server_title
                    } else {
                        R.string.pxlnet_website_delay_label
                    },
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            @Composable fun choiceRow(item: ServerChoice) {
                val probe = serverProbeResults[item.tag]
                ServerPickerRow(
                    item = item,
                    selected = item.tag == selectedServer,
                    delay = if (latencySource == PxlLatencySource.VPN_SERVER) {
                        (probe as? ServerProbeDisplay.Measured)?.milliseconds
                    } else {
                        item.delay
                    },
                    loading = latencySource == PxlLatencySource.VPN_SERVER && probe == ServerProbeDisplay.Loading,
                ) {
                    hapticFeedback.performPxlConfirmation()
                    viewModel.selectPreferredServer(item.tag)
                    showServerPicker = false
                }
            }
            if (automaticChoices.isNotEmpty()) {
                ServerPickerSectionTitle(stringResource(R.string.pxlnet_server_section_auto))
                if (latencySource == PxlLatencySource.VPN_SERVER) {
                    Text(
                        stringResource(R.string.pxlnet_latency_auto_explanation),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                automaticChoices.forEach { choiceRow(it) }
            }
            if (manualChoices.isNotEmpty()) {
                ServerPickerSectionTitle(stringResource(R.string.pxlnet_server_section_manual))
                manualChoices.forEachIndexed { index, item ->
                    choiceRow(item)
                    if (index != manualChoices.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
          }
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
        if (hasProfile || uiState.hasTelegramSession) {
            item {
                AccessStrip(
                    profileName = uiState.selectedProfileName,
                    summary = SubscriptionInfoStore.summary(context, uiState.selectedProfileId),
                    accountName = uiState.telegramUsername?.let { "@$it" } ?: uiState.telegramAccountName,
                    hasAccountSession = uiState.hasTelegramSession,
                    accountVerified = uiState.accountLastVerifiedAt != null,
                    accountServiceAvailable = uiState.accountServiceAvailable,
                    accountActive = uiState.telegramSubscriptionActive,
                    accountExpiry = formatPxlAccountExpiry(uiState.telegramSubscriptionExpiresAt),
                    updating = uiState.updatingProfileId != null || uiState.telegramLoginPending,
                    onRefresh = {
                        if (uiState.hasTelegramSession) {
                            viewModel.refreshTelegramAccount()
                        } else {
                            uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }?.let(viewModel::updateProfile)
                        }
                    },
                )
            }
        }
        item {
            ConnectionControl(
                serviceStatus = serviceStatus,
                state = connectionState,
                reachability = reachability,
                delay = selectedDisplayedDelay,
                enabled = hasProfile && connectionState !in setOf(
                    ConnectionPresentationState.Connecting,
                    ConnectionPresentationState.Disconnecting,
                    ConnectionPresentationState.Switching,
                ),
                showMascot = mascotEnabled,
                animateMascot = mascotAnimationsEnabled,
                showMascotTips = mascotTipsEnabled,
                hasProfile = hasProfile,
                onMascotTap = {
                    pixTapCount++
                    if (PxlLocalPreferences.isCosmosUnlocked(context)) {
                        if (pixTapCount >= 5) {
                            pixTapCount = 0
                            Toast.makeText(context, R.string.pxlnet_cosmos_already_unlocked, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (pixTapCount >= 5) {
                            PxlLocalPreferences.unlockCosmos(context)
                            pixTapCount = 0
                            Toast.makeText(context, R.string.pxlnet_cosmos_unlocked, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onClick = {
                    hapticFeedback.performPxlConfirmation()
                    viewModel.toggleService()
                },
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
                serverType = selectedItem?.type.orEmpty(),
                delay = selectedDisplayedDelay,
                loading = latencySource == PxlLatencySource.VPN_SERVER && selectedProbe == ServerProbeDisplay.Loading,
                latencySource = latencySource,
                enabled = serverChoices.isNotEmpty(),
                onClick = { if (serverChoices.isNotEmpty()) showServerPicker = true },
            )
        }

        if (uiState.skippedXhttpCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showXhttpDetails = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.pxlnet_xhttp_summary, uiState.skippedXhttpCount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.pxlnet_xhttp_more),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (uiState.accountServiceAvailable == false) {
            item {
                Text(
                    stringResource(if (hasProfile) R.string.pxlnet_saved_profile_offline else R.string.pxlnet_first_setup_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
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
private fun AccessStrip(
    profileName: String?,
    summary: String?,
    accountName: String?,
    hasAccountSession: Boolean,
    accountVerified: Boolean,
    accountServiceAvailable: Boolean?,
    accountActive: Boolean,
    accountExpiry: String?,
    updating: Boolean,
    onRefresh: () -> Unit,
) {
    val accessSummary = when {
        hasAccountSession && !accountVerified -> stringResource(R.string.pxlnet_account_status_unknown)
        hasAccountSession && accountActive && accountExpiry != null ->
            stringResource(R.string.pxlnet_subscription_active_until, accountExpiry)
        hasAccountSession && accountActive -> stringResource(R.string.pxlnet_subscription_active)
        hasAccountSession -> stringResource(R.string.pxlnet_subscription_inactive)
        summary != null -> stringResource(R.string.pxlnet_link_summary, summary)
        else -> stringResource(R.string.pxlnet_access_by_link)
    }
    val displayedSummary = if (hasAccountSession && accountVerified && accountServiceAvailable == false) {
        stringResource(R.string.pxlnet_account_last_known, accessSummary)
    } else {
        accessSummary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(9.dp).background(
                    if (hasAccountSession && accountVerified && !accountActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    CircleShape,
                ),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (hasAccountSession && accountName != null) "PXLNET · $accountName" else profileName ?: "PXLNET",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displayedSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (profileName != null || hasAccountSession) {
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
private fun PxlCatCharacter(state: ConnectionPresentationState, animationsEnabled: Boolean, onTap: () -> Unit) {
    val ink = MaterialTheme.colorScheme.primary
    val face = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val interactionSource = remember { MutableInteractionSource() }
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
    val bob = if (animationsEnabled && state != ConnectionPresentationState.Error) animatedBob else 0f
    val tail = if (animationsEnabled) animatedTail else 0f
    Canvas(
        modifier = Modifier
            .size(width = 72.dp, height = 86.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = stringResource(R.string.pxlnet_mascot_tap),
                onClick = onTap,
            )
            .graphicsLayer { translationY = bob },
    ) {
        val stroke = 2.dp.toPx()
        // Separate silhouettes keep the head, body and tail from drawing through each other.
        val tailPath = Path().apply {
            moveTo(size.width * 0.77f, size.height * 0.79f)
            cubicTo(
                size.width * 0.97f, size.height * 0.86f,
                size.width * 1.02f, size.height * 0.66f,
                size.width * (0.92f + tail * 0.002f), size.height * 0.58f,
            )
        }
        drawPath(tailPath, ink, style = Stroke(stroke * 4f, cap = StrokeCap.Round))
        val bodyPath = Path().apply {
            moveTo(size.width * 0.29f, size.height * 0.61f)
            cubicTo(size.width * 0.21f, size.height * 0.72f, size.width * 0.26f, size.height * 0.88f,
                size.width * 0.48f, size.height * 0.91f)
            cubicTo(size.width * 0.72f, size.height * 0.92f, size.width * 0.79f, size.height * 0.75f,
                size.width * 0.71f, size.height * 0.61f)
        }
        drawPath(bodyPath, ink, style = Stroke(stroke, cap = StrokeCap.Round))
        val headPath = Path().apply {
            moveTo(size.width * 0.19f, size.height * 0.29f)
            lineTo(size.width * 0.24f, size.height * 0.04f)
            lineTo(size.width * 0.40f, size.height * 0.19f)
            quadraticTo(size.width * 0.50f, size.height * 0.16f, size.width * 0.60f, size.height * 0.19f)
            lineTo(size.width * 0.76f, size.height * 0.04f)
            lineTo(size.width * 0.81f, size.height * 0.29f)
            cubicTo(size.width * 0.90f, size.height * 0.47f, size.width * 0.77f, size.height * 0.61f,
                size.width * 0.50f, size.height * 0.61f)
            cubicTo(size.width * 0.23f, size.height * 0.61f, size.width * 0.10f, size.height * 0.47f,
                size.width * 0.19f, size.height * 0.29f)
            close()
        }
        drawPath(headPath, face)
        drawPath(headPath, ink, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))

        val leftEye = androidx.compose.ui.geometry.Offset(size.width * 0.37f, size.height * 0.35f)
        val rightEye = androidx.compose.ui.geometry.Offset(size.width * 0.63f, size.height * 0.35f)
        if (state == ConnectionPresentationState.Error) {
            drawLine(ink, leftEye.copy(x = leftEye.x - stroke), leftEye.copy(x = leftEye.x + stroke), stroke, StrokeCap.Round)
            drawLine(ink, rightEye.copy(x = rightEye.x - stroke), rightEye.copy(x = rightEye.x + stroke), stroke, StrokeCap.Round)
        } else {
            drawCircle(ink, radius = stroke * 1.15f, center = leftEye)
            if (state in setOf(ConnectionPresentationState.Connecting, ConnectionPresentationState.Switching, ConnectionPresentationState.Disconnecting)) {
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
                androidx.compose.ui.geometry.Offset(size.width * 0.04f, size.height * y),
                androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * (y - 0.01f)),
                stroke * 0.75f,
                StrokeCap.Round,
            )
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * (y - 0.01f)),
                androidx.compose.ui.geometry.Offset(size.width * 0.96f, size.height * y),
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
    state: ConnectionPresentationState,
    reachability: ConnectionReachability,
    delay: Int?,
    enabled: Boolean,
    showMascot: Boolean,
    animateMascot: Boolean,
    showMascotTips: Boolean,
    hasProfile: Boolean,
    onMascotTap: () -> Unit,
    onClick: () -> Unit,
) {
    val label = when (state) {
        ConnectionPresentationState.Connected -> when (reachability) {
            ConnectionReachability.Reachable -> if (delay != null && delay > 0) {
                stringResource(R.string.pxlnet_protected_delay, delay)
            } else {
                stringResource(R.string.pxlnet_connection_works)
            }
            ConnectionReachability.Checking -> stringResource(R.string.pxlnet_checking_internet)
            else -> stringResource(R.string.pxlnet_vpn_changing)
        }
        ConnectionPresentationState.Error -> stringResource(R.string.pxlnet_vpn_no_access)
        ConnectionPresentationState.Connecting -> stringResource(R.string.pxlnet_connecting)
        ConnectionPresentationState.Switching -> stringResource(R.string.pxlnet_vpn_changing)
        ConnectionPresentationState.Disconnecting -> stringResource(R.string.pxlnet_disconnecting)
        ConnectionPresentationState.Disconnected -> stringResource(R.string.pxlnet_connect)
    }
    val containerColor = when (state) {
        ConnectionPresentationState.Connected -> PxlGreen
        ConnectionPresentationState.Error -> MaterialTheme.colorScheme.errorContainer
        ConnectionPresentationState.Connecting,
        ConnectionPresentationState.Switching,
        ConnectionPresentationState.Disconnecting -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionPresentationState.Disconnected -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (state) {
        ConnectionPresentationState.Connected -> Color.White
        ConnectionPresentationState.Error -> MaterialTheme.colorScheme.onErrorContainer
        ConnectionPresentationState.Connecting,
        ConnectionPresentationState.Switching,
        ConnectionPresentationState.Disconnecting -> MaterialTheme.colorScheme.onSecondaryContainer
        ConnectionPresentationState.Disconnected -> MaterialTheme.colorScheme.onPrimary
    }
    val mascotMessage = when {
        !hasProfile -> stringResource(R.string.pxlnet_mascot_no_subscription)
        state == ConnectionPresentationState.Connected && reachability == ConnectionReachability.Reachable -> stringResource(R.string.pxlnet_mascot_online)
        state == ConnectionPresentationState.Error -> stringResource(R.string.pxlnet_mascot_offline)
        state == ConnectionPresentationState.Connected && reachability == ConnectionReachability.Checking -> stringResource(R.string.pxlnet_mascot_checking)
        state in setOf(ConnectionPresentationState.Connecting, ConnectionPresentationState.Switching, ConnectionPresentationState.Disconnecting) -> stringResource(R.string.pxlnet_mascot_transitioning)
        else -> stringResource(R.string.pxlnet_mascot_ready)
    }
    var mascotTipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state, reachability, showMascotTips, hasProfile) {
        mascotTipVisible = false
        if (showMascot && showMascotTips) {
            delay(250)
            mascotTipVisible = true
            delay(4_000)
            mascotTipVisible = false
        }
    }
    val statusTitle = when (state) {
        ConnectionPresentationState.Connected -> stringResource(R.string.pxlnet_status_connected)
        ConnectionPresentationState.Connecting,
        ConnectionPresentationState.Switching -> stringResource(R.string.pxlnet_status_connecting)
        ConnectionPresentationState.Disconnecting -> stringResource(R.string.pxlnet_disconnecting)
        ConnectionPresentationState.Error -> stringResource(R.string.pxlnet_vpn_no_access)
        ConnectionPresentationState.Disconnected -> stringResource(R.string.pxlnet_status_disconnected)
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(360),
        label = "connect-entrance",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.pxlnet_connection_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(statusTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(164.dp).graphicsLayer {
                    scaleX = entranceScale
                    scaleY = entranceScale
                },
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
                        if (state == ConnectionPresentationState.Error) Icons.Default.CloudOff else Icons.Default.PowerSettingsNew,
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
                    PxlCatCharacter(state, animateMascot, onMascotTap)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (state == ConnectionPresentationState.Error) {
            Text(
                stringResource(R.string.pxlnet_choose_another_server),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
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
private fun ServerCard(
    serverTag: String,
    serverType: String,
    delay: Int?,
    loading: Boolean,
    latencySource: PxlLatencySource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
                Text(stringResource(R.string.pxlnet_selected_server_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(serverTitle(context, serverTag), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                serverProtocol(serverTag, serverType).takeIf(String::isNotBlank)?.let { protocol ->
                    Text(protocol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(
                        if (latencySource == PxlLatencySource.VPN_SERVER) {
                            R.string.pxlnet_latency_server_short
                        } else {
                            R.string.pxlnet_website_delay_short
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DelayIndicator(delay, loading)
            }
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
    onAddLink: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / titles.size },
                modifier = Modifier.fillMaxWidth(),
            )
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
                    Text(
                        stringResource(R.string.pxlnet_onboarding_telegram_optional),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onTelegramLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pxlnet_login_telegram))
                    }
                    OutlinedButton(onClick = onAddLink, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pxlnet_add_subscription))
                    }
                    TextButton(onClick = onImportClipboard, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pxlnet_from_clipboard))
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
                            Text(stringResource(R.string.pxlnet_route_smart_title))
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
            Spacer(Modifier.weight(1f))
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
private fun ServerPickerRow(
    item: ServerChoice,
    selected: Boolean,
    delay: Int?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerLocationIcon(item.tag)
        Column(modifier = Modifier.weight(1f)) {
            Text(serverTitle(context, item.tag, item.type), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            val protocol = serverProtocol(item.tag, item.type)
            if (protocol.isNotBlank()) Text(protocol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DelayIndicator(delay, loading)
        if (selected) {
            Box(Modifier.size(8.dp).background(PxlGreen, CircleShape))
        }
    }
}

@Composable
private fun ServerPickerSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DelayIndicator(delay: Int?, loading: Boolean = false) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
    } else if (delay != null && delay > 0) {
        Text(
            stringResource(R.string.pxlnet_delay_ms, delay),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Icon(
            Icons.Default.Speed,
            contentDescription = stringResource(R.string.pxlnet_measurement_unavailable),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun serverTitle(context: Context, tag: String, type: String = ""): String = when (serverRegion(tag)) {
    ServerRegion.AUTO -> context.getString(R.string.pxlnet_server_auto)
    ServerRegion.GERMANY -> context.getString(R.string.pxlnet_country_germany)
    ServerRegion.FINLAND -> context.getString(R.string.pxlnet_country_finland)
    ServerRegion.OTHER -> cleanServerTag(tag)
}

@Composable
private fun ServerLocationIcon(tag: String) {
    when (serverRegion(tag)) {
        ServerRegion.AUTO -> {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        ServerRegion.GERMANY -> {
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
        ServerRegion.FINLAND -> {
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
                        .align(Alignment.CenterStart)
                        .offset(x = 9.dp)
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
        ServerRegion.OTHER -> {
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
