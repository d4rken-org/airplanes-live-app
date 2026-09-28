package eu.darken.apl.map.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.io.File

class AircraftShapesTest : BaseTest() {

    // The bundled asset, so the tests also catch a regenerated table that lost an entry
    private val shapes: AircraftShapes = json.decodeFromString(File("src/main/assets/${AircraftShapesRepo.ASSET}").readText())

    private fun shapeOf(type: String?, category: String? = null) = shapes.refFor(type, category)?.shape

    @Test
    fun `an exact type has its own silhouette`() {
        shapeOf("H60") shouldBe "blackhawk"
        shapeOf("B738") shouldBe "b738"
        shapeOf("A21N") shouldBe "a321"
    }

    @Test
    fun `other types are drawn by their ICAO description`() {
        // L1P: landplane, one piston engine
        shapeOf("C172") shouldBe "cessna"
        // H2T: helicopter, two turbines, only the kind has an entry
        shapeOf("EC35") shouldBe "helicopter"
        // L2J with a light wake category
        shapeOf("C25M") shouldBe "jet_nonswept"
        shapeOf("A270") shouldBe "single_turbo"
    }

    @Test
    fun `the broadcast category decides when the type does not`() {
        shapeOf(null, "B1") shouldBe "glider"
        shapeOf(null, "A7") shouldBe "helicopter"
        shapeOf(null, "B2") shouldBe "balloon"
        shapeOf("ZZZZ", "B6") shouldBe "uav"
    }

    @Test
    fun `the category refines what the description says`() {
        shapes.refFor("C172", "B4") shouldBe AircraftShapes.Ref("cessna", 0.92f)
        shapeOf("A158", "A2") shouldBe "jet_swept"
        shapeOf("A158") shouldBe "airliner"
    }

    @Test
    fun `nothing known leaves the generic aircraft`() {
        shapes.refFor(null, null).shouldBeNull()
        shapes.refFor("ZZZZ", "A9").shouldBeNull()
    }

    @Test
    fun `every reference points to a drawable shape`() {
        val refs = shapes.designators.values + shapes.descriptions.values + shapes.categories.values
        refs.filter { it.shape !in shapes.shapes } shouldBe emptyList()
        shapes.shapes.filterValues { it.paths.isEmpty() || it.viewBox.size != 4 }.keys shouldBe emptySet()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
