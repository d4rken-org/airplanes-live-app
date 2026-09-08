package eu.darken.apl.watch.core.types

/** How the last check attempt for a watch ended, including the ones that carry no evidence. */
enum class WatchCheckOutcome(val raw: String) {
    MATCHED("matched"),
    ABSENT("absent"),
    INCONCLUSIVE("inconclusive"),
    RESTRICTED("restricted"),
    EXHAUSTED("exhausted"),
    INVALID("invalid"),
    FAILED("failed"),
    ;

    companion object {
        fun fromRaw(raw: String?): WatchCheckOutcome? = entries.firstOrNull { it.raw == raw }
    }
}
