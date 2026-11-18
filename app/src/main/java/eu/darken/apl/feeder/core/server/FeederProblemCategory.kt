package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig

/** The groups of server problem codes the user switches notifications for, per feeder. */
enum class FeederProblemCategory {
    MESSAGE_RATE,
    MLAT,
    CONNECTION,
    ;

    companion object {
        /** Null for a code without a category, e.g. one added by a newer server. */
        fun of(code: String): FeederProblemCategory? = when (code) {
            "deaf" -> MESSAGE_RATE
            "mlat_missing", "mlat_no_peers", "mlat_bad_sync", "mlat_outliers" -> MLAT
            "flapping", "duplicate_uuid" -> CONNECTION
            else -> null
        }
    }
}

fun FeederConfig.notifiesAbout(category: FeederProblemCategory): Boolean = when (category) {
    FeederProblemCategory.MESSAGE_RATE -> notifyMessageRateProblems
    FeederProblemCategory.MLAT -> notifyMlatProblems
    FeederProblemCategory.CONNECTION -> notifyConnectionProblems
}

fun FeederConfig.withProblemCategory(category: FeederProblemCategory, enabled: Boolean): FeederConfig = when (category) {
    FeederProblemCategory.MESSAGE_RATE -> copy(notifyMessageRateProblems = enabled)
    FeederProblemCategory.MLAT -> copy(notifyMlatProblems = enabled)
    FeederProblemCategory.CONNECTION -> copy(notifyConnectionProblems = enabled)
}

val FeederConfig.problemCategories: Set<FeederProblemCategory>
    get() = FeederProblemCategory.entries.filter { notifiesAbout(it) }.toSet()

/**
 * Whether a problem with the server [code] may be notified for the feeder of [config], given the
 * global problem alerts switch. A code without a category is notified whenever that switch is on.
 */
fun isProblemAlertEligible(config: FeederConfig, problemAlertsEnabled: Boolean, code: String): Boolean {
    if (!problemAlertsEnabled) return false
    val category = FeederProblemCategory.of(code) ?: return true
    return config.notifiesAbout(category)
}
