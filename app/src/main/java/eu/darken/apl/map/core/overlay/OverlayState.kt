package eu.darken.apl.map.core.overlay

import eu.darken.apl.server.api.OverlayCatalogResponse

/**
 * [catalog] is the last one read successfully; null before that and while overlays aren't [offered].
 * Nothing may be drawn while [block] is set, the catalog is kept for when it lifts.
 */
data class OverlayState(
    val catalog: OverlayCatalogResponse? = null,
    val offered: Boolean = false,
    val block: Block? = null,
) {

    enum class Block {
        UPDATE_REQUIRED,
        REVOKED,
        RESTRICTED,
        ;
    }

    val isBlocked: Boolean
        get() = block != null
}
