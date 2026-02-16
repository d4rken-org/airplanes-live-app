package eu.darken.apl.main.ui.settings.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemeStyle

@Composable
fun GeneralSettingsScreenHost(
    vm: GeneralSettingsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        GeneralSettingsScreen(
            state = it,
            onBack = { vm.navUp() },
            onThemeModeChange = { mode -> vm.setThemeMode(mode) },
            onThemeStyleChange = { style -> vm.setThemeStyle(style) },
            onToggleUpdateCheck = { vm.toggleUpdateCheck() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsViewModel.State,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    onToggleUpdateCheck: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_ui_label)) }

            item {
                EnumDropdownItem(
                    title = stringResource(R.string.ui_theme_mode_setting_label),
                    summary = stringResource(R.string.ui_theme_mode_setting_explanation),
                    currentValue = state.themeMode,
                    values = ThemeMode.entries,
                    onValueChange = onThemeModeChange,
                )
            }

            item {
                EnumDropdownItem(
                    title = stringResource(R.string.ui_theme_style_setting_label),
                    summary = stringResource(R.string.ui_theme_style_setting_explanation),
                    currentValue = state.themeStyle,
                    values = ThemeStyle.entries,
                    onValueChange = onThemeStyleChange,
                )
            }

            if (state.isUpdateCheckSupported) {
                item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.updatecheck_setting_enabled_label),
                        summary = stringResource(R.string.updatecheck_setting_enabled_explanation),
                        checked = state.isUpdateCheckEnabled,
                        onCheckedChange = { onToggleUpdateCheck() },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> EnumDropdownItem(
    title: String,
    summary: String,
    currentValue: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
) where T : Enum<T>, T : eu.darken.apl.common.preferences.EnumPreference<T> {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(currentValue.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(value.labelRes)) },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
