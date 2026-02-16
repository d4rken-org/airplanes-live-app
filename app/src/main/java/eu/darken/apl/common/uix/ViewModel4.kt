package eu.darken.apl.common.uix

import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.error.ErrorEventSource
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.navigation.NavEvent
import eu.darken.apl.common.navigation.NavigationDestination
import eu.darken.apl.common.navigation.NavigationEventSource
import kotlinx.coroutines.CoroutineExceptionHandler

abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
    override val tag: String = defaultTag(),
) : ViewModel2(dispatcherProvider, tag), ErrorEventSource, NavigationEventSource {

    override val errorEvents = SingleEventFlow<Throwable>()
    override val navEvents = SingleEventFlow<NavEvent>()

    override var launchErrorHandler: CoroutineExceptionHandler? = CoroutineExceptionHandler { _, ex ->
        log(tag) { "Error during launch: ${ex.asLog()}" }
        errorEvents.emitBlocking(ex)
    }

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(tag) { "navTo($destination)" }
        navEvents.tryEmit(NavEvent.GoTo(destination, popUpTo, inclusive))
    }

    fun navUp() {
        log(tag) { "navUp()" }
        navEvents.tryEmit(NavEvent.Up)
    }

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM4"
    }
}
