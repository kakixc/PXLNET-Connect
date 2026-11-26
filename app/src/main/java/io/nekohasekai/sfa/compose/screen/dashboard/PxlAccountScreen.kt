package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.component.PxlShapeLoadingIndicator
import io.nekohasekai.sfa.compose.component.pxlPressMotion
import io.nekohasekai.sfa.compose.component.LocalPxlTopBarInset
import io.nekohasekai.sfa.compose.component.LocalPxlFloatingNavInset
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlAuthClient
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PxlAccountScreen(
    onOpenNewProfile: (NewProfileArgs) -> Unit,
    viewModel: DashboardViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasSession = uiState.hasTelegramSession
    val hasVerifiedAccount = uiState.accountLastVerifiedAt != null
    val showTelegramCodeAction = hasSession && uiState.signedInByEmail && hasVerifiedAccount &&
        !uiState.hasTelegramIdentity
    val telegramCodeEnabled = !uiState.telegramLoginPending && !uiState.ecosystemCheckPending
    var showEmailLogin by remember { mutableStateOf(false) }
    var showTelegramLink by remember { mutableStateOf(false) }
    var showLinkHelp by remember { mutableStateOf(false) }
    if (showEmailLogin) {
        PxlEmailLoginDialog(
            viewModel,
            onDismiss = { showEmailLogin = false },
            onSubscriptionHelp = { showLinkHelp = true },
        )
    }
    if (showTelegramLink && hasSession && uiState.signedInByEmail) {
        PxlTelegramLinkDialog(viewModel, onDismiss = { showTelegramLink = false })
    }
    if (showLinkHelp) {
        val checkingLink = hasSession && uiState.accountHasSubscription == false
        AlertDialog(
            onDismissRequest = { showLinkHelp = false },
            title = {
                Text(stringResource(if (checkingLink) R.string.pxl_email_check_link else R.string.pxl_email_link_help))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (checkingLink) {
                        Text(stringResource(R.string.pxl_email_check_link_bot))
                        if (showTelegramCodeAction) {
                            Text(stringResource(R.string.pxl_email_link_help_enter))
                            TextButton(onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) }) {
                                Text(stringResource(R.string.pxl_email_open_bot))
                            }
                        } else {
                            Text(stringResource(R.string.pxl_email_check_link_refresh))
                        }
                    } else {
                        Text(stringResource(R.string.pxl_email_link_help_before))
                        Text(stringResource(R.string.pxl_email_link_help_after))
                    }
                }
            },
            confirmButton = {
                if (checkingLink && showTelegramCodeAction) {
                    TextButton(
                        onClick = {
                            showLinkHelp = false
                            showTelegramLink = true
                        },
                        enabled = telegramCodeEnabled,
                    ) { Text(stringResource(R.string.pxl_email_enter_telegram_code)) }
                } else {
                    TextButton(onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) }) {
                        Text(stringResource(R.string.pxl_email_open_bot))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkHelp = false }) {
                    Text(stringResource(R.string.pxl_email_link_help_close))
                }
            },
        )
    }
    val summaryRevision by SubscriptionInfoStore.summaryRevision.collectAsState()
    val selectedProfile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
    val linkSummary = remember(uiState.selectedProfileId, summaryRevision) {
        SubscriptionInfoStore.summary(context, uiState.selectedProfileId)
    }

    LaunchedEffect(uiState.telegramLoginError) {
        uiState.telegramLoginError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    OverrideTopBar {
        PxlRootTopBar(
            title = stringResource(R.string.pxlnet_account_title),
            subtitle = stringResource(R.string.pxlnet_account_subtitle),
            glassContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        )
    }

    val topBarInset = LocalPxlTopBarInset.current
    val floatingNavInset = LocalPxlFloatingNavInset.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topBarInset + 12.dp, bottom = floatingNavInset + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PxlAccountCard(compact = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(28.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.telegramAccountName
                                ?: uiState.telegramUsername?.let { "@$it" }
                                ?: uiState.verifiedEmail
                                ?: stringResource(if (hasSession) R.string.pxlnet_account_title else R.string.pxlnet_guest_mode),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val accountStatus = stringResource(when {
                                !hasSession -> R.string.pxlnet_guest_mode_description
                                !hasVerifiedAccount -> R.string.pxlnet_account_not_verified
                                uiState.accountServiceAvailable == false -> R.string.pxlnet_account_saved_data_offline
                                uiState.signedInByEmail -> R.string.pxl_email_signed_in
                                else -> R.string.pxlnet_signed_in_telegram
                            })
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                accountStatus,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                if (hasSession) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.pxlnet_tariff_access),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (!hasVerifiedAccount) {
                                    stringResource(R.string.pxlnet_account_status_unknown)
                                } else if (uiState.accountHasSubscription == false) {
                                    stringResource(R.string.pxl_email_no_subscription)
                                } else if (uiState.telegramSubscriptionActive) {
                                    formatAccountExpiry(uiState.telegramSubscriptionExpiresAt)?.let {
                                        stringResource(R.string.pxlnet_subscription_active_until, it)
                                    } ?: stringResource(R.string.pxlnet_subscription_active)
                                } else {
                                    stringResource(R.string.pxlnet_subscription_inactive)
                                },
                                color = if (!hasVerifiedAccount) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else if (uiState.telegramSubscriptionActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::refreshTelegramAccount,
                            enabled = !uiState.telegramLoginPending,
                            modifier = Modifier.weight(1f).pxlPressMotion(enabled = !uiState.telegramLoginPending),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            if (uiState.telegramLoginPending) {
                                PxlShapeLoadingIndicator(size = 18.dp)
                            } else {
                                Text(stringResource(R.string.pxlnet_refresh))
                            }
                        }
                        if (hasVerifiedAccount) {
                            Button(
                                onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                                modifier = Modifier.weight(1f).pxlPressMotion(),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(stringResource(if (uiState.telegramSubscriptionActive) R.string.pxlnet_renew else R.string.pxlnet_buy))
                            }
                        }
                    }
                    if (uiState.accountSubscriptionLoadFailed) {
                        Text(
                            stringResource(R.string.pxl_email_servers_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = viewModel::refreshTelegramAccount,
                            enabled = !uiState.telegramLoginPending,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.pxl_email_retry_subscription)) }
                    }
                    val verifiedEmail = uiState.verifiedEmail
                    if (verifiedEmail != null) {
                        Text(
                            stringResource(R.string.pxl_email_verified_address, verifiedEmail),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (uiState.hasTelegramIdentity) {
                            Text(
                                stringResource(R.string.pxl_email_both_methods),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!uiState.signedInByEmail) {
                        OutlinedButton(
                            onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.pxl_email_link)) }
                        Text(
                            stringResource(R.string.pxl_email_link_bot_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hasVerifiedAccount && uiState.accountHasSubscription == false) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.pxl_email_missing_subscription_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (showTelegramCodeAction) {
                                OutlinedButton(
                                    onClick = { showTelegramLink = true },
                                    enabled = telegramCodeEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.pxl_email_enter_telegram_code)) }
                            }
                            TextButton(onClick = { showLinkHelp = true }) {
                                Text(stringResource(R.string.pxl_email_check_link))
                            }
                        }
                    }
                    if (showTelegramCodeAction && uiState.accountHasSubscription != false) {
                        OutlinedButton(
                            onClick = { showTelegramLink = true },
                            enabled = telegramCodeEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.pxl_email_enter_telegram_code)) }
                    }
                    TextButton(
                        onClick = viewModel::logoutTelegram,
                        enabled = !uiState.telegramLoginPending && !uiState.ecosystemCheckPending,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.pxlnet_sign_out))
                    }
                } else {
                    val loginEnabled = !uiState.telegramLoginPending && !uiState.ecosystemCheckPending
                    Button(
                        onClick = { showEmailLogin = true },
                        enabled = loginEnabled,
                        modifier = Modifier.fillMaxWidth().pxlPressMotion(enabled = loginEnabled),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(stringResource(R.string.pxl_email_login)) }
                    OutlinedButton(
                        onClick = viewModel::startTelegramLogin,
                        enabled = loginEnabled,
                        modifier = Modifier.fillMaxWidth().pxlPressMotion(enabled = loginEnabled),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (uiState.telegramLoginPending) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.pxlnet_login_waiting))
                        } else {
                            Text(stringResource(R.string.pxlnet_login_telegram))
                        }
                    }
                    TextButton(onClick = { showLinkHelp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pxl_email_link_help))
                    }
                }
            }
        }

        item { AccountSectionHeader(R.string.pxlnet_current_vpn_subscription) }
        item {
            PxlAccountCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (uiState.selectedProfileId > 0) Icons.Default.CloudDone else Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedProfile?.name ?: stringResource(R.string.pxlnet_subscription_not_added),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (uiState.selectedProfileId > 0) {
                            linkSummary?.let { summary ->
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (!hasSession || uiState.selectedProfileId <= 0 || uiState.accountHasSubscription == false) {
                    Button(
                        onClick = { onOpenNewProfile(NewProfileArgs()) },
                        modifier = Modifier.fillMaxWidth().pxlPressMotion(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(R.string.pxlnet_add_existing_subscription))
                    }
                }
                if (uiState.selectedProfileId <= 0) {
                    OutlinedButton(
                        onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                        modifier = Modifier.fillMaxWidth().pxlPressMotion(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(stringResource(R.string.pxlnet_buy_in_bot))
                    }
                }
            }
        }

        item { AccountSectionHeader(R.string.pxlnet_ecosystem_title) }
        item {
            PxlAccountCard {
                Text(
                    stringResource(R.string.pxlnet_ecosystem_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PxlPulsePanel(
                    serviceAvailable = uiState.accountServiceAvailable,
                    isLoggedIn = hasSession,
                    subscriptionActive = uiState.telegramSubscriptionActive,
                    hasLocalSubscription = uiState.selectedProfileId > 0,
                    serverCount = uiState.availableServerTags.count { !it.equals("AUTO", true) },
                    checking = uiState.ecosystemCheckPending,
                    checkedAt = uiState.ecosystemLastCheckedAt,
                    onRefresh = viewModel::refreshEcosystem,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EcosystemRow(
                    icon = Icons.Default.Campaign,
                    title = stringResource(R.string.pxlnet_news_title),
                    description = stringResource(R.string.pxlnet_news_description),
                    onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_CHANNEL) },
                )
                EcosystemRow(
                    icon = Icons.Default.SupportAgent,
                    title = stringResource(R.string.pxlnet_support_title),
                    description = stringResource(R.string.pxlnet_support_description),
                    onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                )
            }
        }

        if (uiState.accountServiceAvailable == false) {
            item {
                PxlAccountCard(compact = true) {
                    Text(stringResource(R.string.pxlnet_service_unavailable_guest), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PxlTelegramLinkDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(stringResource(R.string.pxl_email_link_telegram)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(100); error = null },
                    enabled = !pending,
                    singleLine = true,
                    label = { Text(stringResource(R.string.pxl_email_telegram_code)) },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.pxl_email_telegram_code_hint))
                TextButton(
                    onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                    enabled = !pending,
                ) { Text(stringResource(R.string.pxl_email_open_bot)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank() && !pending,
                onClick = {
                    pending = true
                    error = null
                    scope.launch {
                        try {
                            viewModel.linkTelegramToEmailAccount(code.trim())
                            onDismiss()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: PxlAuthClient.ApiError) {
                            error = when (failure.code) {
                                "account_conflict" -> R.string.pxl_email_telegram_conflict
                                "invalid_or_expired_link" -> R.string.pxl_email_telegram_code_rejected
                                else -> R.string.pxl_email_unavailable
                            }
                        } catch (_: Exception) {
                            error = R.string.pxl_email_unavailable
                        } finally {
                            pending = false
                        }
                    }
                },
            ) {
                if (pending) PxlShapeLoadingIndicator(size = 18.dp)
                else Text(stringResource(R.string.pxl_email_link_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun AccountSectionHeader(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

@Composable
private fun PxlPulsePanel(
    serviceAvailable: Boolean?,
    isLoggedIn: Boolean,
    subscriptionActive: Boolean,
    hasLocalSubscription: Boolean,
    serverCount: Int,
    checking: Boolean,
    checkedAt: Long?,
    onRefresh: () -> Unit,
) {
    val summary = when {
        checking -> stringResource(R.string.pxlnet_pulse_checking)
        serviceAvailable == false -> stringResource(R.string.pxlnet_pulse_offline)
        serverCount == 0 -> stringResource(R.string.pxlnet_pulse_no_servers)
        isLoggedIn && !subscriptionActive -> stringResource(R.string.pxlnet_pulse_renew)
        !isLoggedIn && hasLocalSubscription -> stringResource(R.string.pxlnet_pulse_guest_ready)
        else -> stringResource(R.string.pxlnet_pulse_ready)
    }
    val statusIcon = when {
        serviceAvailable == false -> Icons.Default.CloudOff
        serviceAvailable == true && serverCount > 0 -> Icons.Default.Public
        else -> Icons.Default.Public
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                tint = if (serviceAvailable == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pxlnet_pulse_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PulseMetric(
                label = stringResource(R.string.pxlnet_pulse_account),
                value = stringResource(
                    if (isLoggedIn) R.string.pxlnet_pulse_connected else R.string.pxlnet_pulse_guest,
                ),
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.pxlnet_pulse_access),
                value = stringResource(
                    when {
                        subscriptionActive -> R.string.pxlnet_pulse_active
                        hasLocalSubscription -> R.string.pxlnet_pulse_by_link
                        else -> R.string.pxlnet_pulse_inactive
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            PulseMetric(
                label = stringResource(R.string.pxlnet_pulse_servers),
                value = serverCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedButton(
            onClick = onRefresh,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth().pxlPressMotion(enabled = !checking),
            shape = RoundedCornerShape(18.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.pxlnet_pulse_check_now))
        }
        if (checkedAt != null && !checking) {
            Text(
                stringResource(R.string.pxlnet_pulse_checked_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(checkedAt))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PulseMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PxlAccountCard(
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(if (compact) 16.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            content = content,
        )
    }
}

@Composable
private fun EcosystemRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatAccountExpiry(value: String?): String? {
    val parts = value?.take(10)?.split('-') ?: return null
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}
