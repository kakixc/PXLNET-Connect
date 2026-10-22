package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.compose.component.LocalPxlFloatingNavInset
import io.nekohasekai.sfa.compose.component.LocalPxlTopBarInset
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionSettingsScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.pxlnet_settings_subscriptions)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topInset = LocalPxlTopBarInset.current
    val bottomInset = LocalPxlFloatingNavInset.current
    var interval by remember { mutableStateOf(Settings.subscriptionUpdateIntervalMinutes) }
    var refreshing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = topInset + 16.dp, bottom = bottomInset + 16.dp),
    ) {
        Text(
            stringResource(R.string.pxlnet_subscription_interval_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            stringResource(R.string.pxlnet_subscription_interval_summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val intervals = listOf(
                60 to R.string.pxlnet_subscription_interval_1h,
                360 to R.string.pxlnet_subscription_interval_6h,
                720 to R.string.pxlnet_subscription_interval_12h,
            )
            intervals.forEachIndexed { index, (minutes, label) ->
                val selected = interval == minutes
                val shape = when (index) {
                    0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                    intervals.lastIndex -> RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                    else -> RoundedCornerShape(5.dp)
                }
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth().clip(shape).selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            interval = minutes
                            Settings.subscriptionUpdateIntervalMinutes = minutes
                            scope.launch(Dispatchers.IO) { UpdateProfileWork.reconfigureUpdater() }
                        },
                    ),
                )
            }
        }
        Button(
            onClick = {
                refreshing = true
                resultMessage = null
                scope.launch {
                    resultMessage = withContext(Dispatchers.IO) {
                        try {
                            val eligible = ProfileManager.list().any {
                                it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate
                            }
                            when {
                                !eligible -> R.string.pxlnet_subscription_refresh_empty
                                UpdateProfileWork.refreshNow(context) -> R.string.pxlnet_subscription_refresh_success
                                else -> R.string.pxlnet_subscription_refresh_failed
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            R.string.pxlnet_subscription_refresh_failed
                        }
                    }
                    refreshing = false
                }
            },
            enabled = !refreshing,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(stringResource(R.string.pxlnet_subscription_refresh_now))
        }
        resultMessage?.let { message ->
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
