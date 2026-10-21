package eu.darken.apl.main.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material.icons.twotone.TravelExplore
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavTab
import eu.darken.apl.main.ui.settings.DestinationSettingsIndex

val LocalIsInternetAvailable = staticCompositionLocalOf { true }
val LocalIsFeederTabVisible = compositionLocalOf { true }

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavigationController.current ?: return
    val isInternetAvailable = LocalIsInternetAvailable.current
    val isFeederTabVisible = LocalIsFeederTabVisible.current

    Column(modifier = modifier) {
        AnimatedVisibility(visible = !isInternetAvailable) {
            Text(
                text = stringResource(R.string.common_internet_unavailable_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == MainTab.MAP,
                onClick = { navController.selectTab(MainTab.MAP) },
                icon = { Icon(Icons.TwoTone.Map, contentDescription = null) },
                label = { Text(stringResource(R.string.map_page_label)) },
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.SEARCH,
                onClick = { navController.selectTab(MainTab.SEARCH) },
                icon = { Icon(Icons.TwoTone.TravelExplore, contentDescription = null) },
                label = { Text(stringResource(R.string.search_page_label)) },
            )
            NavigationBarItem(
                selected = selectedTab == MainTab.WATCH,
                onClick = { navController.selectTab(MainTab.WATCH) },
                icon = { Icon(Icons.TwoTone.Visibility, contentDescription = null) },
                label = { Text(stringResource(R.string.watch_list_page_label)) },
            )
            if (isFeederTabVisible || selectedTab == MainTab.FEEDER) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.FEEDER,
                    onClick = { navController.selectTab(MainTab.FEEDER) },
                    icon = { Icon(Icons.TwoTone.SettingsInputAntenna, contentDescription = null) },
                    label = { Text(stringResource(R.string.feeder_page_label)) },
                )
            }
            NavigationBarItem(
                selected = false,
                onClick = { navController.goTo(DestinationSettingsIndex) },
                icon = { Icon(Icons.TwoTone.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.label_settings)) },
            )
        }
    }
}
