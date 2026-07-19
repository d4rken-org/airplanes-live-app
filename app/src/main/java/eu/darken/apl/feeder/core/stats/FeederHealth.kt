package eu.darken.apl.feeder.core.stats

import androidx.annotation.StringRes
import eu.darken.apl.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HealthMetric {
    @SerialName("cpu_temperature") CPU_TEMPERATURE,
    @SerialName("memory_available") MEMORY_AVAILABLE,
    @SerialName("disk_available") DISK_AVAILABLE,
    @SerialName("wifi_rssi") WIFI_RSSI,
    @SerialName("clock_skew") CLOCK_SKEW,
    ;
}

@Serializable
enum class HealthSeverity {
    @SerialName("ok") OK,
    @SerialName("warn") WARN,
    @SerialName("crit") CRIT,
    ;
}

/** Age tier of the device-pushed diagnostics snapshot, as classified by the server. */
enum class DeviceFreshness {
    FRESH,
    STALE,

    /** Last-known raw values are still delivered, but the server refuses to classify them. */
    EXPIRED,
    UNKNOWN,
    ;
}

/**
 * Outcome of one health-check attempt for one feeder. Three distinct cases because they demand
 * different reactions: an inconclusive check must not look like a recovery, and diagnostics
 * being disabled is a *successful* answer that retires any previous warning.
 */
sealed interface HealthObservation {
    /** No classifiable evidence (fetch failed, or the snapshot expired) — carry state forward. */
    data object Inconclusive : HealthObservation

    /** Fetch succeeded but the feeder reports no device data (diagnostics off) — clear state. */
    data object DiagnosticsUnavailable : HealthObservation

    /** Fetch succeeded; [warnings] holds only the non-OK metrics (absent metric == healthy). */
    data class Evaluated(val warnings: Map<HealthMetric, HealthSeverity>) : HealthObservation
}

/**
 * Health verdict for monitoring, derived entirely from the SERVER-computed severity map — the
 * server owns the thresholds (`accounts/services/feeder_thresholds.py`) and already refuses to
 * classify expired snapshots or RSSI on non-WiFi connections.
 */
fun FeederMetricFamily.Device?.toHealthObservation(): HealthObservation = when {
    this == null -> HealthObservation.DiagnosticsUnavailable
    freshness == DeviceFreshness.EXPIRED -> HealthObservation.Inconclusive
    else -> HealthObservation.Evaluated(severities.filterValues { it != HealthSeverity.OK })
}

/** Wire metric key ("cpu_temperature_c", ...) to domain metric; null for unknown future keys. */
internal fun healthMetricFromWire(key: String): HealthMetric? = when (key) {
    "cpu_temperature_c" -> HealthMetric.CPU_TEMPERATURE
    "memory_used_percent" -> HealthMetric.MEMORY_AVAILABLE
    "disk_used_percent" -> HealthMetric.DISK_AVAILABLE
    "wifi_rssi_dbm" -> HealthMetric.WIFI_RSSI
    "clock_skew_seconds" -> HealthMetric.CLOCK_SKEW
    else -> null
}

/** Wire severity to domain; the wire uses "err" (not "error"), null means not classifiable. */
internal fun healthSeverityFromWire(value: String?): HealthSeverity? = when (value) {
    "ok" -> HealthSeverity.OK
    "warn" -> HealthSeverity.WARN
    "err" -> HealthSeverity.CRIT
    else -> null
}

internal fun deviceFreshnessFromWire(value: String?): DeviceFreshness = when (value) {
    "fresh" -> DeviceFreshness.FRESH
    "stale" -> DeviceFreshness.STALE
    "expired" -> DeviceFreshness.EXPIRED
    else -> DeviceFreshness.UNKNOWN
}

@StringRes
fun HealthMetric.labelRes(): Int = when (this) {
    HealthMetric.CPU_TEMPERATURE -> R.string.feeder_health_metric_cpu
    HealthMetric.MEMORY_AVAILABLE -> R.string.feeder_health_metric_memory
    HealthMetric.DISK_AVAILABLE -> R.string.feeder_health_metric_disk
    HealthMetric.WIFI_RSSI -> R.string.feeder_health_metric_wifi
    HealthMetric.CLOCK_SKEW -> R.string.feeder_health_metric_clock
}
