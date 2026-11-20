package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.ConnectLoadingIndicator
import io.nekohasekai.sfa.compose.component.pxlPressMotion
import io.nekohasekai.sfa.constant.Status

@Composable
internal fun FocusHomeControl(
    hasProfile: Boolean,
    dnsOnly: Boolean,
    blocked: Boolean,
    serviceStatus: Status,
    switching: Boolean,
    requestPending: Boolean,
    reachability: ConnectionReachability,
    server: String?,
    serverProtocol: String?,
    onChooseServer: (() -> Unit)?,
    onChooseProfile: (() -> Unit)?,
    onConnect: () -> Unit,
    onRetry: (() -> Unit)?,
    onOpenDns: () -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onLogin: (() -> Unit)?,
    mascot: (@Composable () -> Unit)?,
    pinnedControls: Boolean = false,
) {
    val running = serviceStatus == Status.Started
    val heading = when {
        dnsOnly -> R.string.focus_dns_only
        !hasProfile -> R.string.focus_setup
        serviceStatus == Status.Stopping -> R.string.pxlnet_disconnecting
        running -> R.string.focus_on
        serviceStatus == Status.Starting -> R.string.pxlnet_connecting
        else -> R.string.focus_off
    }
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.heightIn(min = if (hasProfile && !dnsOnly) 60.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(heading), style = MaterialTheme.typography.headlineSmall)
                    if (running && !dnsOnly) {
                        val result = when (reachability) {
                            ConnectionReachability.Reachable -> R.string.focus_probe_success
                            ConnectionReachability.Checking -> R.string.pxlnet_checking_internet
                            ConnectionReachability.Unreachable -> if (pinnedControls) R.string.focus_trial_probe_failed else R.string.focus_probe_failed
                            ConnectionReachability.Unknown -> null
                        }
                        if (result != null) Text(stringResource(result), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!pinnedControls && reachability == ConnectionReachability.Unreachable && onRetry != null) {
                            TextButton(onClick = onRetry, modifier = Modifier.pxlPressMotion()) { Text(stringResource(R.string.focus_retry)) }
                        }
                    }
                }
                if (hasProfile && !dnsOnly && mascot != null) {
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { mascot() }
                }
            }
            when {
                dnsOnly -> if (!pinnedControls) Button(onClick = onOpenDns, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.pxlnet_dns_title))
                }
                !hasProfile -> {
                    Text(stringResource(R.string.focus_setup_help), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onPaste, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pxlPressMotion()) {
                        Text(stringResource(R.string.focus_paste))
                    }
                    TextButton(onClick = onImport) { Text(stringResource(R.string.pxlnet_link_cfg)) }
                    if (onLogin != null) TextButton(onClick = onLogin) { Text(stringResource(R.string.pxlnet_login_telegram)) }
                }
                !pinnedControls -> {
                    FocusServerSelection(server, onChooseServer)
                    if (!blocked) FocusConnectButton(serviceStatus, switching, requestPending, onConnect)
                    if (onChooseProfile != null) TextButton(onClick = onChooseProfile) {
                        Text(stringResource(R.string.pxlnet_choose_available_profile))
                    }
                }
                else -> FocusServerSelection(server, onChooseServer, serverProtocol, embedded = true)
            }
        }
    }
}

@Composable
private fun FocusServerSelection(
    server: String?,
    onChooseServer: (() -> Unit)?,
    protocol: String? = null,
    embedded: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().pxlPressMotion(enabled = onChooseServer != null, pressedScale = 0.985f)
            .clickable(enabled = onChooseServer != null, role = Role.Button, onClick = { onChooseServer?.invoke() }),
        shape = RoundedCornerShape(22.dp),
        color = if (embedded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    server ?: stringResource(R.string.focus_no_node),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (embedded) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = protocol?.takeIf(String::isNotBlank)?.let { { Text(it) } },
            overlineContent = { Text(stringResource(R.string.focus_server)) },
            leadingContent = { Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun FocusConnectButton(
    serviceStatus: Status,
    switching: Boolean,
    requestPending: Boolean,
    onConnect: () -> Unit,
    pinned: Boolean = false,
    failedStart: Boolean = false,
) {
    val running = serviceStatus == Status.Started
    val moving = switching || serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    val enabled = !moving && !requestPending
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = when {
            pressed && enabled -> 24.dp
            moving || requestPending -> 26.dp
            running -> 30.dp
            else -> 34.dp
        },
        animationSpec = if (pressed && enabled) spring(dampingRatio = 1f, stiffness = 800f)
            else spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "vpn-button-corners",
    )
    Button(
        onClick = onConnect,
        enabled = enabled,
        interactionSource = interactions,
        shape = RoundedCornerShape(radius),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (pinned) 64.dp else 68.dp),
    ) {
        if (moving || requestPending) ConnectLoadingIndicator(Modifier.size(28.dp), LocalContentColor.current)
        else Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(when {
            switching -> R.string.pxlnet_vpn_changing
            serviceStatus == Status.Stopping -> R.string.pxlnet_disconnecting
            serviceStatus == Status.Starting || (pinned && requestPending && !running) -> R.string.pxlnet_connecting
            running -> R.string.focus_disconnect
            failedStart -> R.string.focus_trial_retry_connect
            else -> R.string.focus_connect
        }), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun TrialConnectionFooter(
    modifier: Modifier,
    serviceStatus: Status,
    switching: Boolean,
    requestPending: Boolean,
    blocked: Boolean,
    failedStart: Boolean,
    dnsOnly: Boolean,
    onOpenDns: () -> Unit,
    onConnect: () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            if (dnsOnly) {
                Button(onClick = onOpenDns, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.pxlnet_dns_title))
                }
            } else {
                if (!blocked) FocusConnectButton(serviceStatus, switching, requestPending, onConnect, pinned = true, failedStart = failedStart)
            }
        }
    }
}

@Composable
internal fun FocusTrafficSummary(
    smartRouting: Boolean,
    appRules: Boolean,
    dnsEnabled: Boolean,
    dnsActive: Boolean,
    onRouting: () -> Unit,
    onDns: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusShortcut(
            label = stringResource(when {
                appRules -> R.string.focus_route_apps
                smartRouting -> R.string.focus_route_smart
                else -> R.string.focus_route_all
            }),
            onClick = onRouting,
            modifier = Modifier.weight(1f),
        )
        if (dnsEnabled) FocusShortcut(
            label = stringResource(if (dnsActive) R.string.focus_dns_short_on else R.string.focus_dns_short_off),
            onClick = onDns,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FocusShortcut(label: String, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).pxlPressMotion()) {
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
