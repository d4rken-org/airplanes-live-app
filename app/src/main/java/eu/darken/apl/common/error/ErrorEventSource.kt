package eu.darken.apl.common.error

import eu.darken.apl.common.flow.SingleEventFlow

interface ErrorEventSource {
    val errorEvents: SingleEventFlow<Throwable>
}
