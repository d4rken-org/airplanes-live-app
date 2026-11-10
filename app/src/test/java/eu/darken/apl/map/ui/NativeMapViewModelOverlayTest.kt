package eu.darken.apl.map.ui

import eu.darken.apl.map.core.overlay.OverlayState
import eu.darken.apl.map.ui.NativeMapViewModel.Companion.overlayOptions
import eu.darken.apl.map.ui.NativeMapViewModel.OverlayOption
import eu.darken.apl.server.api.OverlayCatalogResponse
import eu.darken.apl.server.api.OverlayDto
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class NativeMapViewModelOverlayTest : BaseTest() {

    private fun overlay(
        id: String,
        status: String = OverlayDto.STATUS_AVAILABLE,
        access: String = OverlayDto.ACCESS_ALLOWED,
    ) = OverlayDto(
        id = id,
        kind = OverlayDto.KIND_VECTOR,
        category = OverlayDto.CATEGORY_AIRSPACE,
        coverage = listOf("United States"),
        title = "Title of $id",
        forecast = false,
        attribution = emptyList(),
        status = status,
        access = access,
        minZoom = 0,
        maxZoom = 12,
        tileSize = 512,
        refreshSeconds = 300,
    )

    private val catalog = OverlayCatalogResponse(
        generatedAt = 1_794_225_640_000L,
        overlays = listOf(
            overlay(OverlayDto.ID_RESTRICTIONS),
            overlay(OverlayDto.ID_RADAR, status = OverlayDto.STATUS_UNAVAILABLE),
            overlay(OverlayDto.ID_MILITARY, access = OverlayDto.ACCESS_UPGRADE_REQUIRED),
        ),
    )

    @Test
    fun `nothing is listed while overlays are not offered`() {
        overlayOptions(OverlayState(catalog = catalog, offered = false), emptySet()) shouldBe emptyList()
    }

    @Test
    fun `nothing is listed before the catalog was read`() {
        overlayOptions(OverlayState(catalog = null, offered = true), emptySet()) shouldBe emptyList()
    }

    @Test
    fun `the catalog's overlays are listed in its order with the stored choice`() {
        overlayOptions(OverlayState(catalog = catalog, offered = true), setOf(OverlayDto.ID_RADAR)) shouldBe listOf(
            OverlayOption(
                id = OverlayDto.ID_RESTRICTIONS,
                title = "Title of ${OverlayDto.ID_RESTRICTIONS}",
                coverage = listOf("United States"),
                checked = false,
                locked = false,
                available = true,
            ),
            OverlayOption(
                id = OverlayDto.ID_RADAR,
                title = "Title of ${OverlayDto.ID_RADAR}",
                coverage = listOf("United States"),
                checked = true,
                locked = false,
                available = false,
            ),
            OverlayOption(
                id = OverlayDto.ID_MILITARY,
                title = "Title of ${OverlayDto.ID_MILITARY}",
                coverage = listOf("United States"),
                checked = false,
                locked = true,
                available = true,
            ),
        )
    }

    @Test
    fun `while blocked the overlays stay listed with the stored choice but none is available`() {
        val options = overlayOptions(
            OverlayState(catalog = catalog, offered = true, block = OverlayState.Block.REVOKED),
            setOf(OverlayDto.ID_RESTRICTIONS),
        )

        options.map { it.id } shouldBe listOf(OverlayDto.ID_RESTRICTIONS, OverlayDto.ID_RADAR, OverlayDto.ID_MILITARY)
        options.map { it.checked } shouldBe listOf(true, false, false)
        options.map { it.available } shouldBe listOf(false, false, false)
    }
}
