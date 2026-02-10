package eu.darken.apl.common.flight.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.databinding.CommonRouteDisplayViewBinding

class RouteDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = CommonRouteDisplayViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
    }

    fun setRoute(route: FlightRoute?) {
        if (route == null) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        ui.originText.text = "↑ ${route.origin?.routeDisplayText ?: "?"}"
        ui.destinationText.text = "↓ ${route.destination?.routeDisplayText ?: "?"}"
    }
}
