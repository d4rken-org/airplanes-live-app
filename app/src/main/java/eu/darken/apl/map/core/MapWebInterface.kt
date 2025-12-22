package eu.darken.apl.map.core

import android.webkit.JavascriptInterface
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.apl.main.core.aircraft.AircraftHex

class MapWebInterface @AssistedInject constructor(
    @Assisted private val listener: Listener,
) {

    interface Listener {
        fun onHomePressed()
        fun onUrlChanged(newUrl: String)
        fun onMapPositionChanged(lat: Double, lon: Double, zoom: Double)
        fun onShowInSearch(hex: AircraftHex)
        fun onAddWatch(hex: AircraftHex)
        fun getWatchCount(hex: AircraftHex): Int
    }

    @JavascriptInterface
    fun onHomePressed() {
        listener.onHomePressed()
    }

    @JavascriptInterface
    fun onUrlChanged(newUrl: String) {
        listener.onUrlChanged(newUrl)
    }

    @JavascriptInterface
    fun onMapPositionChanged(lat: Double, lon: Double, zoom: Double) {
        listener.onMapPositionChanged(lat, lon, zoom)
    }

    @JavascriptInterface
    fun onShowInSearch(hex: String) {
        listener.onShowInSearch(hex)
    }

    @JavascriptInterface
    fun onAddWatch(hex: String) {
        listener.onAddWatch(hex)
    }

    @JavascriptInterface
    fun getWatchCount(hex: String): Int {
        return listener.getWatchCount(hex)
    }

    @AssistedFactory
    interface Factory {
        fun create(listener: Listener): MapWebInterface
    }
}