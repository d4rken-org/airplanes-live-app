package eu.darken.apl.main.core.update

import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether to ask the user to update, from the server's answer and what the user chose about it. */
@Singleton
class AppUpdatePrompt @Inject constructor(
    private val compatibility: AppCompatibility,
) {

    sealed interface Prompt {
        data class Recommended(val version: Int) : Prompt
        data object Required : Prompt
    }

    // In memory only, a new process asks again
    private val continuedLimited = MutableStateFlow(false)

    // Kept in memory too, so a dismissal that could not be stored still holds for this process
    private val dismissedHere = MutableStateFlow<Int?>(null)

    /** Silent until the stored answer is read, a null before that would claim there is nothing to ask. */
    val prompt: Flow<Prompt?> = combine(
        compatibility.state.filter { it != CompatState.Unknown },
        compatibility.dismissedRecommendation,
        dismissedHere,
        continuedLimited,
    ) { state, dismissedStored, dismissedInMemory, continued ->
        when (state) {
            CompatState.Unknown, CompatState.Ok -> null
            CompatState.UpdateRequired -> Prompt.Required.takeIf { !continued }
            is CompatState.UpdateRecommended -> state.recommendedVersion
                .takeIf { it != dismissedStored && it != dismissedInMemory }
                ?.let { Prompt.Recommended(it) }
        }
    }.distinctUntilChanged()

    fun continueLimited() {
        log(TAG) { "continueLimited()" }
        continuedLimited.value = true
    }

    suspend fun notNow(version: Int) {
        log(TAG) { "notNow($version)" }
        dismissedHere.value = version
        // The prompt clears on the in-memory value, which can end the caller's scope before the write finishes
        withContext(NonCancellable) { compatibility.dismissRecommendation(version) }
    }

    companion object {
        private val TAG = logTag("Update", "Prompt")
    }
}
