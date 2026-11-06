package eu.darken.apl.server.deletion

/** The server accepted the deletion request without confirming the data is gone, only a 204 confirms it. */
class UnconfirmedDeletionException(
    val status: Int,
) : RuntimeException("The server did not confirm the deletion (HTTP $status)")
