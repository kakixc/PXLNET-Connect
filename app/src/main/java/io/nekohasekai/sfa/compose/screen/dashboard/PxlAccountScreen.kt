package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.SubscriptionInfoStore

private val PxlAccountGreen = Color(0xFF277A4A)

@Composable
fun PxlAccountScreen(
    onOpenNewProfile: (NewProfileArgs) -> Unit,
    viewModel: DashboardViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExistingSubscriptionOptions by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.telegramLoginError) {
        uiState.telegramLoginError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    OverrideTopBar {
        PxlRootTopBar(title = "Аккаунт PXLNET", subtitle = "Подписка и управление")
    }

    if (showExistingSubscriptionOptions) {
        AlertDialog(
            onDismissRequest = { showExistingSubscriptionOptions = false },
            title = { Text("У меня есть подписка") },
            text = {
                Text(
                    "Войдите через Telegram для автоматической синхронизации или добавьте существующую ссылку вручную.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingSubscriptionOptions = false
                        viewModel.startTelegramLogin()
                    },
                    enabled = !uiState.telegramLoginPending,
                ) { Text("Войти через Telegram") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExistingSubscriptionOptions = false
                        onOpenNewProfile(NewProfileArgs())
                    },
                ) { Text("Добавить ссылку / CFG") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
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
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                uiState.telegramAccountName
                                    ?: uiState.telegramUsername?.let { "@$it" }
                                    ?: "Гостевой режим",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (uiState.telegramAccountName != null) {
                                    "Вход выполнен через Telegram"
                                } else {
                                    "Можно пользоваться VPN по ссылке без входа"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (uiState.telegramAccountName != null) {
                        Text(
                            if (uiState.telegramSubscriptionActive) {
                                "Подписка активна${formatAccountExpiry(uiState.telegramSubscriptionExpiresAt)?.let { " до $it" }.orEmpty()}"
                            } else {
                                "Активной подписки пока нет"
                            },
                            color = if (uiState.telegramSubscriptionActive) PxlAccountGreen else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
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
                                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Обновить")
                                }
                            }
                            Button(
                                onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(if (uiState.telegramSubscriptionActive) "Продлить" else "Купить")
                            }
                        }
                        TextButton(onClick = viewModel::logoutTelegram) { Text("Выйти из аккаунта") }
                    } else {
                        Button(
                            onClick = viewModel::startTelegramLogin,
                            enabled = !uiState.telegramLoginPending,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (uiState.telegramLoginPending) {
                                CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text("Ожидаем подтверждение…")
                            } else {
                                Text("Войти через Telegram")
                            }
                        }
                        Text(
                            "Вход нужен для статуса, продления и автоматической синхронизации. Пароль Telegram приложению не передаётся.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
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
                ) {
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
                            Text("Подписка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                SubscriptionInfoStore.summary(context, uiState.selectedProfileId)
                                    ?: if (uiState.selectedProfileId > 0) "Добавлена в приложение" else "Ещё не добавлена",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = { showExistingSubscriptionOptions = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("У меня есть подписка")
                    }
                    OutlinedButton(
                        onClick = { PxlLinks.open(context, PxlLinks.TELEGRAM_BOT) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("У меня нет подписки")
                    }
                }
            }
        }

        if (uiState.accountServiceAvailable == false) {
            item {
                Text(
                    "Сервис аккаунтов временно недоступен. Гостевой режим по ссылке подписки продолжает работать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatAccountExpiry(value: String?): String? {
    val parts = value?.take(10)?.split('-') ?: return null
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else value
}
