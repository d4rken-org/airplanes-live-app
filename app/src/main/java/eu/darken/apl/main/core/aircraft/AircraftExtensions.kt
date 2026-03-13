package eu.darken.apl.main.core.aircraft

val Aircraft.messageTypeLabel: String
    get() = when {
        messageType == "mlat" -> "MLAT"
        messageType.startsWith("adsb") -> "ADS-B"
        messageType == "mode_s" -> "MODE-S"
        else -> "Other"
    }

val Aircraft.altitudeFt: Int?
    get() {
        val raw = altitude ?: return null
        val trimmed = raw.trim().lowercase()
        if (trimmed == "ground") return 0
        return trimmed.replace(",", "").toIntOrNull()
    }

val Aircraft.isEmergencySquawk: Boolean
    get() = squawk?.startsWith("7") == true
