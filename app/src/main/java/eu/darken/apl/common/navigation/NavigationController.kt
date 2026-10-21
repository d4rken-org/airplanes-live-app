package eu.darken.apl.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.map.ui.DestinationMap
import eu.darken.apl.search.ui.DestinationSearch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationController @Inject constructor(
    private val tabRequests: TabRequests,
) {
    private var _state: NavigationState? = null

    val isReady: Boolean get() = _state != null

    private val state: NavigationState
        get() = _state ?: error("NavigationController not initialized")

    val currentTab: MainTab get() = state.currentTab

    private val activeStack: NavBackStack<NavKey>
        get() = if (state.inTabs) state.stack(state.currentTab) else state.bootstrap

    fun setup(state: NavigationState) {
        log(TAG) { "setup()" }
        _state = state
    }

    fun enterTabs() {
        log(TAG) { "enterTabs() at ${state.currentTab}" }
        state.inTabs = true
        while (state.bootstrap.size > 1) state.bootstrap.removeLastOrNull()
    }

    fun selectTab(tab: MainTab) {
        if (tab == state.currentTab) return
        log(TAG) { "selectTab($tab)" }
        state.returnTab = null
        state.currentTab = tab
    }

    fun up(): Boolean {
        val stack = activeStack
        if (stack.size > 1) {
            val removed = stack.removeLastOrNull()
            log(TAG) { "up() to ${stack.lastOrNull()} (removed $removed)" }
            return true
        }
        if (!state.inTabs) {
            log(TAG) { "up() prevented removing the last element in backstack" }
            return false
        }
        val target = state.backTarget() ?: return false
        log(TAG) { "up() from ${state.currentTab} to $target" }
        state.returnTab = null
        state.currentTab = target
        return true
    }

    fun goTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(TAG) { "goTo($destination, popUpTo=$popUpTo, inclusive=$inclusive)" }

        if (destination !is BootstrapDestination) {
            MainTab.of(destination)?.let {
                showTab(it, destination)
                return
            }
        }

        val stack = if (destination is BootstrapDestination) state.bootstrap else state.stack(state.currentTab)

        if (popUpTo != null) {
            if (stack.none { it == popUpTo }) {
                log(TAG, WARN) { "popUpTo target $popUpTo not found in stack, skipping pop" }
            } else {
                while (stack.isNotEmpty() && stack.last() != popUpTo) {
                    val removed = stack.removeLastOrNull()
                    log(TAG) { "Popping $removed while looking for $popUpTo" }
                }
                if (inclusive && stack.isNotEmpty() && stack.last() == popUpTo) {
                    val removed = stack.removeLastOrNull()
                    log(TAG) { "Popping $removed (inclusive)" }
                }
            }
        }

        if (stack.lastOrNull() == destination) {
            log(TAG, WARN) { "goTo() ignored duplicate destination: $destination" }
            return
        }

        stack.add(destination)
    }

    /** Shows the tab's own screen, the screens above it go, and applies the request [destination] carries. */
    private fun showTab(tab: MainTab, destination: NavigationDestination) {
        if (state.inTabs && tab != state.currentTab) state.returnTab = state.currentTab
        val stack = state.stack(tab)
        while (stack.size > 1) {
            val removed = stack.removeLastOrNull()
            log(TAG) { "Popping $removed to show $tab" }
        }
        state.currentTab = tab

        when (destination) {
            is DestinationMap -> destination.mapOptions?.let { tabRequests.map.publish(it) }
            is DestinationSearch -> if (destination != MainTab.SEARCH.root) tabRequests.search.publish(destination)
        }
    }

    companion object {
        private val TAG = logTag("Navigation", "Controller")
    }
}
