package eu.darken.apl.common.planespotters.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.darken.apl.main.core.aircraft.Aircraft

class PlanespottersKeyer : Keyer<Aircraft> {
    override fun key(data: Aircraft, options: Options): String {
        return "aircraft-${data.hex}"
    }
}

class PlanespottersThumbnailKeyer : Keyer<AircraftThumbnailQuery> {
    override fun key(data: AircraftThumbnailQuery, options: Options): String {
        return "planespotters-${data.hex}-${data.large}"
    }
}
