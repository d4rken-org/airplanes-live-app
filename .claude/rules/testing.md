# Testing Guidelines

## What To Test

- **DO** write tests for anything related to **web APIs**
- **DO** write tests for data that is **serialized and stored** (to avoid breaking user data)
- Compose UI tests are fine when they catch behavior a unit test can't: enabled state derived from screen state, callbacks firing, conditional content, back/sheet handling. Extend `BaseComposeTest` and drive the stateless `XScreen` composable with a hand-built state, never `XScreenHost` (it needs Hilt and a ViewModel). Example: `AddFeederScreenTest`

## What NOT To Test

- Visual appearance, layout, pixel positions: Robolectric doesn't draw, and in its default graphics mode it fakes text metrics. Whether text fits can be tested, see "Text fit"
- Text that only echoes the state you passed in
- The MapLibre map view: its native code doesn't run on Robolectric. Test map logic in `MapAircraftProvider` and the ViewModel

## Text fit

- `@GraphicsMode(GraphicsMode.Mode.NATIVE)` gives real text metrics. `@Config(qualifiers = "420dpi")` (a common phone density) shows kerning issues the default density hides. Example: `AirportFlightRowFitTest` (also `MapTopBarFitTest`, `BottomNavBarTest`)
- With Robolectric 4.11.1 (`buildSrc/src/main/java/Dependencies.kt`) in NATIVE mode, `hasVisualOverflow` reported overflow for text that fit. Use `isLineEllipsized`, `getLineEnd(0, visibleEnd = true)` and the node's `positionInRoot` (`boundsInRoot` clips when the box is wider than the window)
- The platform ellipsizer needs a few px more than a plain measurement of "prefix…" (kerning, e.g. names starting VA/AT/LY/LO/TA). Decide fits from the same ellipsizing `TextMeasurer` call the `Text` uses, not from summed standalone widths

## ViewModel tests and virtual time

- `TestDispatcherProvider()` without an argument falls back to `Dispatchers.Unconfined`, and `vmScope` (`common/uix/ViewModel2.kt`) runs on its `Default`, so `advanceTimeBy` never reaches delays inside the ViewModel (`WhileSubscribed(5000)` stops, `withTimeoutOrNull`). Pass `TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))` and `setMain` on the same scheduler
- A ViewModel with an endless clock loop makes `runTest` hang (`UncompletedCoroutinesError`); cancel its `viewModelScope` at the end. Example: `vmTest` in `WatchDetailsViewModelLiveCheckTest`

## Test Stack

- JUnit5 (Jupiter) for unit tests
- JUnit4 + Robolectric for tests that need the Android framework, including Compose UI tests (Robolectric doesn't support JUnit5)
- Kotest for assertions
- MockK for mocking
- MockWebServer for HTTP endpoint testing

## Running Tests

```bash
# Local testing - use FOSS debug flavor
./gradlew testFossDebugUnitTest

# Other variants
./gradlew testGplayDebugUnitTest
```
