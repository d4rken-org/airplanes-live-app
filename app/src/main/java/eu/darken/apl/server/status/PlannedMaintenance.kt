package eu.darken.apl.server.status

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

/** A maintenance the server announced ahead of time. A changed announcement comes with a new [id]. */
data class PlannedMaintenance(
    val id: String,
    val startsAt: Instant,
    val endsAt: Instant,
) {

    companion object {
        /**
         * The notice in a `/meta` answer, null for anything but an object like
         * `{"id": "test-notice-1", "startsAt": "2026-12-01T02:00:00Z", "endsAt": "2026-12-01T04:00:00Z"}`
         * with a non-blank `id` and `endsAt` after `startsAt`.
         */
        fun parse(element: JsonElement?): PlannedMaintenance? {
            val obj = element as? JsonObject ?: return null
            val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return null
            val startsAt = obj.instant("startsAt") ?: return null
            val endsAt = obj.instant("endsAt") ?: return null
            if (!endsAt.isAfter(startsAt)) return null
            return PlannedMaintenance(id = id, startsAt = startsAt, endsAt = endsAt)
        }

        private fun JsonObject.string(key: String): String? =
            (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.instant(key: String): Instant? = string(key)?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}
