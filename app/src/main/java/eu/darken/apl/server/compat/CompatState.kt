package eu.darken.apl.server.compat

sealed interface CompatState {
    /** The stored answer has not been read yet. Nothing may treat this as [Ok]. */
    data object Unknown : CompatState

    data object Ok : CompatState

    data class UpdateRecommended(val recommendedVersion: Int) : CompatState

    data object UpdateRequired : CompatState
}
