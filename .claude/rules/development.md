# Development Commands

## Build

```bash
# Compile (local testing - use FOSS debug flavor)
./gradlew :app:compileFossDebugKotlin --no-daemon

# Build APKs
./gradlew app:assembleFossDebug
./gradlew app:assembleGplayRelease

# Verify R8 obfuscation (after touching any ProGuard/R8 rule file)
./gradlew :app:assembleGplayRelease :app:assembleFossRelease :app:bundleGplayRelease
bash tools/check-obfuscation.sh
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

## Install

- Observation: `./gradlew :app:installFossDebug` installed on every connected device and emulator. Only
  `ANDROID_SERIAL=<serial>` restricted it; `-Pandroid.injected.device.serial` was ignored. Prefer
  `assembleFossDebug` plus `adb -s <serial> install -r <apk>`.

## Debugging

```bash
adb logcat -v time | grep "APL:"  # Filter app logs (add -d for a dump)
```

Tags are `APL:<Area>:<Sub>` (`logTag()` in `common/debug/logging/LogExtensions.kt`), so `-s APL:V` matches no tag.

## Local build pitfalls

Environment problems seen locally, not code issues:

- Android Studio can write an untracked `gradle/gradle-daemon-jvm.properties` when it opens a checkout
  (seen with `toolchainVersion=25`; the file isn't in `.gitignore`). On JDK 25 Robolectric tests failed
  en masse (`Unsupported class file major version 69`, `NoClassDefFoundError android/webkit/RoboCookieManager`).
  Move the file aside for test runs.
- Kotlin compile OOM (`Not enough memory to run compilation`): the Kotlin daemon inherits the 2 GB
  `org.gradle.jvmargs` from `gradle.properties` and can be shared with other builds.
  `-Pkotlin.daemon.jvmargs=-Xmx6g` starts a separate daemon with more heap without editing tracked config.
  `--max-workers` alone didn't help.
- CI dependency downloads failing with HTTP 429 from Maven Central were transient. Confirm the failing
  request in the log, re-run the failed jobs (`gh run rerun <id> --failed`), and investigate if it persists.

## Build Variants

- **Flavors:** `foss` (F-Droid), `gplay` (Google Play)
- **Build types:** `debug`, `beta`, `release`
- **Min SDK:** 26 | **Target SDK:** 36 | **Compile SDK:** 37

## Commit Messages

Format: `<area>: <description>` (user-friendly for changelogs)
Areas: Map, Search, Feeder, Watch, Common, UI, Build

## Important Locations

- Database schemas: `app/schemas/`
- ProGuard rules: `app/proguard/`
- CI/CD: `.github/workflows/`
- Version config: `version.properties`

## Pull requests

- Changes land through a PR to `main`; never push to `main` directly.
- GitHub's rebase-merge re-applies the PR onto the current `main`, and nothing re-validates that
  combination before it lands. Directly before `gh pr merge` (after any approval wait), `git fetch` and
  run `git merge-base --is-ancestor origin/main origin/<branch>` as its own command and read the result.
  Merge only if it succeeds and CI is green for that exact head; otherwise update the branch and wait for
  CI. A race remains between the check and the merge.
- To bring an open PR onto a moved `main`, prefer `gh pr update-branch <n> --rebase` over a local rebase
  plus force-push. Check `git merge-tree --write-tree origin/main origin/<branch>` for conflicts first
  (update-branch can't resolve them), then re-point the local branch to the remote one.
