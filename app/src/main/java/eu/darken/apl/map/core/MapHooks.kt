package eu.darken.apl.map.core

import android.webkit.WebView
import eu.darken.apl.common.debug.logging.log

internal fun WebView.setupButtonHook(
    elementId: String,
    hookName: String
) {
    log(MapHandler.TAG) { "Setting up hook for '$hookName(...)' on '$elementId'" }
    val jsCode = """
            (function() {
                var button = document.getElementById('$elementId');
                if (button && !button.dataset.listenerAdded) {
                    button.addEventListener('click', function() {
                        if(event.isTrusted) {
                            Android.$hookName();
                        }
                    });
                    button.dataset.listenerAdded = 'true';
                }
            })();
        """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.setupUrlChangeHook() {
    log(MapHandler.TAG) { "Setting up hook for URL change events" }
    val jsCode = """
            (function() {
                if (!window.urlChangeListenerAdded) {
                    var pushState = history.pushState;
                    history.pushState = function() {
                        pushState.apply(history, arguments);
                        Android.onUrlChanged(window.location.href);
                    };
                    var replaceState = history.replaceState;
                    history.replaceState = function() {
                        replaceState.apply(history, arguments);
                        Android.onUrlChanged(window.location.href);
                    };
                    window.urlChangeListenerAdded = true;
                }
            })();
        """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.setupMapPositionHook() {
    log(MapHandler.TAG) { "Setting up hook for map position changes" }
    val jsCode = """
        (function() {
            if (window.mapPositionHookAdded) return;

            // Wait for the OpenLayers map object to be available
            var checkMap = setInterval(function() {
                if (typeof OLMap !== 'undefined') {
                    var map = OLMap;
                    map.on('moveend', function() {
                        try {
                            var view = map.getView();
                            var center = view.getCenter();
                            var zoom = view.getZoom();
                            // Convert from EPSG:3857 to lat/lon
                            var lonLat = ol.proj.toLonLat(center);
                            Android.onMapPositionChanged(lonLat[1], lonLat[0], zoom);
                        } catch(e) {
                            console.log('Error getting map position: ' + e);
                        }
                    });
                    window.mapPositionHookAdded = true;
                    clearInterval(checkMap);
                    console.log('Map position hook installed');
                }
            }, 500);
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.setupAircraftDetailsExtraction() {
    log(MapHandler.TAG) { "Setting up aircraft details extraction hook" }
    val jsCode = """
        (function() {
            if (window.aircraftDetailsExtractionAdded) return;

            var lastJsonSent = '';
            var observerDebounceTimer = null;

            function extractAircraftDetails() {
                var infoBlock = document.getElementById('selected_infoblock');
                if (!infoBlock) return null;
                if (typeof SelectedPlane !== 'undefined') {
                    if (SelectedPlane === null) return null;
                } else if (window.getComputedStyle(infoBlock).display === 'none') {
                    return null;
                }
                function getText(id) {
                    var el = document.getElementById(id);
                    if (!el) return '';
                    var text = (el.textContent || '').trim();
                    return text.replace(/\s*copy\s+link\s*/gi, '').trim();
                }
                function getPhotoUrl() {
                    var photoEl = document.getElementById('selected_photo');
                    if (!photoEl) return '';
                    var img = photoEl.querySelector('img');
                    return img ? (img.src || '') : '';
                }
                function getPhotoCredit() {
                    var el = document.getElementById('copyrightInfo');
                    if (el) {
                        var text = (el.textContent || '').trim();
                        if (text && !/planespotters/i.test(text) && !/^view\s/i.test(text)) return text;
                    }
                    return '';
                }
                return JSON.stringify({
                    hex: getText('selected_icao'),
                    callsign: getText('selected_callsign'),
                    registration: getText('selected_registration'),
                    country: getText('selected_country'),
                    icaoType: getText('selected_icaotype'),
                    typeLong: getText('selected_typelong'),
                    typeDesc: getText('selected_typedesc'),
                    operator: getText('selected_ownop'),
                    speed: getText('selected_speed1'),
                    altitude: getText('selected_altitude1'),
                    altitudeGeom: getText('selected_altitude_geom1'),
                    vertRate: getText('selected_vert_rate'),
                    track: getText('selected_track1'),
                    position: getText('selected_position'),
                    source: getText('selected_source'),
                    rssi: getText('selected_rssi1'),
                    messageRate: getText('selected_message_rate'),
                    messageCount: getText('selected_message_count'),
                    seen: getText('selected_seen'),
                    seenPos: getText('selected_seen_pos'),
                    squawk: getText('selected_squawk1'),
                    route: getText('selected_route'),
                    navAltitude: getText('selected_nav_altitude'),
                    navHeading: getText('selected_nav_heading'),
                    navModes: getText('selected_nav_modes'),
                    navQnh: getText('selected_nav_qnh'),
                    speed2: getText('selected_speed2'),
                    tas: getText('selected_tas'),
                    ias: getText('selected_ias'),
                    mach: getText('selected_mach'),
                    altitude2: getText('selected_altitude2'),
                    baroRate: getText('selected_baro_rate'),
                    altitudeGeom2: getText('selected_altitude_geom2'),
                    geomRate: getText('selected_geom_rate'),
                    track2: getText('selected_track2'),
                    trueHeading: getText('selected_true_heading'),
                    magHeading: getText('selected_mag_heading'),
                    roll: getText('selected_roll'),
                    windSpeed: getText('selected_ws'),
                    windDir: getText('selected_wd'),
                    temp: getText('selected_temp'),
                    dbFlags: getText('selected_dbFlags'),
                    adsVersion: getText('selected_version'),
                    category: getText('selected_category'),
                    photoUrl: getPhotoUrl(),
                    photoCredit: getPhotoCredit()
                });
            }

            function sendUpdate() {
                var json = extractAircraftDetails();
                if (window.androidDeselecting) {
                    if (json === null) {
                        window.androidDeselecting = false;
                        if (window.androidDeselectingTimer) {
                            clearTimeout(window.androidDeselectingTimer);
                            window.androidDeselectingTimer = null;
                        }
                    } else {
                        return;
                    }
                }
                if (json === null) {
                    if (lastJsonSent !== '') {
                        lastJsonSent = '';
                        Android.onAircraftDeselected();
                    }
                    return;
                }
                if (json === lastJsonSent) return;
                lastJsonSent = json;
                Android.onAircraftDetailsChanged(json);
            }

            function scheduleObserverUpdate() {
                if (observerDebounceTimer) {
                    clearTimeout(observerDebounceTimer);
                }
                observerDebounceTimer = setTimeout(function() {
                    observerDebounceTimer = null;
                    sendUpdate();
                }, 160);
            }

            // MutationObserver for selection/deselection changes
            new MutationObserver(function() {
                scheduleObserverUpdate();
            }).observe(document.getElementById('selected_infoblock') || document.body, {
                childList: true, subtree: true, characterData: true, attributes: true
            });

            // Polling for real-time updates (position, altitude, speed)
            setInterval(function() {
                sendUpdate();
            }, 1000);

            window.androidDeselectSelectedAircraft = function() {
                try {
                    window.androidDeselecting = true;
                    window.androidDeselectingTimer = setTimeout(function() {
                        window.androidDeselecting = false;
                        window.androidDeselectingTimer = null;
                    }, 2000);

                    if (typeof deselectAllPlanes === 'function') {
                        deselectAllPlanes();
                    } else {
                        var closeButton = document.getElementById('selected_close');
                        if (closeButton && typeof closeButton.click === 'function') {
                            closeButton.click();
                        } else {
                            if (typeof SelectedPlane !== 'undefined') {
                                SelectedPlane = null;
                            }
                            if (typeof refreshSelected === 'function') {
                                refreshSelected();
                            }
                        }
                    }

                    Android.onAircraftDeselected();
                } catch(e) {
                    window.androidDeselecting = false;
                    if (window.androidDeselectingTimer) {
                        clearTimeout(window.androidDeselectingTimer);
                        window.androidDeselectingTimer = null;
                    }
                    console.log('androidDeselectSelectedAircraft error: ' + e);
                }
            };

            sendUpdate();
            window.aircraftDetailsExtractionAdded = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.deselectSelectedAircraft() {
    log(MapHandler.TAG) { "Deselecting selected aircraft via JS" }
    val jsCode = """
        (function() {
            if (window.androidDeselectSelectedAircraft) {
                window.androidDeselectSelectedAircraft();
                return;
            }

            if (typeof deselectAllPlanes === 'function') {
                deselectAllPlanes();
            } else {
                if (typeof SelectedPlane !== 'undefined') {
                    SelectedPlane = null;
                }
                if (typeof refreshSelected === 'function') {
                    refreshSelected();
                }
            }
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.hideInfoBlock() {
    log(MapHandler.TAG) { "Hiding #selected_infoblock off-screen" }
    val jsCode = """
        (function() {
            if (window.infoBlockHidden) return;
            var style = document.createElement('style');
            style.textContent = '#selected_infoblock { position: fixed !important; left: -9999px !important; opacity: 0 !important; pointer-events: none !important; } #credits, #selected_sitedist, #selected_sitedist1, #selected_sitedist2 { display: none !important; }';
            document.head.appendChild(style);
            window.infoBlockHidden = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}
