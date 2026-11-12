package eu.darken.apl.map.ui

import eu.darken.apl.map.core.MapLabelFields
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class MapLabelFadeTest : BaseTest() {

    @Test
    fun `labels with altitude or speed place without the fade unless a finger moves the map`() {
        labelFade(true, MapLabelFields(), false) shouldBe true
        labelFade(true, MapLabelFields(altitude = true), false) shouldBe false
        labelFade(true, MapLabelFields(speed = true), false) shouldBe false
        labelFade(false, MapLabelFields(altitude = true, speed = true), false) shouldBe true
        labelFade(true, MapLabelFields(altitude = true), true) shouldBe true
    }

    @Test
    fun `a gesture is active from its start until the camera is idle`() {
        MapGesture().active shouldBe false

        MapGesture().apply {
            onMoveStarted(true)
            active shouldBe true
            onIdle()
            active shouldBe false
        }
    }

    @Test
    fun `camera moves by the app are not a gesture`() {
        MapGesture().apply {
            onMoveStarted(false)
            onCameraChanged()
            active shouldBe false
        }

        MapGesture().apply {
            onMoveStarted(true)
            onIdle()
            onCameraChanged()
            active shouldBe false
        }
    }

    @Test
    fun `a resting finger that moves on is a gesture again`() {
        MapGesture().apply {
            onMoveStarted(true)
            onSettled()
            active shouldBe false
            onCameraChanged()
            active shouldBe true
            onIdle()
            active shouldBe false
        }
    }
}
