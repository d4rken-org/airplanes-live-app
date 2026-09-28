package eu.darken.apl.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationController @Inject constructor() {
    private var _backStack: NavBackStack<NavKey>? = null

    val isReady: Boolean get() = _backStack != null

    private val backStack: NavBackStack<NavKey>
        get() = _backStack ?: error("NavigationController not initialized")

    fun setup(backStack: NavBackStack<NavKey>) {
        log(TAG) { "setup()" }
        _backStack = backStack
    }

    fun up(): Boolean {
        if (backStack.size <= 1) {
            log(TAG) { "up() prevented removing the last element in backstack" }
            return false
        }
        val removed = backStack.removeLastOrNull()
        log(TAG) { "up() to ${backStack.lastOrNull()} (removed $removed)" }
        return removed != null
    }

    fun goTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(TAG) { "goTo($destination, popUpTo=$popUpTo, inclusive=$inclusive)" }

        if (popUpTo != null) {
            if (backStack.none { it == popUpTo }) {
                log(TAG, WARN) { "popUpTo target $popUpTo not found in stack, skipping pop" }
            } else {
                while (backStack.isNotEmpty() && backStack.last() != popUpTo) {
                    val removed = backStack.removeLastOrNull()
                    log(TAG) { "Popping $removed while looking for $popUpTo" }
                }
                if (inclusive && backStack.isNotEmpty() && backStack.last() == popUpTo) {
                    val removed = backStack.removeLastOrNull()
                    log(TAG) { "Popping $removed (inclusive)" }
                }
            }
        }

        if (backStack.lastOrNull() == destination) {
            log(TAG, WARN) { "goTo() ignored duplicate destination: $destination" }
            return
        }

        removeOtherInstances(destination)
        backStack.add(destination)
    }

    fun replace(destination: NavigationDestination) {
        backStack.removeLastOrNull()
        removeOtherInstances(destination)
        backStack.add(destination)
    }

    private fun removeOtherInstances(destination: NavigationDestination) {
        if (destination !is SingleInstanceDestination) return
        var index = 0
        while (index < backStack.size) {
            if (backStack[index]::class != destination::class) {
                index++
                continue
            }
            log(TAG) { "Removing earlier instance ${backStack[index]}" }
            backStack.removeAt(index)
            while (index < backStack.size && backStack[index] is OverlayDestination) {
                log(TAG) { "Removing overlay ${backStack[index]} with it" }
                backStack.removeAt(index)
            }
        }
    }

    companion object {
        private val TAG = logTag("Navigation", "Controller")
    }
}
