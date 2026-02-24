package eu.darken.apl.main.ui.settings.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsSwitchItem
import android.os.Build
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColor
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
            onThemeColorChange = { color -> vm.setThemeColor(color) },
            onToggleUpdateCheck = { vm.toggleUpdateCheck() },
        )
    }
}

@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsViewModel.State,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    onThemeColorChange: (ThemeColor) -> Unit,
    onToggleUpdateCheck: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
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
                    icon = Icons.Outlined.DarkMode,
                    currentValue = state.themeMode,
                    values = ThemeMode.entries,
                    onValueChange = onThemeModeChange,
                )
            }

            item {
                EnumDropdownItem(
                    title = stringResource(R.string.ui_theme_style_setting_label),
                    summary = stringResource(R.string.ui_theme_style_setting_explanation),
                    icon = Icons.Outlined.Palette,
                    currentValue = state.themeStyle,
                    values = ThemeStyle.entries,
                    onValueChange = onThemeStyleChange,
                )
            }

            item {
                val isMaterialYouActive = state.themeStyle == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31
                EnumDropdownItem(
                    title = stringResource(R.string.ui_theme_color_setting_label),
                    icon = Icons.Outlined.ColorLens,
                    summary = if (isMaterialYouActive) {
                        stringResource(R.string.ui_theme_color_setting_disabled_materialyou)
                    } else {
                        stringResource(R.string.ui_theme_color_setting_explanation)
                    },
                    currentValue = state.themeColor,
                    values = ThemeColor.entries,
                    onValueChange = onThemeColorChange,
                    enabled = !isMaterialYouActive,
                    displayValueOverride = if (isMaterialYouActive) {
                        stringResource(R.string.ui_theme_color_value_system)
                    } else {
                        null
                    },
                )
            }

            if (state.isUpdateCheckSupported) {
                item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.updatecheck_setting_enabled_label),
                        summary = stringResource(R.string.updatecheck_setting_enabled_explanation),
                        checked = state.isUpdateCheckEnabled,
                        icon = Icons.Outlined.SystemUpdate,
                        onCheckedChange = { onToggleUpdateCheck() },
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreview() {
    PreviewWrapper {
        GeneralSettingsScreen(
            state = GeneralSettingsViewModel.State(
                themeMode = ThemeMode.SYSTEM,
                themeStyle = ThemeStyle.DEFAULT,
                themeColor = ThemeColor.BLUE,
                isUpdateCheckEnabled = true,
                isUpdateCheckSupported = true,
            ),
            onBack = {},
            onThemeModeChange = {},
            onThemeStyleChange = {},
            onThemeColorChange = {},
            onToggleUpdateCheck = {},
        )
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenMaterialYouPreview() {
    PreviewWrapper {
        GeneralSettingsScreen(
            state = GeneralSettingsViewModel.State(
                themeMode = ThemeMode.SYSTEM,
                themeStyle = ThemeStyle.MATERIAL_YOU,
                themeColor = ThemeColor.BLUE,
                isUpdateCheckEnabled = false,
                isUpdateCheckSupported = false,
            ),
            onBack = {},
            onThemeModeChange = {},
            onThemeStyleChange = {},
            onThemeColorChange = {},
            onToggleUpdateCheck = {},
        )
    }
}

@Composable
private fun <T> EnumDropdownItem(
    title: String,
    summary: String,
    currentValue: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    displayValueOverride: String? = null,
) where T : Enum<T>, T : eu.darken.apl.common.preferences.EnumPreference<T> {
    var expanded by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
            Text(
                text = displayValueOverride ?: stringResource(currentValue.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
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
