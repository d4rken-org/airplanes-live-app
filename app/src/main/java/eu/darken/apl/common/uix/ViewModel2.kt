package eu.darken.apl.common.uix

import androidx.lifecycle.viewModelScope
import eu.darken.apl.common.coroutine.DefaultDispatcherProvider
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext


abstract class ViewModel2(
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    override val tag: String,
) : ViewModel1(tag = tag) {

    val vmScope = viewModelScope + dispatcherProvider.Default

    abstract var launchErrorHandler: CoroutineExceptionHandler?

    private fun getVMContext(): CoroutineContext {
        val dispatcher = dispatcherProvider.Default
        return launchErrorHandler?.let { dispatcher + it } ?: dispatcher
    }

    fun launch(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = getVMContext(),
        block: suspend CoroutineScope.() -> Unit
    ) {
        try {
            scope.launch(context = context, block = block)
        } catch (e: CancellationException) {
            log(tag, WARN) { "launch()ed coroutine was canceled (scope=$scope): ${e.asLog()}" }
        }
    }

    open fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(tag) { "launchInViewModel()" }
        .launchIn(vmScope)

    fun <T> Flow<T>.asStateFlow(defaultValue: T? = null): Flow<T> = stateIn(
        vmScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = defaultValue,
    ).mapNotNull { it }
}