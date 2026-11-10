package eu.darken.apl.map.core.overlay

import eu.darken.apl.server.api.OverlayDto

/**
 * An overlay as the map draws it. It holds nothing the map doesn't draw, so an equal spec means the
 * overlay on the map can stay as it is; a new frame comes with a new [tiles] template.
 */
data class OverlaySpec(
    val id: String,
    /** Absolute, with the literal `{z}/{x}/{y}` placeholders. */
    val tiles: String,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: Bounds?,
    /** HTML links, empty when the overlay names no source. */
    val attribution: String,
    val drawing: Drawing,
) {

    data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double)

    sealed interface Drawing

    data class Raster(val tileSize: Int) : Drawing

    /** Colours are `#rrggbb`. A feature's `#rrggbb` value in [colorProperty] replaces both of them. */
    data class Vector(
        val sourceLayer: String,
        val lineColor: String,
        val lineWidth: Double,
        val fillColor: String,
        val fillOpacity: Double,
        val labelProperty: String?,
        val colorProperty: String?,
    ) : Drawing

    val sourceId: String
        get() = "$ID_PREFIX$id"

    val rasterLayerId: String
        get() = "$sourceId-raster"

    val fillLayerId: String
        get() = "$sourceId-fill"

    val lineLayerId: String
        get() = "$sourceId-line"

    val labelLayerId: String
        get() = "$sourceId-label"

    /** Bottom to top. */
    val layerIds: List<String>
        get() = when (drawing) {
            is Raster -> listOf(rasterLayerId)
            is Vector -> listOfNotNull(fillLayerId, lineLayerId, labelLayerId.takeIf { drawing.labelProperty != null })
        }

    companion object {
        private const val ID_PREFIX = "apl-overlay-"
    }
}

/** A step that brings the overlays on the map to what should be drawn, see [OverlayLayers.reconcile]. */
sealed interface OverlayOp {
    val spec: OverlaySpec

    data class RemoveLayers(override val spec: OverlaySpec) : OverlayOp

    data class RemoveSource(override val spec: OverlaySpec) : OverlayOp

    data class AddSource(override val spec: OverlaySpec) : OverlayOp

    /** Each layer, bottom to top, right below [belowLayerId]; null is right below the app's own layers. */
    data class AddLayers(override val spec: OverlaySpec, val belowLayerId: String?) : OverlayOp
}

object OverlayLayers {

    /** What to draw, in catalog order. [baseUrl] is the API base URL the catalog's tile paths belong to. */
    fun drawable(
        overlays: List<OverlayDto>,
        enabled: Set<String>,
        isBlocked: Boolean,
        baseUrl: String,
    ): List<OverlaySpec> {
        if (isBlocked) return emptyList()
        return overlays.mapNotNull { overlay ->
            if (overlay.id !in enabled || !overlay.isAvailable || !overlay.isAllowed) return@mapNotNull null
            val tiles = overlay.tiles ?: return@mapNotNull null
            val drawing = drawingOf(overlay) ?: return@mapNotNull null
            OverlaySpec(
                id = overlay.id,
                // Not HttpUrl.resolve(), it would percent-encode the placeholders
                tiles = baseUrl.trimEnd('/') + tiles,
                minZoom = overlay.minZoom,
                maxZoom = overlay.maxZoom,
                bounds = overlay.bounds
                    ?.takeIf { it.size == 4 }
                    ?.let { (west, south, east, north) -> OverlaySpec.Bounds(west, south, east, north) },
                attribution = overlay.attribution.joinToString(" ") {
                    "<a href=\"${it.url.escapeHtml()}\">${it.text.escapeHtml()}</a>"
                },
                drawing = drawing,
            )
        }
    }

    /** Null for a kind this build doesn't know, and for a vector overlay without its layer or style. */
    private fun drawingOf(overlay: OverlayDto): OverlaySpec.Drawing? = when {
        overlay.isRaster -> OverlaySpec.Raster(overlay.tileSize)
        overlay.isVector -> {
            val sourceLayer = overlay.sourceLayer
            val style = overlay.style
            if (sourceLayer == null || style == null) {
                null
            } else {
                OverlaySpec.Vector(
                    sourceLayer = sourceLayer,
                    lineColor = style.lineColor,
                    lineWidth = style.lineWidth,
                    fillColor = style.fillColor,
                    fillOpacity = style.fillOpacity,
                    labelProperty = style.labelProperty,
                    colorProperty = style.colorProperty,
                )
            }
        }

        else -> null
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * Turns the [applied] overlays into the [wanted] ones, both in catalog order. An equal spec stays.
     * Removals come first, a source can't go while a layer still uses it. Then the overlays to add go in
     * from the top down, each below the next overlay in catalog order, which is already in place by then.
     *
     * Applied `[radar, restrictions, military]`, wanted the same with a new restrictions frame: the old
     * restrictions go, the new ones go in below military's first layer, so above radar again.
     */
    fun reconcile(applied: List<OverlaySpec>, wanted: List<OverlaySpec>): List<OverlayOp> {
        val unchanged = wanted.filter { it in applied }
        // Overlays the catalog now orders differently can't stay where they are
        val kept = if (applied.filter { it in unchanged } == unchanged) unchanged else emptyList()

        val ops = mutableListOf<OverlayOp>()
        applied.filter { it !in kept }.forEach {
            ops += OverlayOp.RemoveLayers(it)
            ops += OverlayOp.RemoveSource(it)
        }

        var above: OverlaySpec? = null
        wanted.asReversed().forEach { spec ->
            if (spec !in kept) {
                ops += OverlayOp.AddSource(spec)
                ops += OverlayOp.AddLayers(spec, above?.layerIds?.first())
            }
            above = spec
        }
        return ops
    }
}
