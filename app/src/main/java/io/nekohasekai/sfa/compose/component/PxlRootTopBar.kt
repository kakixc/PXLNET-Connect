package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status

val LocalPxlTopBarOverlay = compositionLocalOf { false }
val LocalPxlTopBarInset = compositionLocalOf<Dp> { 0.dp }
val LocalPxlFloatingNavInset = compositionLocalOf<Dp> { 0.dp }
val LocalPxlTopBarServiceStatus = compositionLocalOf { Status.Stopped }
val LocalPxlTopBarStatusText = compositionLocalOf<String?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PxlRootTopBar(
    title: String,
    subtitle: String = "PXLNET Connect · ${BuildConfig.VERSION_NAME}",
    onTitleClick: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val glassOverlay = LocalPxlTopBarOverlay.current
    val serviceStatus = LocalPxlTopBarServiceStatus.current
    val displayedSubtitle = LocalPxlTopBarStatusText.current ?: when (serviceStatus) {
        Status.Starting -> stringResource(R.string.status_starting)
        Status.Started -> stringResource(R.string.status_started)
        Status.Stopping -> stringResource(R.string.status_stopping)
        else -> subtitle
    }
    TopAppBar(
        title = {
            Column(
                modifier = if (onTitleClick == null) Modifier else Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick,
                ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = displayedSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (glassOverlay) Color.Transparent else MaterialTheme.colorScheme.surface,
            scrolledContainerColor = if (glassOverlay) Color.Transparent else MaterialTheme.colorScheme.surface,
        ),
    )
}
