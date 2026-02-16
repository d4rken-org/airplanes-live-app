package eu.darken.apl.main.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsPreferenceItem

@Composable
fun SettingsIndexScreenHost(
    vm: SettingsIndexViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    SettingsIndexScreen(
        onBack = { vm.navUp() },
        onGeneralSettings = { vm.goGeneralSettings() },
        onMapSettings = { vm.goMapSettings() },
        onWatchSettings = { vm.goWatchSettings() },
        onFeederSettings = { vm.goFeederSettings() },
        onSponsor = { vm.goSponsor() },
        onChangelog = { vm.goChangelog() },
        onSupport = { vm.goSupport() },
        onAcknowledgements = { vm.goAcknowledgements() },
        onPrivacyPolicy = { vm.goPrivacyPolicy() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsIndexScreen(
    onBack: () -> Unit,
    onGeneralSettings: () -> Unit,
    onMapSettings: () -> Unit,
    onWatchSettings: () -> Unit,
    onFeederSettings: () -> Unit,
    onSponsor: () -> Unit,
    onChangelog: () -> Unit,
    onSupport: () -> Unit,
    onAcknowledgements: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(R.string.label_settings)) },
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
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.general_settings_label),
                    summary = stringResource(R.string.general_settings_desc),
                    onClick = onGeneralSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.map_settings_title),
                    summary = stringResource(R.string.map_settings_summary),
                    onClick = onMapSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.watch_settings_title),
                    summary = stringResource(R.string.watch_settings_summary),
                    onClick = onWatchSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.feeder_settings_title),
                    summary = stringResource(R.string.feeder_settings_summary),
                    onClick = onFeederSettings,
                )
            }

            item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.common_sponsor_action),
                    summary = stringResource(R.string.common_sponsor_description),
                    onClick = onSponsor,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.changelog_label),
                    summary = BuildConfigWrap.VERSION_DESCRIPTION,
                    onClick = onChangelog,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_support_label),
                    summary = "¯\\_(ツ)_/¯",
                    onClick = onSupport,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_acknowledgements_label),
                    summary = stringResource(R.string.general_thank_you_label),
                    onClick = onAcknowledgements,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_privacy_policy_label),
                    summary = stringResource(R.string.settings_privacy_policy_desc),
                    onClick = onPrivacyPolicy,
                )
            }
        }
    }
}
