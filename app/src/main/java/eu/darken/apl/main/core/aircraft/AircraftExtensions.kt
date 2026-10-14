package eu.darken.apl.main.core.aircraft

import java.time.Duration
import java.time.Instant

val Aircraft.messageTypeLabel: String
    get() = when {
        source == "mlat" -> "MLAT"
        source?.startsWith("adsb") == true -> "ADS-B"
        source == "mode_s" -> "MODE-S"
        else -> "Other"
    }

val Aircraft.altitudeLabel: String
    get() = when {
        onGround == true -> "ground"
        altitudeFt != null -> "$altitudeFt"
        else -> "?"
    }

val Aircraft.isEmergencySquawk: Boolean
    get() = SquawkEmergency.fromSquawk(squawk) != null

/** Observation timestamps are server time, so [now] has to be too, the device clock may be off by hours. */
fun Aircraft.ageAt(now: Instant): Duration? =
    messageSeenAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }

fun Aircraft.isLiveAt(now: Instant): Boolean = ageAt(now)?.let { it < LIVE_WITHIN } == true

val LIVE_WITHIN: Duration = Duration.ofMinutes(5)
