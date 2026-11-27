package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapLabelFieldsTest : BaseTest() {

    @Test
    fun `defaults are all off`() {
        val defaults = MapLabelFields()
        MapLabelField.entries.filter { defaults.isEnabled(it) } shouldBe emptyList()
        defaults.hasAny shouldBe false
    }

    @Test
    fun `any enabled field counts`() {
        MapLabelField.entries.forEach { field ->
            MapLabelFields().with(field, true).hasAny shouldBe true
        }
        MapLabelFields(callsign = true, speed = true).hasAny shouldBe true
    }

    @Test
    fun `with sets one field`() {
        MapLabelFields().with(MapLabelField.ALTITUDE, true) shouldBe MapLabelFields(altitude = true)
        MapLabelFields(callsign = true).with(MapLabelField.ALTITUDE, true) shouldBe MapLabelFields(callsign = true, altitude = true)
        MapLabelFields(type = true).with(MapLabelField.CALLSIGN, false) shouldBe MapLabelFields(callsign = false, type = true)
    }

    @Test
    fun `only altitude or speed make a label change while in view`() {
        MapLabelFields().hasMotion shouldBe false
        MapLabelFields(callsign = true).hasMotion shouldBe false
        MapLabelFields(type = true).hasMotion shouldBe false
        MapLabelFields(altitude = true).hasMotion shouldBe true
        MapLabelFields(speed = true).hasMotion shouldBe true
        MapLabelFields(altitude = true, speed = true).hasMotion shouldBe true
    }
}
