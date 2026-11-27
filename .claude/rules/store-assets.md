---
paths:
  - "fastlane/**"
  - "app/src/debug/java/eu/darken/apl/screenshots/**"
  - "app/src/screenshotTest/**"
---

# Store listing assets

- en-US set: `1_map` (map following a flight, aircraft sheet open), `2_location_watch`, `3_airport`
  (arrivals board), `4_watch`, `5_search` (airport result), `6_map_overlays`, `7_feeders`, plus
  `images/featureGraphic.png`. Other locales are generated on demand and gitignored.
  `.github/workflows/pages.yml` copies 1_map, 4_watch, 5_search and 7_feeders by name.
- 1, 3 and 6 are manual emulator captures with live data on the Free tier (`capture_map_screenshots.sh`
  only does a bare map; sheets were opened with adb taps after it). 2, 4, 5 and 7 are Compose renders from
  `ScreenshotContent.kt` via `generate_screenshots.sh` and `copy_screenshots.sh`.
- Screenshot tests run with `user.timezone=Europe/Berlin` (`app/build.gradle.kts`); 7_feeders still shifts
  each run because it uses `Instant.now()`.
- Play wants 24-bit PNG without alpha. The capture and copy scripts run `opaque_png.py`; run it by hand on
  manual captures before committing.
- `feature_graphic.py` renders the feature graphic from `icon.png` and `1_map.png`; rerun it after retaking
  1_map. The app name, icon tile and phone frame are deliberate.
- No third-party aircraft photos in any store image: 1_map's aircraft must show the "Submit photo"
  placeholder. Few US airliners lack a Planespotters photo; find one with fleet searches in the Search tab
  (e.g. `DAL A21N`) and check its position via the public airplanes.live API. Each search counts against
  the install's Free daily allowance.
- US airliners often show the registered owner (a bank trustee) as operator; pick a flight whose sheet
  shows the airline.
- Device state for captures: map camera via `capture_map_screenshots.sh` or `.claude/guides/device-qa.md`;
  demo-mode state is in `dumpsys activity service com.android.systemui/.SystemUIService DemoModeController`;
  `cmd locale set-app-locales eu.darken.apl --locales de-DE` switches the app locale.
