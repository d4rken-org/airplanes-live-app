package eu.darken.apl.server.deletion

enum class DeletionState {
    NONE,

    /** The deletion request may have reached the server, whether it deleted anything is unknown. */
    REQUEST_PENDING,

    /** The server confirmed the deletion, the local data still has to be wiped. */
    WIPE_PENDING,
}
