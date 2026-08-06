package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.utils.PxlLinks

private val PxlAccountGreen = Color(0xFF277A4A)

@Composable
fun PxlAccountScreen(
    onOpenNewProfile: (NewProfileArgs) -> Unit,
    viewModel: DashboardViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isLoggedIn = uiState.telegramAccountName != null || uiState.telegramUsername != null

    LaunchedEffect(uiState.telegramLoginError) {
        uiState.telegramLoginError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    OverrideTopBar {
        PxlRootTopBar(
            title = stringResource(R.string.pxlnet_account_title),
            subtitle = stringResource(R.string.pxlnet_account_subtitle),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PxlAccountCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.telegramAccountName
                                ?: uiState.telegramUsername?.let { "@$it" }
                                ?: stringResource(R.string.pxlnet_guest_mode),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(
                                if (isLoggedIn) R.string.pxlnet_signed_in_telegram
                                else R.string.pxlnet_guest_mode_description,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isLoggedIn) {
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (uiState.telegramSubscriptionActive) {
                                    formatAccountExpiry(uiState.telegramSubscriptionExpiresAt)?.let {
                                        stringResource(R.string.pxlnet_subscription_active_until, it)
                                    } ?: stringResource(R.string.pxlnet_subscription_active)
                                } else {
                                    stringResource(R.string.pxlnet_subscription_inactive)
                                },
                                color = if (uiState.telegramSubscriptionActive) {
                                    PxlAccountGreen
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            if (uiState.selectedProfileId > 0) {
                                Text(
                                    stringResource(R.string.pxlnet_subscription_synced),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::refreshTelegramAccount,
                            enabled = !uiState.telegramLoginPending,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (uiState.telegramLoginPending) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.pxlnet_refresh))
                            }
                        }
                        Button(
                            onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (uiState.telegramSubscriptionActive) R.string.pxlnet_renew
                                    else R.string.pxlnet_buy,
                                ),
                            )
                        }
                    }
                    TextButton(onClick = viewModel::logoutTelegram) {
                        Text(stringResource(R.string.pxlnet_sign_out))
                    }
                } else {
                    Button(
                        onClick = viewModel::startTelegramLogin,
                        enabled = !uiState.telegramLoginPending,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (uiState.telegramLoginPending) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.pxlnet_login_waiting))
                        } else {
                            Text(stringResource(R.string.pxlnet_login_telegram))
                        }
                    }
                    Text(
                        stringResource(R.string.pxlnet_login_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!isLoggedIn) {
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
                                stringResource(R.string.pxlnet_subscription_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(
                                    if (uiState.selectedProfileId > 0) R.string.pxlnet_subscription_added
                                    else R.string.pxlnet_subscription_not_added,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (uiState.selectedProfileId <= 0) {
                        Button(
                            onClick = { onOpenNewProfile(NewProfileArgs()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.pxlnet_add_existing_subscription))
                        }
                        OutlinedButton(
                            onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.pxlnet_buy_in_bot))
                        }
                    }
                }
            }
        }

        item {
            PxlAccountCard {
                Text(
                    stringResource(R.string.pxlnet_ecosystem_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.pxlnet_ecosystem_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EcosystemRow(
                    icon = when (uiState.accountServiceAvailable) {
                        true -> Icons.Default.CheckCircle
                        false -> Icons.Default.CloudOff
                        null -> Icons.Default.Public
                    },
                    title = stringResource(R.string.pxlnet_services_title),
                    description = when (uiState.accountServiceAvailable) {
                        true -> stringResource(R.string.pxlnet_services_online)
                        false -> stringResource(R.string.pxlnet_services_offline)
                        null -> stringResource(R.string.pxlnet_services_checking)
                    } + " · " + stringResource(
                        R.string.pxlnet_servers_available,
                        uiState.availableServerTags.count { !it.equals("AUTO", true) },
                    ),
                )
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
                Text(
                    stringResource(R.string.pxlnet_service_unavailable_guest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PxlAccountCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
