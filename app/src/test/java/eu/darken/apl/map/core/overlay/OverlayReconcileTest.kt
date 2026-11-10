package eu.darken.apl.map.core.overlay

import eu.darken.apl.map.core.overlay.OverlayLayersTest.Companion.military
import eu.darken.apl.map.core.overlay.OverlayLayersTest.Companion.radar
import eu.darken.apl.map.core.overlay.OverlayLayersTest.Companion.restrictions
import eu.darken.apl.server.api.OverlayDto
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class OverlayReconcileTest : BaseTest() {

    private fun specs(vararg overlays: OverlayDto): List<OverlaySpec> = OverlayLayers.drawable(
        overlays = overlays.toList(),
        enabled = overlays.map { it.id }.toSet(),
        isBlocked = false,
        baseUrl = "https://api.example.test/",
    )

    /** Layer ids bottom to top, as MapLibre would stack them; [APP_LAYER] stands for the app's own lowest layer. */
    private fun MutableList<String>.applyOps(ops: List<OverlayOp>) = ops.forEach { op ->
        when (op) {
            is OverlayOp.RemoveLayers -> removeAll(op.spec.layerIds)
            is OverlayOp.RemoveSource -> Unit
            is OverlayOp.AddSource -> Unit
            is OverlayOp.AddLayers -> op.spec.layerIds.forEach { layer ->
                val below = op.belowLayerId ?: APP_LAYER
                indexOf(below).also { check(it >= 0) { "$below is not on the map" } }
                add(indexOf(below), layer)
            }
        }
    }

    private fun stackOf(specs: List<OverlaySpec>) = listOf(BASE_LAYER) + specs.flatMap { it.layerIds } + APP_LAYER

    @Test
    fun `equal overlays need nothing`() {
        val applied = specs(radar(), restrictions(), military())

        OverlayLayers.reconcile(applied, specs(radar(), restrictions(), military())).shouldBeEmpty()
    }

    @Test
    fun `a new frame replaces the overlay, its source only after its layers are gone`() {
        val old = specs(radar()).single()
        val new = specs(radar(frame = "7a1d02c3e9b8")).single()

        OverlayLayers.reconcile(listOf(old), listOf(new)) shouldBe listOf(
            OverlayOp.RemoveLayers(old),
            OverlayOp.RemoveSource(old),
            OverlayOp.AddSource(new),
            OverlayOp.AddLayers(new, belowLayerId = null),
        )
    }

    @Test
    fun `an overlay switched off is removed`() {
        val (radar, restrictions) = specs(radar(), restrictions())

        OverlayLayers.reconcile(listOf(radar, restrictions), listOf(restrictions)) shouldBe listOf(
            OverlayOp.RemoveLayers(radar),
            OverlayOp.RemoveSource(radar),
        )
    }

    @Test
    fun `an overlay switched on is added below the next one in catalog order`() {
        val (radar, restrictions) = specs(radar(), restrictions())

        OverlayLayers.reconcile(listOf(restrictions), listOf(radar, restrictions)) shouldBe listOf(
            OverlayOp.AddSource(radar),
            OverlayOp.AddLayers(radar, belowLayerId = "apl-overlay-restrictions-fill"),
        )
        OverlayLayers.reconcile(listOf(radar), listOf(radar, restrictions)) shouldBe listOf(
            OverlayOp.AddSource(restrictions),
            OverlayOp.AddLayers(restrictions, belowLayerId = null),
        )
    }

    @Test
    fun `catalog order holds after a change in the middle`() {
        val applied = specs(radar(), restrictions(), military())
        val wanted = specs(radar(), restrictions(frame = "b52d10f3c7a4"), military())
        val stack = stackOf(applied).toMutableList()

        stack.applyOps(OverlayLayers.reconcile(applied, wanted))

        stack shouldBe stackOf(wanted)
    }

    @Test
    fun `catalog order holds when everything is added at once, changed or switched`() {
        val stack = mutableListOf(BASE_LAYER, APP_LAYER)
        var applied = emptyList<OverlaySpec>()
        listOf(
            specs(radar(), restrictions(), military()),
            specs(radar(frame = "7a1d02c3e9b8"), restrictions(frame = "b52d10f3c7a4"), military()),
            specs(radar(frame = "7a1d02c3e9b8"), military()),
            specs(radar(frame = "7a1d02c3e9b8"), restrictions(), military(frame = "deadbeef0001")),
            specs(restrictions()),
            emptyList(),
        ).forEach { wanted ->
            stack.applyOps(OverlayLayers.reconcile(applied, wanted))
            stack shouldBe stackOf(wanted)
            applied = wanted
        }
    }

    @Test
    fun `overlays the catalog reorders are all added again`() {
        val (radar, restrictions) = specs(radar(), restrictions())
        val stack = stackOf(listOf(restrictions, radar)).toMutableList()

        stack.applyOps(OverlayLayers.reconcile(listOf(restrictions, radar), listOf(radar, restrictions)))

        stack shouldBe stackOf(listOf(radar, restrictions))
    }

    companion object {
        private const val BASE_LAYER = "base"
        private const val APP_LAYER = "app"
    }
}
