package eu.darken.apl.map.core

import android.webkit.WebView
import eu.darken.apl.common.debug.logging.log

internal fun WebView.ensureMapLayer(layerKey: String) {
    log(MapHandler.TAG) { "ensureMapLayer($layerKey)" }
    val safeKey = MapLayer.fromKey(layerKey).key
    val jsCode = """
        (function() {
            localStorage['MapType_tar1090'] = '$safeKey';
            if (window._mapLayerInterval) clearInterval(window._mapLayerInterval);

            function switchBaseLayers(collection, key) {
                var found = false;
                collection.forEach(function(lyr) {
                    if (typeof lyr.getLayers === 'function') {
                        if (switchBaseLayers(lyr.getLayers(), key)) found = true;
                    } else if (lyr.get && lyr.get('type') === 'base') {
                        lyr.setVisible(lyr.get('name') === key);
                        found = true;
                    }
                });
                return found;
            }

            window._mapLayerInterval = setInterval(function() {
                if (typeof OLMap === 'undefined' || typeof OLMap.getLayers !== 'function') return;
                try {
                    if (switchBaseLayers(OLMap.getLayers(), '$safeKey')) {
                        clearInterval(window._mapLayerInterval);
                        window._mapLayerInterval = null;
                    }
                } catch(e) { /* OLMap not fully ready yet */ }
            }, 500);
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

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
            var mapPollAttempts = 0;
            var checkMap = setInterval(function() {
                mapPollAttempts++;
                if (mapPollAttempts >= 60) {
                    clearInterval(checkMap);
                    console.log('Map position hook: gave up after 60 attempts');
                    return;
                }
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
            window._detailsPollInterval = setInterval(function() {
                sendUpdate();
            }, 1000);

            window.androidDeselectSelectedAircraft = function() {
                try {
                    window.androidDeselecting = true;
                    window.androidDeselectingTimer = setTimeout(function() {
                        window.androidDeselecting = false;
                        window.androidDeselectingTimer = null;
                    }, 2000);

                    lastJsonSent = '';

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

internal fun WebView.hideButtonSidebar() {
    log(MapHandler.TAG) { "Hiding #header_top, #header_side and #sidebar_container" }
    val jsCode = """
        (function() {
            if (window.buttonSidebarHidden) return;
            var style = document.createElement('style');
            style.textContent = '#header_top, #header_side, .ol-zoom, #altitude_chart, .layer-switcher { display: none !important; } #sidebar_container { position: fixed !important; left: -9999px !important; width: 0px !important; opacity: 0 !important; pointer-events: none !important; overflow: hidden !important; }';
            document.head.appendChild(style);
            window.buttonSidebarHidden = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.setupButtonStateHook() {
    log(MapHandler.TAG) { "Setting up button state observation hook" }
    val buttonIds = MapControl.entries.joinToString(",") { "'${it.buttonId}'" }
    val jsCode = """
        (function() {
            if (window.buttonStateHookAdded) return;

            var buttonIds = [$buttonIds];
            var debounceTimer = null;

            function readButtonStates() {
                var states = {};
                for (var i = 0; i < buttonIds.length; i++) {
                    var btn = document.getElementById(buttonIds[i]);
                    if (btn) {
                        states[buttonIds[i]] = btn.classList.contains('activeButton');
                    }
                }
                Android.onButtonStatesChanged(JSON.stringify(states));
            }

            function scheduleRead() {
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function() {
                    debounceTimer = null;
                    readButtonStates();
                }, 160);
            }

            var attempts = 0;
            var initInterval = setInterval(function() {
                attempts++;
                var found = 0;
                for (var i = 0; i < buttonIds.length; i++) {
                    if (document.getElementById(buttonIds[i])) found++;
                }
                if (found > 0 || attempts >= 5) {
                    clearInterval(initInterval);
                    readButtonStates();

                    for (var i = 0; i < buttonIds.length; i++) {
                        var btn = document.getElementById(buttonIds[i]);
                        if (btn) {
                            new MutationObserver(function() {
                                scheduleRead();
                            }).observe(btn, { attributes: true, attributeFilter: ['class'] });
                        } else {
                            console.warn('MapControl button not found: ' + buttonIds[i]);
                        }
                    }
                }
            }, 500);

            window.buttonStateHookAdded = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.executeMapToggle(buttonId: String) {
    log(MapHandler.TAG) { "executeMapToggle($buttonId)" }
    val jsCode = """
        (function() {
            var btn = document.getElementById('$buttonId');
            if (btn) btn.click();
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.setupAircraftListExtraction() {
    log(MapHandler.TAG) { "Setting up aircraft list extraction hook" }
    val jsCode = """
        (function() {
            if (window.aircraftListExtractionAdded) return;

            var lastJson = '';

            function extractAircraftList() {
                if (typeof g === 'undefined' || !g.planesOrdered || !g.planesOrdered.length) return;

                var planes = g.planesOrdered;
                var total = planes.length;
                var onScreen = 0;
                var list = [];
                var limit = 200;

                for (var i = 0; i < planes.length; i++) {
                    var p = planes[i];
                    if (!p || !p.icao) continue;
                    if (p.visible && p.position) onScreen++;
                    if (!p.visible || !p.position) continue;
                    if (list.length >= limit) continue;
                    var alt = '';
                    if (p.altitude !== null && p.altitude !== undefined) {
                        if (p.altitude === 'ground') {
                            alt = 'ground';
                        } else {
                            alt = p.altitude + ' ft';
                        }
                    }
                    var spd = '';
                    if (p.speed !== null && p.speed !== undefined) {
                        spd = Math.round(p.speed) + ' kt';
                    }
                    list.push({
                        hex: p.icao,
                        callsign: p.flight ? p.flight.trim() : '',
                        icaoType: p.icaoType || '',
                        squawk: p.squawk || '',
                        country: p.country || '',
                        altitude: alt,
                        speed: spd
                    });
                }

                var json = JSON.stringify({
                    totalAircraft: total,
                    onScreen: onScreen,
                    aircraft: list
                });
                if (json === lastJson) return;
                lastJson = json;
                Android.onAircraftListChanged(json);
            }

            // Wait for g.planesOrdered to become available, then start polling
            var waitAttempts = 0;
            var waitInterval = setInterval(function() {
                waitAttempts++;
                if (waitAttempts >= 120) {
                    clearInterval(waitInterval);
                    console.log('Aircraft list extraction: gave up waiting after 120 attempts');
                    return;
                }
                if (typeof g !== 'undefined' && g.planesOrdered && g.planesOrdered.length > 0) {
                    clearInterval(waitInterval);
                    extractAircraftList();
                    window._aircraftListPollInterval = setInterval(extractAircraftList, 2000);
                }
            }, 500);

            window.aircraftListExtractionAdded = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}

internal fun WebView.selectAircraft(hex: String) {
    log(MapHandler.TAG) { "selectAircraft($hex)" }
    val safeHex = hex.replace(Regex("[^a-fA-F0-9]"), "")
    val jsCode = """
        (function() {
            var hex = '$safeHex';
            if (typeof selectPlaneByHex === 'function') {
                selectPlaneByHex(hex, {follow: false});
            }
            // Center map on the aircraft
            if (typeof g !== 'undefined' && g.planesOrdered && typeof OLMap !== 'undefined') {
                for (var i = 0; i < g.planesOrdered.length; i++) {
                    var p = g.planesOrdered[i];
                    if (p.icao === hex && p.position) {
                        var pos = ol.proj.fromLonLat(p.position);
                        OLMap.getView().animate({center: pos, duration: 300});
                        break;
                    }
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
            window.adjustInfoBlock = function() {};
            window.infoBlockHidden = true;
        })();
    """.trimIndent()
    evaluateJavascript(jsCode, null)
}
