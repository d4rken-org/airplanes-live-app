# Testing Guidelines

## What To Test

- **DO** write tests for anything related to **web APIs**
- **DO** write tests for data that is **serialized and stored** (to avoid breaking user data)
- Compose UI tests are fine when they catch behavior a unit test can't: enabled state derived from screen state, callbacks firing, conditional content, back/sheet handling. Extend `BaseComposeTest` and drive the stateless `XScreen` composable with a hand-built state, never `XScreenHost` (it needs Hilt and a ViewModel). Example: `AddFeederScreenTest`

## What NOT To Test

- Visual appearance, layout, pixel positions: Robolectric doesn't draw and fakes text metrics
- Text that only echoes the state you passed in
- The MapLibre map view: its native code doesn't run on Robolectric. Test map logic in `MapAircraftProvider` and the ViewModel

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
