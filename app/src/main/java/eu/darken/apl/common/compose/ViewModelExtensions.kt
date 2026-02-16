package eu.darken.apl.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun <T> Flow<T?>.waitForState(): State<T?> = produceState<T?>(initialValue = null) {
    filterNotNull().collect { value = it }
}
