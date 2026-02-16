package eu.darken.apl.common.compose

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.feeder.ui.DestinationFeederList
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import eu.darken.apl.watch.ui.DestinationWatchList

val LocalIsInternetAvailable = staticCompositionLocalOf { true }

@Composable
fun BottomNavBar(
    selectedTab: Int,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavigationController.current ?: return
    val isInternetAvailable = LocalIsInternetAvailable.current

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
                selected = selectedTab == 0,
                onClick = { if (selectedTab != 0) navController.replace(DestinationMap()) },
                icon = { Icon(painterResource(R.drawable.ic_map_24), contentDescription = null) },
                label = { Text(stringResource(R.string.map_page_label)) },
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { if (selectedTab != 1) navController.replace(DestinationSearch()) },
                icon = { Icon(painterResource(R.drawable.ic_airplane_search_24), contentDescription = null) },
                label = { Text(stringResource(R.string.search_page_label)) },
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { if (selectedTab != 2) navController.replace(DestinationWatchList()) },
                icon = { Icon(painterResource(R.drawable.ic_watchlist_24), contentDescription = null) },
                label = { Text(stringResource(R.string.watch_list_page_label)) },
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { if (selectedTab != 3) navController.replace(DestinationFeederList) },
                icon = { Icon(painterResource(R.drawable.ic_settings_input_antenna_24), contentDescription = null) },
                label = { Text(stringResource(R.string.feeder_page_label)) },
            )
        }
    }
}
