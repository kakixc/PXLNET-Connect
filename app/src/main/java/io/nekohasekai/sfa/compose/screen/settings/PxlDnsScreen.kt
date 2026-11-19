package io.nekohasekai.sfa.compose.screen.settings

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.StartServiceCoordinator
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.compose.component.LocalPxlFloatingNavInset
import io.nekohasekai.sfa.compose.component.LocalPxlTopBarInset
import io.nekohasekai.sfa.compose.component.PxlShapeLoadingIndicator
import io.nekohasekai.sfa.compose.component.pxlPressMotion
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.icons.MaterialIconsLibrary
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlDnsEndpoint
import io.nekohasekai.sfa.utils.PxlDnsProfile
import io.nekohasekai.sfa.utils.PxlDnsProfiles
import io.nekohasekai.sfa.utils.PxlDnsProbe
import io.nekohasekai.sfa.utils.PxlDnsTileMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PxlDnsScreen(navController: NavController, serviceStatus: Status, confirmTileStart: Boolean = false, tileStartMode: String = "", rootTab: Boolean = false, settingsOnly: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var showIntroduction by remember { mutableStateOf(!settingsOnly && !PxlDnsProfiles.introSeen(context)) }
    val profiles = remember(revision) { PxlDnsProfiles.all(context) }
    val selected = remember(revision) { PxlDnsProfiles.selected(context) }
    var editing by remember { mutableStateOf<PxlDnsProfile?>(null) }
    var quickAddress by remember { mutableStateOf("") }
    val quickAddressValid = remember(quickAddress) {
        quickAddress.isNotBlank() && runCatching { PxlDnsEndpoint.parse(quickAddress) }.isSuccess
    }
    var probeJob by remember { mutableStateOf<Job?>(null) }
    var probeBusy by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    var probeSuccess by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<PxlDnsProfile?>(null) }
    var showVpnWarning by remember(confirmTileStart) { mutableStateOf(confirmTileStart) }
    var warningIsOwnVpn by remember { mutableStateOf(false) }
    var pendingPermissionMode by remember { mutableStateOf<PxlDnsTileMode?>(null) }
    var tileMode by remember(revision) { mutableStateOf(PxlDnsProfiles.tileMode(context)) }
    var startWithVpn by remember(revision) { mutableStateOf(PxlDnsProfiles.startWithVpn(context)) }
    val addressBringIntoView = remember { BringIntoViewRequester() }
    val activeDnsMode = PxlDnsProfiles.activeMode(context)
    val dnsActive = activeDnsMode != null && serviceStatus == Status.Started
    val activeProfile = if (dnsActive) profiles.firstOrNull { it.id == PxlDnsProfiles.activeProfileId(context) } else null
    val vpnActive = serviceStatus == Status.Started && activeDnsMode != PxlDnsTileMode.DNS_ONLY
    val dnsBusy = serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    val floatingNavInset = LocalPxlFloatingNavInset.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val actionInteraction = remember { MutableInteractionSource() }
    val actionPressed by actionInteraction.collectIsPressedAsState()
    val actionCorner by animateDpAsState(
        targetValue = if (actionPressed && !dnsBusy) 20.dp else if (dnsBusy) 32.dp else if (dnsActive) 42.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "dns-action-shape",
    )
    val actionScale by animateFloatAsState(
        targetValue = if (actionPressed && !dnsBusy) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessHigh),
        label = "dns-action-press",
    )
    val statusColor by animateColorAsState(
        targetValue = if (dnsActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "dns-status-color",
    )
    val actionColor by animateColorAsState(
        targetValue = if (dnsActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "dns-action-color",
    )
    val actionContentColor by animateColorAsState(
        targetValue = if (dnsActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "dns-action-content-color",
    )

    LaunchedEffect(selected?.id) {
        probeJob?.cancel()
        probeBusy = false
        probeResult = null
    }

    fun startAfterPermission(mode: PxlDnsTileMode) {
        scope.launch {
            if (mode == PxlDnsTileMode.DNS_ONLY) {
                when (StartServiceCoordinator.preflightDnsOnly(context)) {
                    StartServiceCoordinator.DnsDecision.START -> if (StartServiceCoordinator.startDnsOnly(StartServiceCoordinator.Source.DNS_SCREEN)) {
                        (context as? MainActivity)?.reconnectServiceConnection()
                    }
                    else -> Toast.makeText(context, R.string.pxlnet_dns_no_profile, Toast.LENGTH_SHORT).show()
                }
            } else {
                if (serviceStatus == Status.Started) {
                    BoxService.setIntegratedDnsEnabled(true)
                } else if (StartServiceCoordinator.preflight(context, serviceStatus) == StartServiceCoordinator.Decision.START) {
                    if (!StartServiceCoordinator.start(StartServiceCoordinator.Source.DNS_SCREEN, PxlDnsTileMode.DNS_AND_VPN)) {
                        Toast.makeText(context, R.string.pxlnet_dns_requires_vpn, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingPermissionMode?.let(::startAfterPermission)
        pendingPermissionMode = null
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingPermissionMode?.let(::startAfterPermission)
        pendingPermissionMode = null
    }

    fun requestStart(mode: PxlDnsTileMode, confirmed: Boolean = false) {
        if (!PxlDnsProfiles.enabled(context)) return
        if (selected == null) {
            Toast.makeText(context, R.string.pxlnet_dns_no_profile, Toast.LENGTH_SHORT).show()
            return
        }
        if (runCatching { PxlDnsEndpoint.parse(selected.address) }.isFailure) {
            Toast.makeText(context, R.string.pxlnet_dns_invalid_address, Toast.LENGTH_LONG).show()
            return
        }
        if (mode == PxlDnsTileMode.DNS_AND_VPN && Settings.selectedProfile == -1L) {
            Toast.makeText(context, R.string.pxlnet_dns_requires_vpn, Toast.LENGTH_SHORT).show()
            return
        }
        if (mode == PxlDnsTileMode.DNS_AND_VPN && serviceStatus == Status.Started &&
            PxlDnsProfiles.activeMode(context) != PxlDnsTileMode.DNS_ONLY && Settings.serviceMode != ServiceMode.VPN) {
            Toast.makeText(context, R.string.pxlnet_dns_requires_vpn, Toast.LENGTH_SHORT).show()
            return
        }
        if (mode == PxlDnsTileMode.DNS_AND_VPN &&
            serviceStatus == Status.Started && PxlDnsProfiles.activeMode(context) == PxlDnsTileMode.DNS_ONLY) {
            scope.launch {
                withContext(Dispatchers.IO) { Settings.rebuildServiceMode() }
                if (Settings.serviceMode == ServiceMode.VPN) {
                    StartServiceCoordinator.switchMode(StartServiceCoordinator.Source.DNS_SCREEN, mode)
                } else {
                    Toast.makeText(context, R.string.pxlnet_dns_requires_vpn, Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (mode == PxlDnsTileMode.DNS_ONLY && !confirmed) {
            val capabilities = context.getSystemService(ConnectivityManager::class.java)
                ?.let { it.getNetworkCapabilities(it.activeNetwork) }
            val otherVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true && serviceStatus == Status.Stopped
            val ownVpn = serviceStatus == Status.Started && PxlDnsProfiles.activeMode(context) != PxlDnsTileMode.DNS_ONLY
            if (ownVpn || otherVpn) {
                warningIsOwnVpn = ownVpn
                showVpnWarning = true
                return
            }
        }
        scope.launch {
            if (mode == PxlDnsTileMode.DNS_ONLY) {
                when (StartServiceCoordinator.preflightDnsOnly(context)) {
                    StartServiceCoordinator.DnsDecision.START -> if (StartServiceCoordinator.startDnsOnly(StartServiceCoordinator.Source.DNS_SCREEN)) {
                        (context as? MainActivity)?.reconnectServiceConnection()
                    }
                    StartServiceCoordinator.DnsDecision.OPEN_APP_FOR_VPN_PERMISSION -> {
                        pendingPermissionMode = mode
                        vpnPermission.launch(VpnService.prepare(context) ?: return@launch)
                    }
                    StartServiceCoordinator.DnsDecision.OPEN_APP_FOR_NOTIFICATION_PERMISSION -> {
                        pendingPermissionMode = mode
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else -> Unit
                }
            } else if (serviceStatus == Status.Started) {
                BoxService.setIntegratedDnsEnabled(true)
            } else {
                when (StartServiceCoordinator.preflight(context, serviceStatus)) {
                    StartServiceCoordinator.Decision.START -> if (!StartServiceCoordinator.start(StartServiceCoordinator.Source.DNS_SCREEN, mode)) {
                        Toast.makeText(context, R.string.pxlnet_dns_requires_vpn, Toast.LENGTH_SHORT).show()
                    }
                    StartServiceCoordinator.Decision.OPEN_APP_FOR_VPN_PERMISSION -> {
                        pendingPermissionMode = mode
                        vpnPermission.launch(VpnService.prepare(context) ?: return@launch)
                    }
                    StartServiceCoordinator.Decision.OPEN_APP_FOR_NOTIFICATION_PERMISSION -> {
                        pendingPermissionMode = mode
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(tileStartMode, showIntroduction) {
        if (!showIntroduction) PxlDnsTileMode.entries.firstOrNull { it.name == tileStartMode }?.let(::requestStart)
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(if (settingsOnly) R.string.pxlnet_dns_settings else R.string.pxlnet_dns_title)) },
            navigationIcon = {
                if (!rootTab) IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
            actions = {
                if (rootTab) IconButton(onClick = { navController.navigate("dns/settings") }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.pxlnet_dns_settings))
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = LocalPxlTopBarInset.current),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = if (settingsOnly) 16.dp else floatingNavInset + 124.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!settingsOnly) item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = statusColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = RoundedCornerShape(if (dnsActive) 24.dp else 18.dp),
                        color = if (dnsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (dnsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        AnimatedContent(targetState = dnsActive, label = "dns-status-icon") { active ->
                            Icon(
                                if (active) Icons.Default.CheckCircle else Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(24.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AnimatedContent(
                            targetState = dnsActive,
                            transitionSpec = { androidx.compose.animation.fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith androidx.compose.animation.fadeOut() },
                            label = "dns-status",
                        ) { active ->
                            Text(stringResource(if (active) R.string.pxlnet_dns_working else R.string.pxlnet_dns_off), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            activeProfile?.name ?: selected?.name ?: stringResource(R.string.pxlnet_dns_no_profile),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (serviceStatus == Status.Started) {
                            Text(
                                stringResource(if (vpnActive) R.string.pxlnet_dns_vpn_also_on else R.string.pxlnet_dns_vpn_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        probeResult?.let { result ->
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (probeSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (selected != null) {
                        if (probeBusy) PxlShapeLoadingIndicator(size = 24.dp)
                        else IconButton(onClick = {
                            probeJob = scope.launch {
                                probeBusy = true
                                probeResult = null
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { PxlDnsProbe.check(PxlDnsEndpoint.parse(selected.address)) }
                                }
                                if (isActive) {
                                    probeSuccess = result.isSuccess
                                    probeResult = result.fold(
                                        onSuccess = { context.getString(R.string.pxlnet_dns_available_ms, it) },
                                        onFailure = { context.getString(R.string.pxlnet_dns_unavailable) },
                                    )
                                    probeBusy = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pxlnet_dns_check))
                        }
                    }
                }
            }
        }
        if (!settingsOnly) item {
            OutlinedTextField(
                value = quickAddress,
                onValueChange = { quickAddress = it },
                isError = quickAddress.isNotBlank() && !quickAddressValid,
                modifier = Modifier.fillMaxWidth()
                    .bringIntoViewRequester(addressBringIntoView)
                    .onFocusChanged { if (it.isFocused) scope.launch { addressBringIntoView.bringIntoView() } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                label = { Text(stringResource(R.string.pxlnet_dns_server)) },
                placeholder = { Text(stringResource(R.string.pxlnet_dns_server_hint)) },
                supportingText = if (quickAddress.isNotBlank() && !quickAddressValid) ({ Text(stringResource(R.string.pxlnet_dns_invalid_address)) }) else null,
                trailingIcon = {
                    IconButton(onClick = {
                        editing = PxlDnsProfile("", "", quickAddress.trim())
                        quickAddress = ""
                    }, enabled = quickAddressValid) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pxlnet_dns_add_profile))
                    }
                },
            )
        }
        if (!settingsOnly) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pxlnet_dns_profiles), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = PxlDnsProfile("", "", "") }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pxlnet_dns_add_profile))
                }
            }
        }
        if (!settingsOnly) items(profiles, key = { it.id }) { profile ->
            val isSelected = selected?.id == profile.id
            Card(
                shape = RoundedCornerShape(if (isSelected) 32.dp else 24.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable {
                            PxlDnsProfiles.select(context, profile.id)
                            revision++
                            if (serviceStatus == Status.Started && PxlDnsProfiles.activeMode(context) != null) {
                                scope.launch(Dispatchers.IO) { Libbox.newStandaloneCommandClient().serviceReload() }
                            }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(if (isSelected) 20.dp else 14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(MaterialIconsLibrary.getIconById(profile.iconId) ?: Icons.Default.Public, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                runCatching { PxlDnsEndpoint.parse(profile.address) }.getOrNull()?.let { "${it.type.uppercase()} · ${it.host}" } ?: profile.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(onClick = { editing = profile }) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.pxlnet_dns_edit_profile)) }
                }
            }
        }
        if (settingsOnly) item {
            Card(
                onClick = {
                    if (!startWithVpn && serviceStatus == Status.Started && activeDnsMode == PxlDnsTileMode.DNS_ONLY) {
                        Toast.makeText(context, R.string.pxlnet_dns_pair_stop_dns_first, Toast.LENGTH_SHORT).show()
                    } else if (!startWithVpn && (selected == null || Settings.selectedProfile == -1L)) {
                        Toast.makeText(context, R.string.pxlnet_dns_pair_needs_profiles, Toast.LENGTH_SHORT).show()
                    } else {
                        startWithVpn = !startWithVpn
                        PxlDnsProfiles.setStartWithVpn(context, startWithVpn)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().pxlPressMotion(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.pxlnet_dns_start_with_vpn), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.pxlnet_dns_start_with_vpn_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = startWithVpn,
                        onCheckedChange = null,
                    )
                }
            }
        }
        if (settingsOnly) item {
            if (startWithVpn) {
                Text(
                    stringResource(R.string.pxlnet_dns_paired_tile_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.pxlnet_dns_tile_action), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.pxlnet_dns_tile_action_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PxlDnsTileMode.entries.forEach { mode ->
                            val chosen = tileMode == mode
                            val corner by animateDpAsState(
                                targetValue = if (chosen) 26.dp else 18.dp,
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                                label = "dns-tile-choice-shape",
                            )
                            val choiceColor by animateColorAsState(
                                targetValue = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                label = "dns-tile-choice-color",
                            )
                            Card(
                                onClick = { tileMode = mode; PxlDnsProfiles.setTileMode(context, mode) },
                                modifier = Modifier.weight(1f).height(64.dp).pxlPressMotion(),
                                shape = RoundedCornerShape(corner),
                                colors = CardDefaults.cardColors(containerColor = choiceColor),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (chosen) Icons.Default.CheckCircle else if (mode == PxlDnsTileMode.DNS_ONLY) Icons.Default.Public else Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        stringResource(if (mode == PxlDnsTileMode.DNS_ONLY) R.string.pxlnet_dns_only else R.string.pxlnet_dns_with_vpn),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (!settingsOnly && !imeVisible) {
        Button(
            onClick = {
                when {
                    startWithVpn && activeDnsMode == PxlDnsTileMode.DNS_AND_VPN -> BoxService.stop()
                    startWithVpn -> requestStart(PxlDnsTileMode.DNS_AND_VPN)
                    activeDnsMode == PxlDnsTileMode.DNS_ONLY -> BoxService.stop()
                    activeDnsMode == PxlDnsTileMode.DNS_AND_VPN -> BoxService.setIntegratedDnsEnabled(false)
                    vpnActive -> requestStart(PxlDnsTileMode.DNS_AND_VPN)
                    else -> requestStart(PxlDnsTileMode.DNS_ONLY)
                }
            },
            enabled = !dnsBusy && selected != null &&
                (!startWithVpn || Settings.selectedProfile != -1L),
            shape = RoundedCornerShape(actionCorner),
            colors = ButtonDefaults.buttonColors(
                containerColor = actionColor,
                contentColor = actionContentColor,
                disabledContainerColor = actionColor,
                disabledContentColor = actionContentColor,
            ),
            interactionSource = actionInteraction,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = floatingNavInset + 8.dp)
                .height(72.dp)
                .graphicsLayer { scaleX = actionScale; scaleY = actionScale },
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (dnsBusy) {
                    PxlShapeLoadingIndicator(size = 30.dp, color = actionContentColor)
                } else {
                    AnimatedContent(
                        targetState = dnsActive,
                        transitionSpec = {
                            (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.88f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.08f))
                        },
                        label = "dns-action-state",
                    ) { active ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (active) Icons.Default.CheckCircle else Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(when {
                                    startWithVpn && activeDnsMode == PxlDnsTileMode.DNS_AND_VPN -> R.string.pxlnet_dns_disconnect_pair
                                    startWithVpn && activeDnsMode == PxlDnsTileMode.DNS_ONLY -> R.string.pxlnet_dns_switch_to_pair
                                    startWithVpn && vpnActive -> R.string.pxlnet_dns_add_to_vpn
                                    startWithVpn -> R.string.pxlnet_dns_connect_pair
                                    active -> R.string.pxlnet_dns_disconnect
                                    vpnActive -> R.string.pxlnet_dns_add_to_vpn
                                    else -> R.string.pxlnet_dns_connect
                                }),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (showVpnWarning && !showIntroduction) {
        AlertDialog(
            onDismissRequest = { showVpnWarning = false },
            title = { Text(stringResource(R.string.pxlnet_dns_title)) },
            text = { Text(stringResource(if (warningIsOwnVpn) R.string.pxlnet_dns_vpn_running_warning else R.string.pxlnet_dns_other_vpn_warning)) },
            confirmButton = { TextButton(onClick = {
                showVpnWarning = false
                if (warningIsOwnVpn) {
                    StartServiceCoordinator.switchMode(StartServiceCoordinator.Source.DNS_SCREEN, PxlDnsTileMode.DNS_ONLY)
                } else requestStart(PxlDnsTileMode.DNS_ONLY, true)
            }) { Text(stringResource(R.string.pxlnet_dns_switch)) } },
            dismissButton = { TextButton(onClick = { showVpnWarning = false }) { Text(stringResource(R.string.pxlnet_dns_cancel)) } },
        )
    }
    if (showIntroduction) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Public, contentDescription = null) },
            title = { Text(stringResource(R.string.pxlnet_dns_intro_title)) },
            text = { Text(stringResource(R.string.pxlnet_dns_intro_body)) },
            confirmButton = {
                Button(onClick = {
                    PxlDnsProfiles.setIntroSeen(context)
                    showIntroduction = false
                }, modifier = Modifier.pxlPressMotion()) { Text(stringResource(R.string.pxlnet_dns_intro_ack)) }
            },
            shape = RoundedCornerShape(32.dp),
        )
    }
    deleteCandidate?.let { candidate -> AlertDialog(
        onDismissRequest = { deleteCandidate = null },
        title = { Text(stringResource(R.string.pxlnet_dns_delete)) },
        text = { Text(stringResource(R.string.pxlnet_dns_delete_confirm)) },
        confirmButton = { TextButton(onClick = { PxlDnsProfiles.delete(context, candidate.id); revision++; deleteCandidate = null }) { Text(stringResource(R.string.pxlnet_dns_delete)) } },
        dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.pxlnet_dns_cancel)) } },
    ) }
    editing?.let { candidate ->
        DnsProfileEditor(
            initial = candidate,
            onDismiss = { editing = null },
            onChooseIcon = { showIconPicker = true },
            onChooseApps = { showAppPicker = true },
            onDelete = if (candidate.id.isNotBlank() && PxlDnsProfiles.activeMode(context) == null) {
                { editing = null; deleteCandidate = candidate }
            } else null,
            onSave = {
                runCatching { PxlDnsProfiles.save(context, it) }.onSuccess {
                    editing = null
                    revision++
                }.onFailure { error -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
            },
        )
    }
    if (showIconPicker) DnsIconPicker(onSelect = { id -> editing = editing?.copy(iconId = id); showIconPicker = false }, onDismiss = { showIconPicker = false })
    if (showAppPicker) DnsAppPicker(
        selected = editing?.appPackages.orEmpty(),
        onSelect = { packages -> editing = editing?.copy(appPackages = packages); showAppPicker = false },
        onDismiss = { showAppPicker = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsProfileEditor(
    initial: PxlDnsProfile,
    onDismiss: () -> Unit,
    onChooseIcon: () -> Unit,
    onChooseApps: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (PxlDnsProfile) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var address by remember(initial.id) { mutableStateOf(initial.address) }
    var domains by remember(initial.id) { mutableStateOf(initial.domains.joinToString(", ")) }
    val error = remember(address) { runCatching { PxlDnsEndpoint.parse(address) }.exceptionOrNull()?.message }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (initial.id.isBlank()) R.string.pxlnet_dns_add_profile else R.string.pxlnet_dns_edit_profile),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.pxlnet_dns_cancel))
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(stringResource(R.string.pxlnet_dns_editor_basic), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.pxlnet_dns_profile_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.pxlnet_dns_server)) },
                        placeholder = { Text(stringResource(R.string.pxlnet_dns_server_hint)) },
                        isError = address.isNotBlank() && error != null,
                        supportingText = if (address.isNotBlank() && error != null) ({ Text(stringResource(R.string.pxlnet_dns_invalid_address)) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Surface(
                        onClick = onChooseIcon,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(MaterialIconsLibrary.getIconById(initial.iconId) ?: Icons.Default.Public, contentDescription = null, modifier = Modifier.size(28.dp))
                            Text(stringResource(R.string.pxlnet_dns_icon), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(stringResource(R.string.pxlnet_dns_editor_rules), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    OutlinedTextField(
                        value = domains,
                        onValueChange = { domains = it },
                        label = { Text(stringResource(R.string.pxlnet_dns_domains)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Surface(
                        onClick = onChooseApps,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.pxlnet_dns_select_apps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(initial.appPackages.size.toString(), style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
                if (initial.appPackages.isNotEmpty()) item {
                    Text(
                        stringResource(R.string.pxlnet_dns_selected_apps_route_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null) item {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().pxlPressMotion()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.pxlnet_dns_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).pxlPressMotion()) {
                    Text(stringResource(R.string.pxlnet_dns_cancel))
                }
                Button(
                    onClick = { onSave(initial.copy(name = name, address = address, domains = domains.split(',').map(String::trim).filter(String::isNotBlank))) },
                    enabled = name.isNotBlank() && address.isNotBlank() && error == null,
                    modifier = Modifier.weight(1.5f).pxlPressMotion(enabled = name.isNotBlank() && address.isNotBlank() && error == null),
                ) {
                    Text(stringResource(R.string.pxlnet_dns_save))
                }
            }
        }
    }
}

@Composable
private fun DnsIconPicker(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val icons = remember(query) { MaterialIconsLibrary.searchIcons(query).take(300) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pxlnet_dns_icon)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true)
                LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.height(360.dp)) {
                    items(icons.size) { index ->
                        val choice = icons[index]
                        IconButton(onClick = { onSelect(choice.id) }) { Icon(choice.icon, contentDescription = choice.label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pxlnet_dns_cancel)) } },
    )
}

@Composable
private fun DnsAppPicker(selected: List<String>, onSelect: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(context) {
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .mapNotNull { info -> info.activityInfo?.packageName?.let { it to info.loadLabel(context.packageManager).toString() } }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    val visibleApps = remember(apps, query) {
        apps.orEmpty().filter { (packageName, label) ->
            label.contains(query.trim(), ignoreCase = true) || packageName.contains(query.trim(), ignoreCase = true)
        }
    }
    var choices by remember { mutableStateOf(selected.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pxlnet_dns_select_apps)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.pxlnet_dns_search_apps)) },
                )
                if (apps == null) {
                    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        PxlShapeLoadingIndicator(size = 28.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.pxlnet_dns_loading_apps))
                    }
                } else if (visibleApps.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.pxlnet_dns_no_matching_apps))
                    }
                } else LazyColumn(Modifier.height(360.dp)) {
                    items(visibleApps, key = { it.first }) { (packageName, label) ->
                        Row(Modifier.fillMaxWidth().clickable { choices = if (packageName in choices) choices - packageName else choices + packageName }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = packageName in choices, onCheckedChange = { choices = if (it) choices + packageName else choices - packageName })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(choices.toList()) }, enabled = apps != null) { Text(stringResource(R.string.pxlnet_dns_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pxlnet_dns_cancel)) } },
    )
}
