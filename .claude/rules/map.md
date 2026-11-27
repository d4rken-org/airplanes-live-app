---
paths:
  - "app/src/main/java/eu/darken/apl/map/**"
---

# Map: measurement and past decisions

## Measuring frame time

- Observation from one investigation: debuggable builds skip ahead-of-time code and inflated per-answer UI
  frames about 3x (29-34 ms debuggable, 19-26 ms non-debuggable JIT-only, 8-10 ms after
  `cmd package compile`). Measure on a temporary debug build with `isDebuggable = false` and
  `isProfileable = true` (same debug key, installs over a debug build with data), then
  `adb shell cmd package compile -m speed -f eu.darken.apl` and force-stop. Logging and `run-as` were
  unavailable in that build; reinstall the debuggable build afterwards.
- Render cost without root: CPU of the map's render thread from `/proc/<pid>/task/<tid>/schedstat` plus
  `dumpsys SurfaceFlinger --timestats`; on-device `perfetto` for traces.
- Noise (observation): three 20 s pans in one session repeated within ~2%, but the same build varied 5-10%
  between sessions at zoom 5. Alternate base and candidate over several sessions before calling a 10%
  difference. A cost seen in pans didn't show in a flick test, so measure the scenario itself. A 2 s
  `input swipe` doesn't fling; a 60 ms one glides.
- Label blinking: count labels that vanish and return in a `screenrecord`. Always record the build without
  the fix at the same camera first; a quiet scene (night, parked aircraft) shows no gaps on any build.
- Frame pacing: `screenrecord` is variable frame rate, so take real composition times from
  `ffprobe -show_entries frame=pts_time` and diff consecutive frames in the map area. Slow taxiing moves
  under a pixel per frame and shows as long gaps from the content, not dropped frames.

## Decisions

- Splitting traffic across several GeoJSON sources refreshed in turn (hash 4/8/16, speed bands, altitude
  bands) was measured and dropped. Hash splits removed the idle long frames, but every extra source made
  panning dearer (about 95 fps pans with one source, 60 with 4, 35 with 16); speed and altitude bands were
  dominated by one band. MapLibre Native has no partial GeoJSON update. Labels and "all tracks" showed no
  measurable frame-time difference. Don't propose source splitting again without a new angle.
- RainViewer radar is left out of the weather overlays (external terms, checked 2026-11): its free API is
  for personal/educational use and the Play build sells upgrades. Revisit with a paid global radar API.
  Free global alternatives found then: NOAA nowCOAST IR satellite mosaic (observed clouds), ECCC GDPS
  model precipitation (forecast, commercial use with attribution).
