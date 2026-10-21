package eu.darken.apl.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import eu.darken.apl.main.ui.DestinationMain

/** One back stack per tab, so leaving a tab keeps its screens, and with them their state. */
class NavigationState(
    val bootstrap: NavBackStack<NavKey> = NavBackStack(DestinationMain),
    val tabs: Map<MainTab, NavBackStack<NavKey>> = MainTab.entries.associateWith { NavBackStack(it.root) },
    currentTab: MutableState<MainTab> = mutableStateOf(MainTab.MAP),
    returnTab: MutableState<MainTab?> = mutableStateOf(null),
    inTabs: MutableState<Boolean> = mutableStateOf(false),
) {
    var currentTab by currentTab

    /** Where back goes from the root of [currentTab] after another tab asked to show something there. */
    var returnTab by returnTab

    /** False while [bootstrap] is shown. */
    var inTabs by inTabs

    fun stack(tab: MainTab): NavBackStack<NavKey> = tabs.getValue(tab)

    /** The tab that back from the root of [currentTab] goes to, null on the map's root. */
    fun backTarget(): MainTab? = returnTab?.takeIf { it != currentTab } ?: MainTab.MAP.takeIf { it != currentTab }
}

@Composable
fun rememberNavigationState(): NavigationState {
    val bootstrap = rememberNavBackStack(DestinationMain)
    val map = rememberNavBackStack(MainTab.MAP.root)
    val search = rememberNavBackStack(MainTab.SEARCH.root)
    val watch = rememberNavBackStack(MainTab.WATCH.root)
    val feeder = rememberNavBackStack(MainTab.FEEDER.root)
    val currentTab = rememberSaveable { mutableStateOf(MainTab.MAP) }
    val returnTab = rememberSaveable { mutableStateOf<MainTab?>(null) }
    val inTabs = rememberSaveable { mutableStateOf(false) }
    return remember {
        NavigationState(
            bootstrap = bootstrap,
            tabs = mapOf(
                MainTab.MAP to map,
                MainTab.SEARCH to search,
                MainTab.WATCH to watch,
                MainTab.FEEDER to feeder,
            ),
            currentTab = currentTab,
            returnTab = returnTab,
            inTabs = inTabs,
        )
    }
}
