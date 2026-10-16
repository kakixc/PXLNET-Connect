package io.nekohasekai.sfa.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.theme.SFATheme
import io.nekohasekai.sfa.utils.PxlUiPreferences
import io.nekohasekai.sfa.utils.PxlUiPreferences.WidgetColorStyle
import kotlinx.coroutines.launch

/** The launcher's one-time configuration screen; colors are shared by all PXLNET widgets. */
class PxlConnectWidgetConfigureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_CANCELED, result)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            provider != ComponentName(this, PxlConnectWidgetProvider::class.java)
        ) {
            finish()
            return
        }

        setContent {
            var selectedStyle by remember { mutableStateOf(
                WidgetColorStyle.forAndroidVersion(PxlUiPreferences.readWidgetColorStyle(this), Build.VERSION.SDK_INT)
            ) }
            var saving by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }

            SFATheme {
                WidgetColorChooser(
                    selectedStyle = selectedStyle,
                    saving = saving,
                    failed = failed,
                    onSelect = { selectedStyle = it },
                    onCancel = { finish() },
                    onAdd = {
                        if (!saving) {
                            saving = true
                            failed = false
                            val previous = PxlUiPreferences.readWidgetColorStyle(this)
                            lifecycleScope.launch {
                                try {
                                    PxlUiPreferences.setWidgetColorStyle(this@PxlConnectWidgetConfigureActivity, selectedStyle)
                                    PxlConnectWidgetProvider.updateAllNow(this@PxlConnectWidgetConfigureActivity, newWidgetId = widgetId)
                                    setResult(Activity.RESULT_OK, result)
                                    finish()
                                } catch (_: Exception) {
                                    PxlUiPreferences.setWidgetColorStyle(this@PxlConnectWidgetConfigureActivity, previous)
                                    PxlConnectWidgetProvider.updateAll(this@PxlConnectWidgetConfigureActivity)
                                    failed = true
                                    saving = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WidgetColorChooser(
    selectedStyle: WidgetColorStyle,
    saving: Boolean,
    failed: Boolean,
    onSelect: (WidgetColorStyle) -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.pxlnet_widget_config_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.pxlnet_widget_config_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WidgetColorOption(
                title = stringResource(R.string.pxlnet_widget_config_classic),
                description = stringResource(R.string.pxlnet_widget_config_classic_summary),
                selected = selectedStyle == WidgetColorStyle.CLASSIC,
                enabled = !saving,
                swatch = Color(ContextCompat.getColor(context, R.color.pxlnet_widget_surface)),
                onClick = { onSelect(WidgetColorStyle.CLASSIC) },
            )
            WidgetColorOption(
                title = stringResource(R.string.pxlnet_widget_config_monet),
                description = stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        R.string.pxlnet_widget_config_monet_summary
                    } else {
                        R.string.pxlnet_settings_widget_dynamic_color_unavailable
                    },
                ),
                selected = selectedStyle == WidgetColorStyle.MONET,
                enabled = !saving && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                swatch = Color(ContextCompat.getColor(context, R.color.pxlnet_widget_monet_surface)),
                onClick = { onSelect(WidgetColorStyle.MONET) },
            )
            WidgetColorOption(
                title = stringResource(R.string.pxlnet_widget_config_monet_soft),
                description = stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        R.string.pxlnet_widget_config_monet_soft_summary
                    } else {
                        R.string.pxlnet_settings_widget_dynamic_color_unavailable
                    },
                ),
                selected = selectedStyle == WidgetColorStyle.MONET_SOFT,
                enabled = !saving && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                swatch = Color(ContextCompat.getColor(context, R.color.pxlnet_widget_soft_surface)),
                onClick = { onSelect(WidgetColorStyle.MONET_SOFT) },
            )
            if (failed) {
                Text(stringResource(R.string.pxlnet_widget_config_failed), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !saving) {
                    Text(stringResource(R.string.pxlnet_widget_config_cancel))
                }
                Button(onClick = onAdd, enabled = !saving) {
                    Text(stringResource(R.string.pxlnet_widget_config_add))
                }
            }
        }
    }
}

@Composable
private fun WidgetColorOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    swatch: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(32.dp).background(swatch, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}
