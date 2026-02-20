package eu.darken.apl.feeder.ui.preview

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.core.config.FeederConfig

fun mockFeeder(
    label: String = "Home Feeder",
    id: ReceiverId = "abc12",
) = Feeder(config = FeederConfig(receiverId = id, label = label))
