# Architecture

## Pattern

**MVVM with Repository Pattern** organized by feature.

## Module Structure

```
app/src/main/java/eu/darken/apl/
├── common/          # Shared utilities (DI, HTTP, coroutines, Room, DataStore)
├── main/            # Aircraft tracking (AircraftRepo, AirplanesLiveApi)
├── feeder/          # Feeder management (FeederRepo, background monitoring)
├── map/             # Map display and settings
├── search/          # Aircraft search functionality
└── watch/           # Alerts system (ICAO, SQUAWK watches)
```

## Key Patterns

- ViewModels use `@HiltViewModel` and extend `ViewModel4`
- Repositories combine API, Room database, and DataStore sources
- All reactive data uses Kotlin Flow
- Background work via WorkManager with Hilt integration
- Navigation via Navigation3 with `NavigationEntry` multibinding

## Tech Stack

- **UI:** Jetpack Compose + Navigation3
- **DI:** Hilt
- **Async:** Kotlin Coroutines & Flow
- **Database:** Room
- **Preferences:** DataStore
- **Network:** Retrofit/OkHttp
- **Serialization:** Kotlinx Serialization
- **Images:** Coil 3