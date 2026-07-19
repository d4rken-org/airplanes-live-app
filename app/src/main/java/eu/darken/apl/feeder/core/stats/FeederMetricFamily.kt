package eu.darken.apl.feeder.core.stats

/**
 * One live metric family of a feeder as reported by the detail endpoint. Sealed so UI and
 * monitoring code dispatch exhaustively — adding a future family (e.g. aircraft stats) breaks
 * compilation at every `when` until it is handled, instead of being silently dropped.
 */
sealed interface FeederMetricFamily {

    /** Key of this family in the history `series` map. */
    val wireKey: String

    data class Feed(
        val connected: Boolean,
        val messagesPerSec: Double?,
        val positionsPerSec: Double?,
        val rttMs: Int?,
        val connectedForSeconds: Long?,
    ) : FeederMetricFamily {
        override val wireKey: String get() = WIRE_KEY

        companion object {
            const val WIRE_KEY = "feed"
        }
    }

    data class Mlat(
        val connected: Boolean,
        val peerCount: Int?,
        val messageRate: Double?,
        val outlierPercent: Double?,
        val badSync: Boolean?,
        val interestCount: Int?,
    ) : FeederMetricFamily {
        override val wireKey: String get() = WIRE_KEY

        companion object {
            const val WIRE_KEY = "mlat"
        }
    }

    data class Device(
        val cpuTemperatureC: Double?,
        val memoryUsedPercent: Double?,
        val diskUsedPercent: Double?,
        val wifiRssiDbm: Int?,
        val clockSkewSeconds: Double?,
    ) : FeederMetricFamily {
        override val wireKey: String get() = WIRE_KEY

        companion object {
            const val WIRE_KEY = "device"
        }
    }
}

inline fun <reified T : FeederMetricFamily> List<FeederMetricFamily>.family(): T? =
    filterIsInstance<T>().singleOrNull()
