package eu.darken.apl.main.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material.icons.twotone.TravelExplore
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import eu.darken.apl.common.navigation.LocalNavigationController
import eu.darken.apl.common.navigation.NavTab
import kotlinx.coroutines.delay

val LocalIsInternetAvailable = staticCompositionLocalOf { true }
val LocalIsFeederTabVisible = compositionLocalOf { true }

private const val UNTIL_RECHECK_MS = 60_000L

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavigationController.current ?: return
    val isInternetAvailable = LocalIsInternetAvailable.current
    val isFeederTabVisible = LocalIsFeederTabVisible.current
    val slotState = LocalStatusSlotState.current
    val slotActions = LocalStatusSlotActions.current
    val shownUntil = slotState.shownWhen(isInternetAvailable).until
    // "back around 14:35" has to become "back shortly" once 14:35 passes, even without a new answer
    val now by produceState(initialValue = slotActions.now(), shownUntil, slotActions) {
        value = slotActions.now()
        if (shownUntil == null) return@produceState
        while (value < shownUntil) {
            delay(UNTIL_RECHECK_MS)
            value = slotActions.now()
        }
    }

    Column(modifier = modifier) {
        StatusSlot(
            state = slotState,
            isInternetAvailable = isInternetAvailable,
            now = now,
            onDismiss = slotActions.onDismiss,
        )
        ShortNavigationBar {
            ShortNavigationBarItem(
                selected = selectedTab == MainTab.MAP,
                onClick = { navController.selectTab(MainTab.MAP) },
                icon = { Icon(Icons.TwoTone.Map, contentDescription = null) },
                label = { Text(stringResource(R.string.map_page_label)) },
            )
            ShortNavigationBarItem(
                selected = selectedTab == MainTab.SEARCH,
                onClick = { navController.selectTab(MainTab.SEARCH) },
                icon = { Icon(Icons.TwoTone.TravelExplore, contentDescription = null) },
                label = { Text(stringResource(R.string.search_page_label)) },
            )
            ShortNavigationBarItem(
                selected = selectedTab == MainTab.WATCH,
                onClick = { navController.selectTab(MainTab.WATCH) },
                icon = { Icon(Icons.TwoTone.Visibility, contentDescription = null) },
                label = { Text(stringResource(R.string.watch_list_nav_label)) },
            )
            if (isFeederTabVisible || selectedTab == MainTab.FEEDER) {
                ShortNavigationBarItem(
                    selected = selectedTab == MainTab.FEEDER,
                    onClick = { navController.selectTab(MainTab.FEEDER) },
                    icon = { Icon(Icons.TwoTone.SettingsInputAntenna, contentDescription = null) },
                    label = { Text(stringResource(R.string.feeder_page_label)) },
                )
            }
        }
    }
}
