package eu.darken.apl.main.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.automirrored.twotone.ContactSupport
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.NewReleases
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.PrivacyTip
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.github.GithubApi
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsPreferenceItem

@Composable
fun SettingsIndexScreenHost(
    vm: SettingsIndexViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val newRelease by vm.newRelease.collectAsState(initial = null)

    SettingsIndexScreen(
        newRelease = newRelease,
        onBack = { vm.navUp() },
        onGeneralSettings = { vm.goGeneralSettings() },
        onMapSettings = { vm.goMapSettings() },
        onWatchSettings = { vm.goWatchSettings() },
        onFeederSettings = { vm.goFeederSettings() },
        onSponsor = { vm.goSponsor() },
        onChangelog = { vm.goChangelog() },
        onUpdate = { vm.openUpdate(it) },
        onSupport = { vm.goSupport() },
        onAcknowledgements = { vm.goAcknowledgements() },
        onPrivacyPolicy = { vm.goPrivacyPolicy() },
    )
}

@Composable
fun SettingsIndexScreen(
    newRelease: GithubApi.ReleaseInfo? = null,
    onBack: () -> Unit,
    onGeneralSettings: () -> Unit,
    onMapSettings: () -> Unit,
    onWatchSettings: () -> Unit,
    onFeederSettings: () -> Unit,
    onSponsor: () -> Unit,
    onChangelog: () -> Unit,
    onUpdate: (GithubApi.ReleaseInfo) -> Unit = {},
    onSupport: () -> Unit,
    onAcknowledgements: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(R.string.label_settings)) },
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
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.general_settings_label),
                    summary = stringResource(R.string.general_settings_desc),
                    icon = Icons.TwoTone.Tune,
                    onClick = onGeneralSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.map_settings_title),
                    summary = stringResource(R.string.map_settings_summary),
                    icon = Icons.TwoTone.Map,
                    onClick = onMapSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.watch_settings_title),
                    summary = stringResource(R.string.watch_settings_summary),
                    icon = Icons.TwoTone.Notifications,
                    onClick = onWatchSettings,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.feeder_settings_title),
                    summary = stringResource(R.string.feeder_settings_summary),
                    icon = Icons.TwoTone.CellTower,
                    onClick = onFeederSettings,
                )
            }

            item { SettingsCategoryHeader(title = stringResource(R.string.settings_category_other_label)) }

            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.common_sponsor_action),
                    summary = stringResource(R.string.common_sponsor_description),
                    icon = Icons.TwoTone.Favorite,
                    onClick = onSponsor,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.changelog_label),
                    summary = BuildConfigWrap.VERSION_DESCRIPTION,
                    icon = Icons.TwoTone.NewReleases,
                    onClick = onChangelog,
                )
            }
            if (newRelease != null) {
                item {
                    UpdateAvailableCard(
                        currentVersion = BuildConfigWrap.VERSION_NAME,
                        latestVersion = newRelease.tagName,
                        onClick = { onUpdate(newRelease) },
                    )
                }
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_support_label),
                    summary = "¯\\_(ツ)_/¯",
                    icon = Icons.AutoMirrored.TwoTone.ContactSupport,
                    onClick = onSupport,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_acknowledgements_label),
                    summary = stringResource(R.string.general_thank_you_label),
                    icon = Icons.TwoTone.Groups,
                    onClick = onAcknowledgements,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.settings_privacy_policy_label),
                    summary = stringResource(R.string.settings_privacy_policy_desc),
                    icon = Icons.TwoTone.PrivacyTip,
                    onClick = onPrivacyPolicy,
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    currentVersion: String,
    latestVersion: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = stringResource(R.string.update_available_settings_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "$currentVersion → $latestVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
