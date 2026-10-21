package eu.darken.apl.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** One back stack per tab, so leaving a tab keeps its screens, and with them their state. */
class NavigationState(
    val setup: TabSetup,
    val bootstrap: NavBackStack<NavKey> = NavBackStack(setup.bootstrapRoot),
    val tabs: Map<NavTab, NavBackStack<NavKey>> = setup.tabs.associateWith { NavBackStack(it.root) },
    currentTabId: MutableState<String> = mutableStateOf(setup.startTab.id),
    returnTabId: MutableState<String?> = mutableStateOf(null),
    inTabs: MutableState<Boolean> = mutableStateOf(false),
) {
    init {
        // Two tabs with one id would share their saved state
        require(setup.tabs.map { it.id }.toSet().size == setup.tabs.size) { "Tab ids must be unique: ${setup.tabs}" }
        require(setup.startTab in setup.tabs) { "${setup.startTab} is not one of ${setup.tabs}" }
    }

    private var currentTabId by currentTabId
    private var returnTabId by returnTabId

    /** A saved id no tab has anymore starts over at [TabSetup.startTab]. */
    var currentTab: NavTab
        get() = tabById(currentTabId) ?: setup.startTab
        set(value) {
            currentTabId = value.id
        }

    /** Where back goes from the root of [currentTab] after another tab asked to show something there. */
    var returnTab: NavTab?
        get() = returnTabId?.let { tabById(it) }
        set(value) {
            returnTabId = value?.id
        }

    /** False while [bootstrap] is shown. */
    var inTabs by inTabs

    fun stack(tab: NavTab): NavBackStack<NavKey> = tabs.getValue(tab)

    /** The tab that back from the root of [currentTab] goes to, null on the start tab's root. */
    fun backTarget(): NavTab? = returnTab?.takeIf { it != currentTab } ?: setup.startTab.takeIf { it != currentTab }

    private fun tabById(id: String): NavTab? = setup.tabs.firstOrNull { it.id == id }
}

@Composable
fun rememberNavigationState(setup: TabSetup): NavigationState {
    val bootstrap = rememberNavBackStack(setup.bootstrapRoot)
    val tabs = setup.tabs.associateWith { tab -> key(tab.id) { rememberNavBackStack(tab.root) } }
    val currentTabId = rememberSaveable { mutableStateOf(setup.startTab.id) }
    val returnTabId = rememberSaveable { mutableStateOf<String?>(null) }
    val inTabs = rememberSaveable { mutableStateOf(false) }
    return remember(setup) {
        NavigationState(
            setup = setup,
            bootstrap = bootstrap,
            tabs = tabs,
            currentTabId = currentTabId,
            returnTabId = returnTabId,
            inTabs = inTabs,
        )
    }
}
