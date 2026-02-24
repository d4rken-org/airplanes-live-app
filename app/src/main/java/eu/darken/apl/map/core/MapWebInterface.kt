package eu.darken.apl.map.core

import android.webkit.JavascriptInterface
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class MapWebInterface @AssistedInject constructor(
    @Assisted private val listener: Listener,
) {

    interface Listener {
        fun onUrlChanged(newUrl: String)
        fun onMapPositionChanged(lat: Double, lon: Double, zoom: Double)
        fun onAircraftDetailsChanged(jsonData: String)
        fun onAircraftDeselected()
        fun onButtonStatesChanged(jsonData: String)
        fun onAircraftListChanged(jsonData: String)
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
    fun onAircraftDetailsChanged(jsonData: String) {
        listener.onAircraftDetailsChanged(jsonData)
    }

    @JavascriptInterface
    fun onAircraftDeselected() {
        listener.onAircraftDeselected()
    }

    @JavascriptInterface
    fun onButtonStatesChanged(jsonData: String) {
        listener.onButtonStatesChanged(jsonData)
    }

    @JavascriptInterface
    fun onAircraftListChanged(jsonData: String) {
        listener.onAircraftListChanged(jsonData)
    }

    @AssistedFactory
    interface Factory {
        fun create(listener: Listener): MapWebInterface
    }
}
