package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.component.PxlRootTopBar
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PxlRoutingMode {
    All,
    Smart,
    SelectedApps,
}

@Composable
fun PxlAppRoutingScreen(
    serviceStatus: Status,
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onManageApps: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)
    var mode by remember {
        mutableStateOf(
            when {
                Settings.perAppProxyEnabled -> PxlRoutingMode.SelectedApps
                viewModel.uiState.value.smartRoutingEnabled -> PxlRoutingMode.Smart
                else -> PxlRoutingMode.All
            },
        )
    }

    fun applyMode(next: PxlRoutingMode) {
        if (mode == next) return
        mode = next
        viewModel.setSmartRouting(next == PxlRoutingMode.Smart)
        coroutineScope.launch(Dispatchers.IO) {
            Settings.perAppProxyEnabled = next == PxlRoutingMode.SelectedApps
            if (next == PxlRoutingMode.SelectedApps) {
                Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
            }
            withContext(Dispatchers.Main) {
                notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                Toast.makeText(context, R.string.pxlnet_mode_applied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    OverrideTopBar {
        PxlRootTopBar(
            title = stringResource(R.string.pxlnet_app_routing_title),
            subtitle = stringResource(R.string.pxlnet_app_routing_subtitle),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.pxlnet_app_routing_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            RoutingModeCard(
                icon = Icons.Default.Public,
                title = stringResource(R.string.pxlnet_route_all_title),
                description = stringResource(R.string.pxlnet_route_all_description),
                selected = mode == PxlRoutingMode.All,
                onClick = { applyMode(PxlRoutingMode.All) },
            )
        }
        item {
            RoutingModeCard(
                icon = Icons.Default.Route,
                title = stringResource(R.string.pxlnet_route_smart_title),
                description = stringResource(R.string.pxlnet_route_smart_description),
                selected = mode == PxlRoutingMode.Smart,
                onClick = { applyMode(PxlRoutingMode.Smart) },
            )
        }
        item {
            RoutingModeCard(
                icon = Icons.Default.Apps,
                title = stringResource(R.string.pxlnet_route_selected_title),
                description = stringResource(R.string.pxlnet_route_selected_description),
                selected = mode == PxlRoutingMode.SelectedApps,
                onClick = { applyMode(PxlRoutingMode.SelectedApps) },
            )
        }
        if (mode == PxlRoutingMode.SelectedApps) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.pxlnet_selected_apps_count, Settings.perAppProxyList.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Button(
                            onClick = onManageApps,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.pxlnet_choose_apps))
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.pxlnet_routing_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RoutingModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
