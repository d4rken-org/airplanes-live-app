package eu.darken.apl.map.ui

import eu.darken.apl.common.units.AircraftUnits
import eu.darken.apl.common.units.DistanceUnit
import eu.darken.apl.common.units.Quantity
import eu.darken.apl.common.units.UnitFormatter
import eu.darken.apl.common.units.UnitSymbol
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapLabelFields
import eu.darken.apl.map.core.MapLabelText
import eu.darken.apl.map.core.MapPlane
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.Locale

class MapLabelRefreshTest : BaseTest() {

    private fun plane(hex: String, callsign: String, altitudeFt: Int?, groundSpeedKnots: Int, onGround: Boolean) = MapPlane(
        hex = hex,
        latitude = 50.0,
        longitude = 8.0,
        trackDegrees = 90f,
        altitudeFt = altitudeFt,
        groundSpeedKnots = groundSpeedKnots,
        onGround = onGround,
        callsign = callsign,
        squawk = null,
        aircraftType = "A320",
        category = null,
        military = false,
        opacity = 1f,
    )

    private val cruising = plane("3C65A3", "DLH453", 36_000, 450, onGround = false)
    private val taxiing = plane("400F01", "BAW12", null, 5, onGround = true)

    private fun frame(trafficVersion: Long = 1) = MapAircraftProvider.Frame(
        traffic = listOf(cruising), trafficVersion = trafficVersion, fades = emptyMap(), selectedHex = taxiing.hex,
        selected = taxiing, selectedDetails = null, selectedMissing = null, selectedTrail = emptyList(),
        selectedTrailHead = null, tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0,
        capped = false, totalMatching = null,
    )

    private fun Quantity.text(): String = number + " " + when (unit) {
        UnitSymbol.FEET -> "ft"
        UnitSymbol.METERS -> "m"
        UnitSymbol.KNOTS -> "kt"
        UnitSymbol.KILOMETERS_PER_HOUR -> "km/h"
        else -> unit.name
    }

    private fun labelText(fields: MapLabelFields, units: AircraftUnits = AircraftUnits.AVIATION): MapLabelText {
        val formatter = UnitFormatter(units, DistanceUnit.KILOMETERS, Locale.US)
        return MapLabelText(
            fields = fields,
            altitudeText = { formatter.altitude(it).text() },
            groundText = "GND",
            speedText = { formatter.speed(it.toDouble()).text() },
        )
    }

    private val motionFields = MapLabelFields(callsign = true, altitude = true, speed = true)

    private class Drawn(val frame: MapAircraftProvider.Frame, val labels: MapLabelText, val step: MapLabelRefresh.Step) {
        val trafficLabels: List<String> get() = frame.traffic.map { labels.label(it, selected = false) }
        val selectedLabel: String? get() = frame.selected?.let { labels.label(it, selected = true) }
    }

    /** Collects like the map does, what was drawn is what [MapLabelRefresh] decided for each pair. */
    private fun TestScope.draw(frames: Flow<MapAircraftProvider.Frame>, labels: Flow<MapLabelText>): List<Drawn> {
        val drawn = mutableListOf<Drawn>()
        val refresh = MapLabelRefresh()
        backgroundScope.launch {
            combine(frames, labels) { frame, text -> frame to text }.conflate().collect { (frame, text) ->
                drawn.add(Drawn(frame, text, refresh.next(frame, text)))
            }
        }
        runCurrent()
        return drawn
    }

    /** One frame and then silence, like a paused history. */
    private fun oneFrame(): MutableSharedFlow<MapAircraftProvider.Frame> =
        MutableSharedFlow<MapAircraftProvider.Frame>(replay = 1).apply { tryEmit(frame()) }

    @Test
    fun `the first frame writes all labels`() = runTest {
        val drawn = draw(oneFrame(), MutableStateFlow(labelText(MapLabelFields())))

        drawn.single().step shouldBe MapLabelRefresh.Step(newFrame = true, relabel = true)
        drawn.single().trafficLabels shouldBe listOf("DLH453")
        drawn.single().selectedLabel shouldBe "BAW12"
    }

    @Test
    fun `other label fields relabel the frame already drawn`() = runTest {
        val labels = MutableStateFlow(labelText(MapLabelFields()))
        val drawn = draw(oneFrame(), labels)

        labels.value = labelText(motionFields)
        runCurrent()

        drawn shouldHaveSize 2
        drawn[1].frame shouldBeSameInstanceAs drawn[0].frame
        drawn[1].step shouldBe MapLabelRefresh.Step(newFrame = false, relabel = true)
        drawn[1].trafficLabels shouldBe listOf("DLH453\n36,000 ft  450 kt")
        // Slow on the ground, only the selection shows it
        drawn[1].selectedLabel shouldBe "BAW12\nGND  5 kt"
    }

    @Test
    fun `other units relabel the frame already drawn`() = runTest {
        val labels = MutableStateFlow(labelText(motionFields))
        val drawn = draw(oneFrame(), labels)

        labels.value = labelText(motionFields, AircraftUnits.METRIC)
        runCurrent()

        drawn shouldHaveSize 2
        drawn[1].frame shouldBeSameInstanceAs drawn[0].frame
        drawn[1].step shouldBe MapLabelRefresh.Step(newFrame = false, relabel = true)
        drawn[0].trafficLabels shouldBe listOf("DLH453\n36,000 ft  450 kt")
        drawn[1].trafficLabels shouldBe listOf("DLH453\n10,970 m  833 km/h")
        drawn[1].selectedLabel shouldBe "BAW12\nGND  9 km/h"
    }

    @Test
    fun `a new frame with the same label text is no relabel`() = runTest {
        val frames = oneFrame()
        val drawn = draw(frames, MutableStateFlow(labelText(MapLabelFields())))

        frames.emit(frame(trafficVersion = 2))
        runCurrent()

        drawn shouldHaveSize 2
        drawn[1].step shouldBe MapLabelRefresh.Step(newFrame = true, relabel = false)
    }

    @Test
    fun `the same label text again changes nothing`() = runTest {
        val text = labelText(MapLabelFields())
        val labels = MutableSharedFlow<MapLabelText>(replay = 1).apply { tryEmit(text) }
        val drawn = draw(oneFrame(), labels)

        labels.emit(text)
        runCurrent()

        drawn shouldHaveSize 2
        drawn[1].step shouldBe MapLabelRefresh.Step(newFrame = false, relabel = false)
    }
}
