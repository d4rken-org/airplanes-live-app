package eu.darken.apl.feeder.core

/** Thrown after the feeders that did answer are stored. [cause] is one of the failures. */
class FeederRefreshFailure(
    val failed: Map<ReceiverId, Throwable>,
    val total: Int,
) : Exception("${failed.size} of $total feeders failed to refresh", failed.values.first()) {
    val isPartial: Boolean
        get() = failed.size < total
}
