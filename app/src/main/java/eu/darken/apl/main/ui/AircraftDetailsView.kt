package eu.darken.apl.main.ui

import android.content.Context
import android.location.Location
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.getColorForAttr
import eu.darken.apl.common.getString
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.load
import eu.darken.apl.databinding.CommonAircraftDetailsViewBinding
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.aircraft.Airframe
import eu.darken.apl.main.core.aircraft.Callsign
import eu.darken.apl.main.core.aircraft.Registration
import eu.darken.apl.main.core.aircraft.SquawkCode
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import java.time.Instant

class AircraftDetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = CommonAircraftDetailsViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        if (isInEditMode) {
            setAircraft(
                object : Aircraft {
                    override val hex: AircraftHex
                        get() = "ABCDEF"
                    override val messageType: String
                        get() = "mlat"
                    override val dbFlags: Int?
                        get() = null
                    override val registration: Registration
                        get() = "REGISTR"
                    override val callsign: Callsign
                        get() = "CALLSIGN"
                    override val operator: String
                        get() = "Some airplane operator company"
                    override val airframe: Airframe
                        get() = "AIRCR"
                    override val description: String
                        get() = "Two wings and a motor"
                    override val squawk: SquawkCode
                        get() = "9999"
                    override val emergency: String
                        get() = "none"
                    override val outsideTemp: Int
                        get() = 11
                    override val altitude: String
                        get() = "ground"
                    override val altitudeRate: Int
                        get() = 0
                    override val groundSpeed: Float
                        get() = 0f
                    override val indicatedAirSpeed: Int
                        get() = 12
                    override val trackheading: Double
                        get() = 301.5
                    override val location: Location?
                        get() = null
                    override val messages: Int
                        get() = 1000
                    override val seenAt: Instant
                        get() = Instant.now()
                    override val rssi: Double
                        get() = -90.0

                }
            )
        }
    }

    var onThumbnailClicked: ((PlanespottersMeta) -> Unit)? = null

    fun setAircraft(
        aircraft: Aircraft,
        distanceInMeter: Float? = null
    ) = ui.apply {
        log(VERBOSE) { "setAircraft(${aircraft.hex}, $distanceInMeter)" }

        airframe.text = aircraft.description ?: getString(R.string.aircraft_details_description_unknown)
        operator.text = aircraft.operator ?: getString(R.string.aircraft_details_operator_unknown)

        distanceAway.text = when {
            distanceInMeter != null -> getString(
                R.string.general_xdistance_away_label,
                "${(distanceInMeter / 1000).toInt()} km",
            )

            else -> ""
        }

        lastSeen.text = DateUtils.getRelativeTimeSpanString(
            aircraft.seenAt.toEpochMilli(),
            Instant.now().toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        messageType.text = getString(
            R.string.aircraft_details_datasource_x,
            aircraft.messageTypeLabel
        )

        firstValue.text = aircraft.callsign?.takeIf { it.isNotBlank() } ?: "?"
        secondValue.text = aircraft.registration ?: "?"
        thirdValue.text = "#${aircraft.hex.uppercase()}"
        fourthValue.apply {
            text = aircraft.squawk ?: "?"
            setTextColor(
                when {
                    aircraft.squawk?.startsWith("7") == true -> getColorForAttr(com.google.android.material.R.attr.colorError)
                    else -> getColorForAttr(com.google.android.material.R.attr.colorControlNormal)
                }
            )
        }
        fifthValue.text = "${aircraft.altitude ?: "?"} ft"
        sixthValue.text = "${aircraft.indicatedAirSpeed ?: aircraft.groundSpeed ?: "?"} kts"
        thumbnail.apply {
            load(aircraft, large = true)
            onViewImageListener = { onThumbnailClicked?.invoke(it) }
        }
    }

    fun setRoute(route: FlightRoute?) {
        ui.routeDisplay.setRoute(route)
    }
}
