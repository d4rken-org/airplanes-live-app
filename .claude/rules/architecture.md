# Architecture

## Pattern

**MVVM with Repository Pattern** organized by feature.

## Module Structure

```
app/src/main/java/eu/darken/apl/
├── backup/          # Watchlist backup/restore
├── common/          # Shared utilities (DI, HTTP, coroutines, Room, DataStore)
├── main/            # Aircraft tracking (AircraftRepo, AirplanesLiveApi)
├── feeder/          # Feeder management (FeederRepo, background monitoring)
├── map/             # Map view (WebView wrapping tar1090, see below)
├── search/          # Aircraft search functionality
└── watch/           # Alerts system (ICAO, SQUAWK watches)
```

## Key Patterns

- ViewModels use `@HiltViewModel` and extend `ViewModel4`
- Repositories combine API, Room database, and DataStore sources
- All reactive data uses Kotlin Flow
- Background work via WorkManager with Hilt integration
- Navigation via Navigation3 with `NavigationEntry` multibinding

## Map Architecture

The map is **not a native map** — it's a WebView loading [globe.airplanes.live](https://globe.airplanes.live), which runs [tar1090](https://github.com/wiedehopf/tar1090) (open-source ADS-B map viewer using OpenLayers).

**Key files:**

| File | Purpose |
|------|---------|
| `MapHandler.kt` | WebView lifecycle, page load, CSS height fix for Compose hosting |
| `MapHooks.kt` | ~600 lines of JavaScript injected into tar1090 for map control |
| `MapWebInterface.kt` | `@JavascriptInterface` bridge (Web → Android callbacks) |
| `MapOptions.kt` | URL parameter building/parsing for map state |

**Communication pattern:**
- **Android → Web:** `evaluateJavascript()` calls manipulating tar1090 DOM and OpenLayers APIs
- **Web → Android:** `Android.*` JavascriptInterface methods defined in `MapWebInterface.kt`

**Gotchas:**
- CSS `height: 100%` doesn't resolve in Compose-hosted WebViews — `MapHandler` injects JS to set explicit pixel heights
- `MapHooks.kt` references tar1090 internals (`OLMap`, `localStorage['MapType_tar1090']`) — changes to tar1090's JS API can break the app

## Tech Stack

- **UI:** Jetpack Compose + Navigation3
- **DI:** Hilt
- **Async:** Kotlin Coroutines & Flow
- **Database:** Room
- **Preferences:** DataStore
- **Network:** Retrofit/OkHttp
- **Serialization:** Kotlinx Serialization
- **Images:** Coil 3