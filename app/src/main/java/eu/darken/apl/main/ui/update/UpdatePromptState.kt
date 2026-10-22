package eu.darken.apl.main.ui.update

import eu.darken.apl.main.core.update.AppUpdatePrompt
import eu.darken.apl.main.core.update.AppUpdatePrompt.Prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [AppUpdatePrompt.prompt] with its silence before the stored answer is read as a value of its own. */
sealed interface UpdatePromptState {
    data object NotKnown : UpdatePromptState

    data class Known(val prompt: Prompt?) : UpdatePromptState
}

/** Never emits [UpdatePromptState.NotKnown], collect it with that as the initial value. */
val AppUpdatePrompt.promptState: Flow<UpdatePromptState>
    get() = prompt.map { UpdatePromptState.Known(it) }
