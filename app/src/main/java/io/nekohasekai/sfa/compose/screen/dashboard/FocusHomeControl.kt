package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
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
    onChooseServer: (() -> Unit)?,
    onChooseProfile: (() -> Unit)?,
    onConnect: () -> Unit,
    onRetry: (() -> Unit)?,
    onOpenDns: () -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onLogin: (() -> Unit)?,
    mascot: (@Composable () -> Unit)?,
) {
    val running = serviceStatus == Status.Started
    val moving = switching || serviceStatus == Status.Starting || serviceStatus == Status.Stopping
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(heading), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (hasProfile && !dnsOnly && mascot != null) {
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { mascot() }
                }
            }
            when {
                dnsOnly -> Button(onClick = onOpenDns, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
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
                else -> {
                    OutlinedButton(
                        onClick = { onChooseServer?.invoke() },
                        enabled = onChooseServer != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(server ?: stringResource(R.string.focus_no_node), Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    if (!blocked) Button(
                        onClick = onConnect,
                        enabled = !moving && !requestPending,
                        shape = RoundedCornerShape(if (running) 20.dp else 32.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).pxlPressMotion(enabled = !moving && !requestPending),
                    ) {
                        if (moving || requestPending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(when {
                            switching -> R.string.pxlnet_vpn_changing
                            serviceStatus == Status.Stopping -> R.string.pxlnet_disconnecting
                            serviceStatus == Status.Starting -> R.string.pxlnet_connecting
                            running -> R.string.focus_disconnect
                            else -> R.string.focus_connect
                        }))
                    }
                    if (running) {
                        val result = when (reachability) {
                            ConnectionReachability.Reachable -> R.string.pxlnet_connection_works
                            ConnectionReachability.Checking -> R.string.pxlnet_checking_internet
                            ConnectionReachability.Unreachable -> R.string.focus_probe_failed
                            ConnectionReachability.Unknown -> null
                        }
                        if (result != null) Text(stringResource(result), style = MaterialTheme.typography.bodyMedium)
                        if (reachability == ConnectionReachability.Unreachable && onRetry != null) {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.focus_retry)) }
                        }
                    }
                    if (onChooseProfile != null) TextButton(onClick = onChooseProfile) {
                        Text(stringResource(R.string.pxlnet_choose_available_profile))
                    }
                }
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
    Card(shape = RoundedCornerShape(24.dp)) {
        TextButton(onClick = onRouting) {
            Text(stringResource(when {
                smartRouting -> R.string.pxlnet_routing_preview_smart
                appRules -> R.string.pxlnet_settings_app_rules
                else -> R.string.pxlnet_settings_routing
            }))
        }
        if (dnsEnabled) TextButton(onClick = onDns) {
            Text(stringResource(if (dnsActive) R.string.focus_dns_active else R.string.focus_dns_idle))
        }
    }
}
