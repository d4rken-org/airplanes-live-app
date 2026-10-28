package eu.darken.apl.map.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapLabelFieldsTest : BaseTest() {

    @Test
    fun `defaults are callsign only`() {
        val defaults = MapLabelFields()
        MapLabelField.entries.filter { defaults.isEnabled(it) } shouldBe listOf(MapLabelField.CALLSIGN)
    }

    @Test
    fun `the only enabled field cannot be disabled`() {
        val defaults = MapLabelFields()
        defaults.canDisable(MapLabelField.CALLSIGN) shouldBe false
        defaults.canDisable(MapLabelField.TYPE) shouldBe true

        val two = defaults.with(MapLabelField.SPEED, true)
        two.canDisable(MapLabelField.CALLSIGN) shouldBe true
        two.canDisable(MapLabelField.SPEED) shouldBe true
    }

    @Test
    fun `with sets one field`() {
        MapLabelFields().with(MapLabelField.ALTITUDE, true) shouldBe MapLabelFields(callsign = true, altitude = true)
        MapLabelFields(type = true).with(MapLabelField.CALLSIGN, false) shouldBe MapLabelFields(callsign = false, type = true)
    }

    @Test
    fun `no enabled field falls back to the defaults`() {
        val none = MapLabelFields(callsign = false, type = false, altitude = false, speed = false)
        MapLabelFields.orDefaults(none) shouldBe MapLabelFields()

        val typeOnly = MapLabelFields(callsign = false, type = true)
        MapLabelFields.orDefaults(typeOnly) shouldBe typeOnly
    }
}
