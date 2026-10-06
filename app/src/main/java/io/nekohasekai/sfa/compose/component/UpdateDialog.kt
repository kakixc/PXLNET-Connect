package io.nekohasekai.sfa.compose.component

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.update.ReleaseNotesSummary
import io.nekohasekai.sfa.update.UpdateApkVerifier
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.emoji.Emoji
import org.kodein.emoji.EmojiTemplateCatalog
import org.kodein.emoji.all

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    previewOnly: Boolean = false,
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cachedApk by UpdateState.cachedApkFile
    var alreadyDownloaded by remember(updateInfo.versionCode, cachedApk, previewOnly) { mutableStateOf(false) }
    LaunchedEffect(updateInfo.versionCode, cachedApk, previewOnly) {
        if (previewOnly || cachedApk == null) return@LaunchedEffect
        val apk = cachedApk ?: return@LaunchedEffect
        alreadyDownloaded = withContext(Dispatchers.IO) {
            UpdateApkVerifier.isValid(context, apk, updateInfo)
        }
        if (!alreadyDownloaded && UpdateState.cachedApkFile.value == apk) {
            UpdateState.clearCachedApkPath()
        }
    }
    val preview = remember(updateInfo.releaseNotes) {
        ReleaseNotesSummary.fromMarkdown(updateInfo.releaseNotes)
    }
    val emojiCatalog = remember { EmojiTemplateCatalog(Emoji.all()) }
    val processedNotes = remember(updateInfo.releaseNotes) {
        updateInfo.releaseNotes?.let(emojiCatalog::replaceShortcodes)
    }
    var showFullNotes by remember(updateInfo.versionCode) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = screenHeight * 0.48f, max = screenHeight * 0.78f)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        if (previewOnly) R.string.pxlnet_update_preview_title
                        else R.string.pxlnet_update_available_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pxlnet_update_version, updateInfo.versionName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.pxlnet_update_whats_new),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (preview.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pxlnet_update_no_notes),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            preview.forEach { item ->
                                Text("• $item", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                if (!processedNotes.isNullOrBlank()) {
                    TextButton(onClick = { showFullNotes = !showFullNotes }) {
                        Text(
                            stringResource(
                                if (showFullNotes) R.string.pxlnet_update_hide_details
                                else R.string.pxlnet_update_show_details,
                            ),
                        )
                    }
                    AnimatedVisibility(visible = showFullNotes) {
                        MarkdownText(
                            markdown = processedNotes,
                            modifier = Modifier.padding(bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (previewOnly) {
                Text(
                    stringResource(R.string.pxlnet_update_preview_not_installable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(
                            if (alreadyDownloaded) R.string.pxlnet_update_install
                            else R.string.pxlnet_update_download_and_install,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (previewOnly) R.string.pxlnet_settings_close else R.string.pxlnet_update_later))
                }
                if (!previewOnly) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl)))
                    }) {
                        Text(stringResource(R.string.pxlnet_update_view_release))
                    }
                }
            }
        }
    }
}
