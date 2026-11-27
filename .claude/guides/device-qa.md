# On-device QA

## Shared devices and app data

- `adb install -r` keeps app data, and every Room DB migrates forward only, so an older build over data a
  newer schema migrated crashes at launch ("A migration from N to N-1 was required"). Compare `version =`
  in each `*RoomDb.kt` of both builds first.
- To read the on-device version on a debug build, copy `databases/<db>` and `<db>-wal` out with
  `adb exec-out run-as eu.darken.apl cat …` and run `sqlite3 <db> 'PRAGMA user_version'` on the host (the
  main file's header can lag behind the WAL). DB files: `aircraft`, `watch`, `flights`.
- An unknown version is not permission to downgrade; use a disposable emulator. After any reinstall,
  relaunch and check `logcat -b crash` and `pidof`.
- Restore any device state you change (camera, rotation, network, recorder) before handing the device back.

## Logs from release builds

- Release builds install no logcat logger. The debug recorder starts at launch when `force_debug_run`
  exists in the app's external files dir (`common/debug/recorder/core/RecorderModule.kt`):
  `adb shell "mkdir -p /sdcard/Android/data/eu.darken.apl/files && touch /sdcard/Android/data/eu.darken.apl/files/force_debug_run"`,
  force-stop, relaunch, then read `/sdcard/Android/data/eu.darken.apl/cache/debug/logs/`.
- Deleting the file doesn't stop it (the recorder persists its log path); stop it in Settings > Support >
  Debug log. If a recording was already running, leave it to its owner.
- Logs can contain locations, searches and watch details: keep them private and delete them after use.
- Retrace gplay traces with the SDK's `retrace` and the mapping of the exact build that produced them (see
  the R8 section of `.claude/rules/architecture.md`). The local `app/build/outputs/mapping/gplayRelease/`
  mapping only fits a build from the same tree.

## Map camera and gestures

- adb can't pinch. `fastlane/capture_map_screenshots.sh` (`write_preferences`) shows how to write
  `settings_map.preferences_pb`, including `map.last.camera` (`{"lat":..,"lon":..,"zoom":..}`, tar1090
  zoom); that script clears app data. For a manual edit, stop the app, edit the file through `run-as`
  (push via `/data/local/tmp`), keep a copy of the original bytes and restore it afterwards. The camera
  only restores while "Restore last view" is on (`NativeMapViewModel.savedCamera()`).
- Observation: one-finger double-tap zooms in; double-tap-and-drag zoomed out on a phone but mostly panned
  on an API 36 emulator. Two overlapping `input tap` processes (`input tap X Y & sleep 0.12; input tap X Y; wait`)
  count as a double-tap; two sequential calls are too slow.
- Check that the APL activity has focus before each scripted tap (`dumpsys window | grep mCurrentFocus`);
  prefer `am start` over Back.

## Rotation

- UI hierarchy dumps (uiautomator) restore the rotation they saved, undoing
  `settings put system user_rotation`. Use `adb shell wm user-rotation lock <0|1>`, verify with
  `dumpsys window | grep mRotation`, and `wm user-rotation free` afterwards.

## Offline

- `adb shell svc wifi disable` and `svc data disable`, then check that a `ping` fails. Back online with
  `adb shell cmd wifi set-wifi-enabled enabled` and `svc data enable`.

## Location

- Map and Search request coarse location; AR requests fine (`ar/ui/ArScreen.kt`), and
  `common/location/LocationUpdates.kt` picks providers by whether fine is granted. Check the actual grants
  and `dumpsys location` before blaming throttling.
- Observations with coarse only on Android 12+: requests show as `(COARSE) Request[@+10m0s0ms LOW_POWER]`,
  one new fix per 10 min, and a repeat `getCurrentLocation` inside that window returned a cached fix or
  nothing, so stale `et=` timestamps are expected.
- The emulator GPS produced a fix only on `adb emu geo fix`. The `android-31;google_apis_playstore` image
  refused every app location registration.

## Watch alerts

- A notification fires when a watch goes from nothing to something; a match on the watch's very first
  check stays quiet (`docs/watchlist.md#notifications`). A QA step that watches an aircraft already on air
  from the first check and waits for a notification can't pass.
- Creating or deleting a watch enqueues `WatchListSyncWorker`; pull-to-refresh runs a foreground check
  instead. For sync after a create/delete, require evidence for that mutation, such as the new watch
  showing server data for itself.
- A worker success alone doesn't prove the upload. The app logs `Finished: <result>` under
  `APL:Watch:List:Worker`; WorkManager's own `Worker result` lines carry no `APL:` tag, so the
  `development.md` filter hides them.

## History replay

- History replay depends on the access policy (`AccessState.offersAircraftHistory`), so a fresh install
  usually can't open it.

## Text truncation

- Judge it from a full-resolution `adb exec-out screencap -p`; downscaled screenshots get letter counts
  wrong. Fit tests: `.claude/rules/testing.md`.

## UI states the app can't reach

- In a throwaway detached worktree add `app/src/debug/java/<pkg>/XMockups.kt` with public
  `@Preview @Composable fun MockNN()` functions that call the stateless `XScreen(state = …)` inside
  `PreviewWrapper`, build a debug APK (debug builds include `ui-tooling`), and open a state with
  `adb shell am start -S -n eu.darken.apl/androidx.compose.ui.tooling.PreviewActivity --es composable eu.darken.apl.<pkg>.XMockupsKt.MockNN`.
- `-S` is required, or the running PreviewActivity keeps its screen. Never commit the mockup file.
