package eu.darken.apl.main.core.aircraft

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
    get() = squawk?.startsWith("7") == true
