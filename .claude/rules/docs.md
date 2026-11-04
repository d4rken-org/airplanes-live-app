---
paths:
  - "docs/**"
  - "tooling/docs/**"
---

# Docs site (apl.darken.eu)

- English pages are `docs/<page>.md` plus the homepage `docs/index.html`; translations are
  `docs/<path>/<page>` for each language in `docs/_data/languages.json`, with site labels in
  `docs/_data/ui/<code>.json`.
- `CHANGELOG.md`, `PRIVACY_POLICY.md` and `SECURITY.md` live in the repo root and are copied into
  `docs/` by the Pages workflow, together with the icon and the English fastlane screenshots. Edit the
  root files, never the ignored copies. They stay English-only (`tooling/docs/english_only.json`). The
  app opens `/privacy` (`PrivacyPolicy.URL`), so that route must keep working. `SECURITY.md` lists the
  release signing certificate; update it if the signing key ever changes.
- Guide screenshots are English-only WebP files in `docs/assets/images/<page>/`, linked from every
  language. Capture them on an emulator at 1080x2400 with the demo status bar, as
  `fastlane/capture_map_screenshots.sh` sets it up. Keep Planespotters photos out (pick an aircraft
  without one). Map screens need the normal build for live traffic. For screens with server-set
  numbers, such as the free watch count in the add-watch dialog, use a build with
  `-PaplServerBaseUrl=https://127.0.0.1:9/` on cleared app data, and check that no count shows.
  Commit them downscaled to 540x1200 WebP; the embeds use those dimensions.
- Every heading needs a `{: #id }` line directly below it. Pages link these anchors
  (e.g. `/feeders#feeder-id`), and translations keep the same IDs, so never change an existing ID.
- Name UI elements exactly as the app's `values/strings.xml` does (`values-<code>` for translated
  pages), and check Pro gating in the code before describing a feature. Daily allowances and watch
  counts can change without an app update: describe them only with the wording the app shows,
  without numbers. Limits fixed in the app's code (check intervals, radius, AR range) may be stated.
- Editing an English page makes its translations stale (their `source_hash` no longer matches). The
  site then shows an "out of date" notice on them; the build does not fail. A new English page shows
  English in every language until translated.
- To resync a language, retranslate only the changed sections, then set `source_hash` to
  `python3 tooling/docs/check_translations.py docs --hash <page>`. A current translation must match
  its English page's rendered headings exactly, or the Pages build fails.
- Check locally by running the "Add the root pages and app images" step of
  `.github/workflows/pages.yml`, then `python3 tooling/docs/check_translations.py docs`, a Jekyll build
  from `docs/` into `../_site`, `check_links.py _site .` and `check_translations.py docs --site _site`.
