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
├── map/             # Native map (MapLibre, see below)
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

The map is native: [MapLibre Native](https://github.com/maplibre/maplibre-native) (OpenGL build) drawing [OpenFreeMap](https://openfreemap.org) base styles, with aircraft fed by the app's own server API. The website ([globe.airplanes.live](https://globe.airplanes.live), running tar1090) is only opened in the browser, via links built by `MapOptions.createUrl()`.

**Key files:**

| File | Purpose |
|------|---------|
| `MapAircraftProvider.kt` | Turns viewport/selection into map queries, server answers into frames (interpolated positions, trails, tracks) |
| `NativeMapViewModel.kt` | Screen state, sidebar, camera persistence, selection/pinning |
| `NativeMapView.kt` | MapLibre `MapView` hosting, style loading, GeoJSON sources and layers per frame |
| `SelectedTrail.kt` / `RecentTracks.kt` | Selected aircraft's server trail (cursor-based deltas) and short client-side tracks |
| `MapOptions.kt` | Browser links to the website (feeders, aircraft, camera) |

**Gotchas:**
- Polling runs only while something collects the frames. Collect anything derived from them lifecycle-aware (`collectAsStateWithLifecycle`, `repeatOnLifecycle(STARTED)`), or the app keeps polling in the background
- MapLibre zoom is tar1090 zoom − 1; `toMapLibreZoom()`/`toTar1090Zoom()` in `NativeMapView.kt` convert between them
- The plain `android-sdk` MapLibre artifact renders with Vulkan only; keep `android-sdk-opengl`

## Tech Stack

- **UI:** Jetpack Compose + Navigation3
- **DI:** Hilt
- **Async:** Kotlin Coroutines & Flow
- **Database:** Room
- **Preferences:** DataStore
- **Network:** Retrofit/OkHttp
- **Serialization:** Kotlinx Serialization
- **Images:** Coil 3