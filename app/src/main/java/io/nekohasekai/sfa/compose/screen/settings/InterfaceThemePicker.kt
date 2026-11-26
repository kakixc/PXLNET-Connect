package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import io.nekohasekai.sfa.compose.component.pxlPressMotion
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.utils.PxlUiPreferences
import io.nekohasekai.sfa.utils.PxlUiPreferences.InterfaceTheme
import io.nekohasekai.sfa.utils.PxlUiPreferences.FocusDetails

enum class AppearanceSection { ALL, THEME, CUSTOMIZE, SHARED }

@Composable
internal fun InterfaceThemePicker(theme: InterfaceTheme, section: AppearanceSection, classicSettings: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance by PxlUiPreferences.focusAppearance.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = InterfaceTheme.entries.firstOrNull { it.name == pendingName } ?: theme
    val choosing = section == AppearanceSection.ALL || section == AppearanceSection.THEME
    if (choosing) {
        Text(stringResource(R.string.focus_theme), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InterfaceTheme.entries.filter { it.available }.forEach { option ->
                val focus = option != InterfaceTheme.CLASSIC
                val trial = option == InterfaceTheme.TRIAL
                val selected = pending == option
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    label = "theme-preview-selection",
                )
                Surface(
                    modifier = Modifier.weight(1f).pxlPressMotion(pressedScale = 0.98f),
                    shape = RoundedCornerShape(20.dp),
                    color = tint,
                ) {
                    Column(
                        Modifier.selectable(selected, role = Role.RadioButton) {
                            pendingName = option.name
                        }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth(if (focus) .65f else .95f).height(5.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)))
                            if (focus && !trial) Box(Modifier.fillMaxWidth(.85f).height(8.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)))
                            if (trial) Box(Modifier.fillMaxWidth().height(14.dp))
                            Box(Modifier.width(if (focus) 60.dp else 32.dp).height(if (focus) 14.dp else 32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)))
                            if (trial) {
                                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                            } else if (!focus) Box(Modifier.fillMaxWidth(.9f).height(9.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)))
                        }
                        Text(stringResource(option.titleResource()), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (trial) R.string.focus_trial_preview else if (focus) R.string.focus_focus_preview else R.string.focus_classic_preview), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    AnimatedVisibility(choosing && pending != theme) {
        Button(
            onClick = {
                PxlUiPreferences.setInterfaceTheme(context, pending)
                pendingName = null
                expanded = false
            },
            modifier = Modifier.fillMaxWidth().pxlPressMotion(),
        ) { Text(stringResource(R.string.focus_apply_theme)) }
    }
    if (choosing) OutlinedButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().pxlPressMotion(),
    ) { Text(stringResource(theme.customizeResource())) }
    val showCustomization = section == AppearanceSection.CUSTOMIZE || (choosing && expanded)
    AnimatedVisibility(showCustomization && theme == InterfaceTheme.CLASSIC) { Column { classicSettings() } }
    AnimatedVisibility(showCustomization && theme != InterfaceTheme.CLASSIC) {
        Column(Modifier.padding(vertical = 8.dp)) {
            FocusOptions(R.string.focus_home_layout, listOf(true to R.string.focus_mascot, false to R.string.focus_compact), appearance.mascot) {
                PxlUiPreferences.setFocusAppearance(context, appearance.copy(mascot = it))
            }
            FocusOptions(R.string.focus_details, listOf(
                FocusDetails.ROUTING to R.string.focus_traffic,
                FocusDetails.SUBSCRIPTION to R.string.focus_subscription,
                FocusDetails.HIDDEN to R.string.focus_hidden,
            ), appearance.details) {
                PxlUiPreferences.setFocusAppearance(context, appearance.copy(details = it))
            }
            FocusOptions(R.string.focus_navigation, listOf(true to R.string.focus_floating, false to R.string.focus_standard), appearance.floating) {
                PxlUiPreferences.setFocusAppearance(context, appearance.copy(floating = it))
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_speed_option)) },
                trailingContent = {
                    Switch(checked = appearance.showSpeed, onCheckedChange = {
                        PxlUiPreferences.setFocusAppearance(context, appearance.copy(showSpeed = it))
                    })
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_label_option)) },
                trailingContent = {
                    Switch(checked = appearance.showNavLabels, onCheckedChange = {
                        PxlUiPreferences.setFocusAppearance(context, appearance.copy(showNavLabels = it))
                    })
                },
            )
            TextButton(onClick = { PxlUiPreferences.setFocusAppearance(context, PxlUiPreferences.FocusAppearance()) }, modifier = Modifier.pxlPressMotion()) {
                Text(stringResource(R.string.focus_reset))
            }
        }
    }
    if (section == AppearanceSection.ALL) Text(stringResource(R.string.focus_general), Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun <T> FocusOptions(title: Int, options: List<Pair<T, Int>>, current: T, onSelect: (T) -> Unit) {
    Text(stringResource(title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
    options.forEach { (value, label) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).pxlPressMotion(pressedScale = 0.98f).selectable(value == current, role = Role.RadioButton) { onSelect(value) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = value == current, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
            Text(stringResource(label))
        }
    }
}


internal fun InterfaceTheme.titleResource() = when (this) {
    InterfaceTheme.CLASSIC -> R.string.focus_classic
    InterfaceTheme.FOCUS -> R.string.focus_focus
    InterfaceTheme.TRIAL -> R.string.focus_trial
}

internal fun InterfaceTheme.customizeResource() = when (this) {
    InterfaceTheme.CLASSIC -> R.string.focus_custom_classic
    InterfaceTheme.FOCUS, InterfaceTheme.TRIAL -> R.string.focus_custom_focus
}
