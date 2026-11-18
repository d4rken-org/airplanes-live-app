package eu.darken.apl.common.uix

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.apl.common.debug.logging.log

abstract class ViewModel1(
    tag: String,
) : ViewModel() {

    open val tag: String = tag

    init {
        // Logs the constructor parameter: subclasses override the property and haven't initialized it yet
        log(tag) { "Initialized" }
    }

    @CallSuper
    override fun onCleared() {
        log(tag) { "onCleared()" }
        super.onCleared()
    }
}