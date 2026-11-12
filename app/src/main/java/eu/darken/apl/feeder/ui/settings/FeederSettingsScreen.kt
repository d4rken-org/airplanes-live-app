package eu.darken.apl.feeder.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.ReportProblem
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsSwitchItem

@Composable
fun FeederSettingsScreenHost(
    vm: FeederSettingsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        FeederSettingsScreen(
            state = it,
            onBack = { vm.navUp() },
            onNavTabVisibleChange = { vm.setNavTabVisible(it) },
            onNotifyProblemsChange = { vm.setNotifyProblems(it) },
        )
    }
}

@Composable
fun FeederSettingsScreen(
    state: FeederSettingsViewModel.State,
    onBack: () -> Unit,
    onNavTabVisibleChange: (Boolean) -> Unit,
    onNotifyProblemsChange: (Boolean) -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeder_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = stringResource(R.string.common_back_action))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            if (state.isServerMode) {
                item { SettingsCategoryHeader(title = stringResource(R.string.feeder_settings_category_monitoring_label)) }

                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.feeder_settings_problem_alerts_title),
                        summary = stringResource(R.string.feeder_settings_problem_alerts_summary),
                        checked = state.notifyProblems,
                        icon = Icons.TwoTone.ReportProblem,
                        onCheckedChange = onNotifyProblemsChange,
                    )
                }
            }

            item { SettingsCategoryHeader(title = stringResource(R.string.feeder_settings_category_navigation_label)) }

            item {
                SettingsSwitchItem(
                    title = stringResource(R.string.feeder_settings_navtab_visible_title),
                    summary = stringResource(R.string.feeder_settings_navtab_visible_summary),
                    checked = state.isNavTabVisible,
                    icon = Icons.TwoTone.SettingsInputAntenna,
                    onCheckedChange = onNavTabVisibleChange,
                )
            }
        }
    }
}
