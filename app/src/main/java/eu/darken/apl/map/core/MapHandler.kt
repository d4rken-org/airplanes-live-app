package eu.darken.apl.map.core

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.http.HttpModule.UserAgent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MapHandler @AssistedInject constructor(
    @Assisted private val webView: WebView,
    @Assisted var useNativePanel: Boolean,
    @Assisted var mapLayerKey: String,
    private val mapWebInterfaceFactory: MapWebInterface.Factory,
    @UserAgent private val userAgent: String,
) : WebViewClient() {

    @Volatile private var currentOptions: MapOptions = MapOptions()
    private val interfaceListener = object : MapWebInterface.Listener {
        override fun onHomePressed() {
            sendEvent(Event.HomePressed)
        }

        override fun onUrlChanged(newUrl: String) {
            val old = currentOptions
            currentOptions = old.copy(
                filter = old.filter.copy(
                    selected = newUrl
                        .takeIf { it.contains("icao=") }
                        ?.substringAfter("icao=")
                        ?.substringBefore("&")
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet(),
                    filtered = newUrl
                        .takeIf { it.contains("icaoFilter=") }
                        ?.substringAfter("icaoFilter=")
                        ?.substringBefore("&")
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet(),
                ),
            )
            sendEvent(Event.OptionsChanged(currentOptions))
        }

        override fun onMapPositionChanged(lat: Double, lon: Double, zoom: Double) {
            log(TAG) { "onMapPositionChanged(lat=$lat, lon=$lon, zoom=$zoom)" }
            val old = currentOptions
            currentOptions = old.copy(
                camera = MapOptions.Camera(lat, lon, zoom)
            )
            sendEvent(Event.OptionsChanged(currentOptions))
        }

        override fun onAircraftDetailsChanged(jsonData: String) {
            val details = MapAircraftDetails.fromJson(jsonData)
            if (details != null) {
                lastAircraftDetails = details
                sendEvent(Event.AircraftDetailsChanged(details))
            } else {
                log(TAG, WARN) { "Failed to parse aircraft info JSON" }
            }
        }

        override fun onAircraftDeselected() {
            lastAircraftDetails = null
            sendEvent(Event.AircraftDeselected)
        }

        override fun onButtonStatesChanged(jsonData: String) {
            sendEvent(Event.ButtonStatesChanged(jsonData))
        }

        override fun onAircraftListChanged(jsonData: String) {
            val data = MapSidebarData.fromJson(jsonData)
            if (data != null) {
                sendEvent(Event.AircraftListChanged(data))
            } else {
                log(TAG, WARN) { "Failed to parse aircraft list JSON" }
            }
        }
    }

    init {
        log(TAG) { "init($webView, useNativePanel=$useNativePanel)" }
        webView.apply {
            webViewClient = this@MapHandler
            addJavascriptInterface(mapWebInterfaceFactory.create(interfaceListener), "Android")
            settings.apply {
                @SuppressLint("SetJavaScriptEnabled")
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                setGeolocationEnabled(true)
                domStorageEnabled = true
                userAgentString = userAgent
            }

            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback,
                ) {
                    log(TAG) { "onGeolocationPermissionsShowPrompt($origin,$callback)" }
                    if (origin == "https://globe.airplanes.live") {
                        callback.invoke(origin, true, false)
                    } else {
                        log(TAG, WARN) { "Denying geolocation to unexpected origin: $origin" }
                        callback.invoke(origin, false, false)
                    }
                }

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    log(TAG, VERBOSE) { "Console: ${message.message()}" }
                    return super.onConsoleMessage(message)
                }
            }
        }
    }

    val events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    internal fun sendEvent(event: Event) {
        val success = events.tryEmit(event)
        log(TAG) { "Sending $event = $success" }
    }

    sealed interface Event {
        data object HomePressed : Event
        data class OpenUrl(val url: String) : Event
        data class OptionsChanged(val options: MapOptions) : Event
        data class AircraftDetailsChanged(val details: MapAircraftDetails) : Event
        data object AircraftDeselected : Event
        data class ButtonStatesChanged(val jsonData: String) : Event
        data class AircraftListChanged(val data: MapSidebarData) : Event
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        log(TAG) { "onPageStarted(): $url $view" }
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        log(TAG) { "onPageFinished(): $url $view" }

        val parsedUrl = android.net.Uri.parse(url)
        if (parsedUrl.scheme != "https" || parsedUrl.host != "globe.airplanes.live") {
            log(TAG, WARN) { "Skipping inject, not globe.airplanes.live" }
            return
        }

        // The globe page uses CSS height:100% which doesn't resolve in Compose-hosted WebViews.
        // Force explicit pixel heights and trigger a resize so OpenLayers re-initializes.
        view.evaluateJavascript("""
            (function() {
                var h = window.innerHeight + 'px';
                document.documentElement.style.setProperty('height', h, 'important');
                document.body.style.setProperty('height', h, 'important');
                var lc = document.getElementById('layout_container');
                if (lc) lc.style.setProperty('height', h, 'important');
                var mc = document.getElementById('map_container');
                if (mc) mc.style.setProperty('height', h, 'important');
                requestAnimationFrame(function() {
                    window.dispatchEvent(new Event('resize'));
                });
            })();
        """.trimIndent(), null)

        // Set localStorage on correct origin and switch layer via OL API
        view.ensureMapLayer(mapLayerKey)

        if (!useNativePanel) {
            log(TAG, INFO) { "Native panel disabled, ensuring web info block is visible." }
            view.evaluateJavascript("""
                (function() {
                    var lc = document.getElementById('layout_container');
                    if (lc) lc.style.setProperty('overflow', 'visible', 'important');
                })();
            """.trimIndent(), null)
            return
        }

        view.setupUrlChangeHook()
        view.setupButtonHook("H", "onHomePressed")
        view.setupMapPositionHook()
        view.hideInfoBlock()
        view.hideButtonSidebar()
        view.setupButtonStateHook()
        view.setupAircraftDetailsExtraction()
        view.setupAircraftListExtraction()

        // Restore cached aircraft info after page reload
        lastAircraftDetails?.let { sendEvent(Event.AircraftDetailsChanged(it)) }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return true

        val isInternal = request.url?.scheme == "https" && request.url?.host == "globe.airplanes.live"

        if (isInternal) {
            log(TAG, VERBOSE) { "Allowing internal URL: $url" }
        } else {
            log(TAG, INFO) { "Not an allowed internal URL, opening external: $url" }
            sendEvent(Event.OpenUrl(url))
        }

        return !isInternal
    }

    fun loadMap(options: MapOptions) {
        log(TAG, INFO) { "loadMap($options)" }

        val url = options.createUrl()

        if (webView.url == url || (webView.url != null && currentOptions == options)) {
            currentOptions = options
            log(TAG) { "Url already loaded, skipped." }
            return
        }
        currentOptions = options

        // Set map layer in localStorage before page loads so tar1090 picks it up during init
        val safeKey = MapLayer.fromKey(mapLayerKey).key
        webView.evaluateJavascript("localStorage['MapType_tar1090'] = '$safeKey';", null)
        webView.loadUrl(url)
    }

    fun forceReload() {
        log(TAG, INFO) { "forceReload()" }
        currentOptions = MapOptions()
        val safeKey = MapLayer.fromKey(mapLayerKey).key
        webView.evaluateJavascript("localStorage['MapType_tar1090'] = '$safeKey';", null)
        webView.loadUrl(currentOptions.createUrl())
    }

    fun clickHome() {
        log(TAG) { "clickHome()" }
        val jsCode = "document.getElementById('H').click();"
        webView.evaluateJavascript(jsCode, null)
    }

    fun executeToggle(buttonId: String) {
        log(TAG) { "executeToggle($buttonId)" }
        webView.executeMapToggle(buttonId)
    }

    fun deselectSelectedAircraft() {
        log(TAG) { "deselectSelectedAircraft()" }
        webView.deselectSelectedAircraft()
    }

    fun selectAircraft(hex: String) {
        log(TAG) { "selectAircraft($hex)" }
        webView.selectAircraft(hex)
    }

    fun applyMapLayer(layerKey: String) {
        log(TAG) { "applyMapLayer($layerKey)" }
        mapLayerKey = layerKey
        webView.ensureMapLayer(layerKey)
    }

    private var lastAircraftDetails: MapAircraftDetails? = null

    @AssistedFactory
    interface Factory {
        fun create(webView: WebView, useNativePanel: Boolean, mapLayerKey: String): MapHandler
    }

    companion object {
        internal val TAG = logTag("Map", "Handler")
    }
}
