package eu.darken.apl.feeder.ui.preview

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.ReceiverId
import java.time.Instant

fun mockFeeder(
    label: String = "Home Feeder",
    id: ReceiverId = "abc12",
    status: FeederStatus = FeederStatus.ACTIVE,
) = Feeder(
    id = id,
    name = label,
    status = status,
    lastSeen = Instant.parse("2026-06-01T07:55:01Z"),
    country = "DE",
)
