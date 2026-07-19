# Testing Guidelines

## What To Test

- **DO** write tests for anything related to **web APIs**
- **DO** write tests for data that is **serialized and stored** (to avoid breaking user data)
- **DO** write tests for non-trivial logic anywhere — including the UI layer (ViewModels,
  pure formatting/URL helpers). ViewModel tests run as plain JVM tests with
  `Dispatchers.setMain` + `TestDispatcherProvider`.
- UI-layer tests via **Compose Testing or Robolectric** are fine when they add value —
  don't avoid them on principle.

## What NOT To Test

- Anything that would require **instrumentation tests** (a device/emulator) — keep the
  suite JVM-only.
- Trivial declarative UI (layout/styling-only composables).

## Test Stack

- JUnit5 (Jupiter) for unit tests
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
