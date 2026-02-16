package eu.darken.apl.common.planespotters

import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft

fun Aircraft.toPlanespottersQuery(large: Boolean = false) = AircraftThumbnailQuery(
    hex = this.hex,
    registration = this.registration,
    large = large,
)
