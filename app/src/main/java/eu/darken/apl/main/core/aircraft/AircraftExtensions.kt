package eu.darken.apl.main.core.aircraft

import androidx.annotation.StringRes
import eu.darken.apl.R
import java.time.Duration
import java.time.Instant

enum class AircraftSourceType(@StringRes val labelRes: Int) {
    MLAT(R.string.aircraft_source_mlat_label),
    ADSB(R.string.aircraft_source_adsb_label),
    MODE_S(R.string.aircraft_source_modes_label),
    OTHER(R.string.aircraft_source_other_label),
}

val Aircraft.sourceType: AircraftSourceType
    get() = when {
        source == "mlat" -> AircraftSourceType.MLAT
        source?.startsWith("adsb") == true -> AircraftSourceType.ADSB
        source == "mode_s" -> AircraftSourceType.MODE_S
        else -> AircraftSourceType.OTHER
    }

val Aircraft.isEmergencySquawk: Boolean
    get() = SquawkEmergency.fromSquawk(squawk) != null

/** Observation timestamps are server time, so [now] has to be too, the device clock may be off by hours. */
fun Aircraft.ageAt(now: Instant): Duration? =
    messageSeenAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }

fun Aircraft.isLiveAt(now: Instant): Boolean = ageAt(now)?.let { it < LIVE_WITHIN } == true

val LIVE_WITHIN: Duration = Duration.ofMinutes(5)
