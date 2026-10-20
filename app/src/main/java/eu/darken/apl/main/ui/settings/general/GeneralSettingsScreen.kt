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
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.ColorLens
import androidx.compose.material.icons.twotone.DarkMode
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.SystemUpdate
import androidx.compose.material.icons.twotone.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import eu.darken.apl.common.settings.CustomThemeDialog
import eu.darken.apl.common.settings.SettingsBaseItem
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsSwitchItem
import eu.darken.apl.common.settings.ThemeColorSelectorDialog
import android.os.Build
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemePalette
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.main.core.ThemeState

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
            onThemeCustomChange = { seed, palette -> vm.setThemeCustom(seed, palette) },
            onUpgrade = { vm.goUpgrade() },
            onToggleUpdateCheck = { vm.toggleUpdateCheck() },
            onToggleFakeUpgrade = { vm.toggleFakeUpgrade() },
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
    onThemeCustomChange: (Int?, ThemePalette) -> Unit,
    onUpgrade: () -> Unit,
    onToggleUpdateCheck: () -> Unit,
    onToggleFakeUpgrade: () -> Unit,
) {
    var showThemeColorDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomThemeDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
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
                    icon = Icons.TwoTone.DarkMode,
                    currentValue = state.themeMode,
                    values = ThemeMode.entries,
                    onValueChange = onThemeModeChange,
                )
            }

            item {
                EnumDropdownItem(
                    title = stringResource(R.string.ui_theme_style_setting_label),
                    summary = stringResource(R.string.ui_theme_style_setting_explanation),
                    icon = Icons.TwoTone.Palette,
                    currentValue = state.themeStyle,
                    values = ThemeStyle.entries,
                    onValueChange = onThemeStyleChange,
                )
            }

            item {
                val isMaterialYouActive = state.themeStyle == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31
                ValueItem(
                    title = stringResource(R.string.ui_theme_color_setting_label),
                    icon = Icons.TwoTone.ColorLens,
                    summary = if (isMaterialYouActive) {
                        stringResource(R.string.ui_theme_color_setting_disabled_materialyou)
                    } else {
                        stringResource(R.string.ui_theme_color_setting_explanation)
                    },
                    value = if (isMaterialYouActive) {
                        stringResource(R.string.ui_theme_color_value_system)
                    } else {
                        stringResource(state.themeColor.labelRes)
                    },
                    onClick = { showThemeColorDialog = true },
                    enabled = !isMaterialYouActive,
                )
            }

            if (state.isUpdateCheckSupported) {
                item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.updatecheck_setting_enabled_label),
                        summary = stringResource(R.string.updatecheck_setting_enabled_explanation),
                        checked = state.isUpdateCheckEnabled,
                        icon = Icons.TwoTone.SystemUpdate,
                        onCheckedChange = { onToggleUpdateCheck() },
                    )
                }
            }

            if (state.isDebugBuild) {
                item { SettingsCategoryHeader(title = stringResource(R.string.debug_settings_category_label)) }

                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.debug_fake_upgrade_label),
                        summary = stringResource(R.string.debug_fake_upgrade_summary),
                        checked = state.isFakeUpgradeEnabled,
                        icon = Icons.TwoTone.BugReport,
                        onCheckedChange = { onToggleFakeUpgrade() },
                    )
                }
            }
        }
    }

    if (showThemeColorDialog) {
        ThemeColorSelectorDialog(
            title = stringResource(R.string.ui_theme_color_setting_label),
            selectedOption = state.themeColor,
            customSeed = ThemeState(customSeed = state.customSeed, customPalette = state.customPalette).customThemeSeed,
            isUpgraded = state.isUpgraded,
            onOptionSelected = { color ->
                showThemeColorDialog = false
                when {
                    color == ThemeColor.CUSTOM && !state.isUpgraded -> onUpgrade()
                    color == ThemeColor.CUSTOM -> {
                        onThemeColorChange(color)
                        showCustomThemeDialog = true
                    }

                    else -> onThemeColorChange(color)
                }
            },
            onDismiss = { showThemeColorDialog = false },
        )
    }

    if (showCustomThemeDialog) {
        CustomThemeDialog(
            seed = state.customSeed,
            palette = state.customPalette,
            style = state.themeStyle,
            onConfirm = { seed, palette ->
                onThemeCustomChange(seed, palette)
                showCustomThemeDialog = false
            },
            onDismiss = { showCustomThemeDialog = false },
        )
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
                customSeed = null,
                customPalette = ThemePalette.TONAL_SPOT,
                isUpdateCheckEnabled = true,
                isUpdateCheckSupported = true,
                isUpgraded = false,
                isDebugBuild = true,
                isFakeUpgradeEnabled = false,
            ),
            onBack = {},
            onThemeModeChange = {},
            onThemeStyleChange = {},
            onThemeColorChange = {},
            onThemeCustomChange = { _, _ -> },
            onUpgrade = {},
            onToggleUpdateCheck = {},
            onToggleFakeUpgrade = {},
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
                customSeed = null,
                customPalette = ThemePalette.TONAL_SPOT,
                isUpdateCheckEnabled = false,
                isUpdateCheckSupported = false,
                isUpgraded = false,
                isDebugBuild = false,
                isFakeUpgradeEnabled = false,
            ),
            onBack = {},
            onThemeModeChange = {},
            onThemeStyleChange = {},
            onThemeColorChange = {},
            onThemeCustomChange = { _, _ -> },
            onUpgrade = {},
            onToggleUpdateCheck = {},
            onToggleFakeUpgrade = {},
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
) where T : Enum<T>, T : eu.darken.apl.common.preferences.EnumPreference<T> {
    var expanded by remember { mutableStateOf(false) }

    ValueItem(
        title = title,
        summary = summary,
        value = stringResource(currentValue.labelRes),
        onClick = { expanded = true },
        icon = icon,
    ) {
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

@Composable
private fun ValueItem(
    title: String,
    summary: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    anchored: @Composable () -> Unit = {},
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
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
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            )
        }

        anchored()
    }
}
