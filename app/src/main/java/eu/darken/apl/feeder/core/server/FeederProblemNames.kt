package eu.darken.apl.feeder.core.server

import androidx.annotation.StringRes
import eu.darken.apl.R

/** The readable name of a server problem code such as "mlat_missing". */
@StringRes
fun feederProblemNameRes(code: String): Int = when (code) {
    "mlat_missing" -> R.string.feeder_problem_mlat_missing
    "mlat_no_peers" -> R.string.feeder_problem_mlat_no_peers
    "mlat_bad_sync" -> R.string.feeder_problem_mlat_bad_sync
    "mlat_outliers" -> R.string.feeder_problem_mlat_outliers
    "deaf" -> R.string.feeder_problem_deaf
    "flapping" -> R.string.feeder_problem_flapping
    "duplicate_uuid" -> R.string.feeder_problem_duplicate_uuid
    else -> R.string.feeder_problem_other
}
