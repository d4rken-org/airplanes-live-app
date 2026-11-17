package eu.darken.apl.main.core.aircraft

import android.location.Location
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

enum class AircraftSourceMeaning(@StringRes val nameRes: Int, @StringRes val textRes: Int) {
    ADSB(R.string.aircraft_source_adsb_label, R.string.aircraft_source_meaning_adsb),
    MLAT(R.string.aircraft_source_mlat_label, R.string.aircraft_source_meaning_mlat),
    MODE_S(R.string.aircraft_source_modes_label, R.string.aircraft_source_meaning_mode_s),
    TISB(R.string.aircraft_source_tisb_label, R.string.aircraft_source_meaning_tisb),
    ADSR(R.string.aircraft_source_adsr_label, R.string.aircraft_source_meaning_adsr),
    ADSC(R.string.aircraft_source_adsc_label, R.string.aircraft_source_meaning_adsc),
    OTHER(R.string.aircraft_source_other_label, R.string.aircraft_source_meaning_other),
}

fun aircraftSourceMeaning(source: String?): AircraftSourceMeaning = when {
    source == "mlat" -> AircraftSourceMeaning.MLAT
    source?.startsWith("adsb") == true -> AircraftSourceMeaning.ADSB
    source == "mode_s" -> AircraftSourceMeaning.MODE_S
    source?.startsWith("tisb") == true -> AircraftSourceMeaning.TISB
    source?.startsWith("adsr") == true -> AircraftSourceMeaning.ADSR
    source == "adsc" -> AircraftSourceMeaning.ADSC
    else -> AircraftSourceMeaning.OTHER
}

val Aircraft.isEmergencySquawk: Boolean
    get() = SquawkEmergency.fromSquawk(squawk) != null

/** Observation timestamps are server time, so [now] has to be too, the device clock may be off by hours. */
fun Aircraft.ageAt(now: Instant): Duration? =
    messageSeenAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }

fun Aircraft.isLiveAt(now: Instant): Boolean = ageAt(now)?.let { it < LIVE_WITHIN } == true

/** A position kept from an earlier observation can be hours older than the last message. */
val Aircraft.currentLocation: Location?
    get() {
        val positionSeenAt = positionSeenAt ?: return location
        val messageSeenAt = messageSeenAt ?: return location
        return location.takeIf { Duration.between(positionSeenAt, messageSeenAt) <= LIVE_WITHIN }
    }

val LIVE_WITHIN: Duration = Duration.ofMinutes(5)
