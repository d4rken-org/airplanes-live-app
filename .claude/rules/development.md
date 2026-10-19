# Development Commands

## Build

```bash
# Compile (local testing - use FOSS debug flavor)
./gradlew :app:compileFossDebugKotlin --no-daemon

# Build APKs
./gradlew app:assembleFossDebug
./gradlew app:assembleGplayRelease
```

## Test

```bash
# Unit tests
./gradlew testFossDebugUnitTest
./gradlew testGplayDebugUnitTest
```

## Lint

```bash
./gradlew lintVitalFossBeta
./gradlew lintVitalGplayRelease
```

## Debugging

```bash
adb logcat -v time -s APL:V  # Filter app logs
```

## Build Variants

- **Flavors:** `foss` (F-Droid), `gplay` (Google Play)
- **Build types:** `debug`, `beta`, `release`
- **Min SDK:** 26 | **Target SDK:** 34 | **Compile SDK:** 36

## Commit Messages

Format: `<area>: <description>` (user-friendly for changelogs)
Areas: Map, Search, Feeder, Watch, Common, UI, Build

## Important Locations

- Database schemas: `app/schemas/`
- ProGuard rules: `app/proguard/`
- CI/CD: `.github/workflows/`
- Version config: `version.properties`
