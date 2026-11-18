# Releasing APL

## How to cut a release

1. In the Actions tab, run the **Release prepare** workflow from `main`. Other refs are refused.
2. Pick:
   - **bump_kind**: `build` (follow-up build), `patch` (bug fix), `minor` (new feature), `major` (breaking change). Ignored when `version_override` is set.
   - **version_override**: an explicit version such as `1.0.1-rc0`, overriding `bump_kind`.
   - **expected_current**: optional safety check, fails if `main` is not on the version you expect.
   - **dry_run**: `true` to preview the plan, `false` to commit, tag and push.
3. With `dry_run=false`, job 1 validates and job 2 commits the bump as `Release: <version>`, creates the annotated tag `v<version>`, and pushes both atomically. That push is made with a GitHub App token, which is what makes **Tagged releases** (`release-tag.yml`) fire; a `GITHUB_TOKEN` push would not trigger it.
4. Approve or reject each release job when it asks. See "Both release jobs are gated" below.

A dry run does not exercise the App token: token minting lives in job 2, which only runs when `dry_run` is false.

## Version scheme

Defined in `tools/release/bump.sh`, mirrored by `buildSrc/src/main/java/ProjectConfig.kt`. APL is rc-only. All fields must be in `0..99`, the formula overflows at 100:

```
versionName = <major>.<minor>.<patch>-rc<build>   (e.g. 1.0.0-rc0)
versionCode = major*10_000_000 + minor*100_000 + patch*1_000 + build*10
```

Files updated by every release:
- `version.properties`, read by Gradle at build time.
- `VERSION`, one line `<versionName> <versionCode>`.

## Tag to channel mapping

| Tag | FOSS build | GitHub release | Gplay lane | Play track | Play release status |
|---|---|---|---|---|---|
| `v<M.m.p>-rc<n>` | `assembleFossRelease` | release | `lane :production` | `alpha` | draft |

Play's API track names do not match the Console labels: `internal` is Internal testing, `alpha` is Closed testing, `beta` is Open testing. APL is in closed testing, so `lane :production` uploads to **`alpha`**, not to production. Release notes come from `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`, or `default.txt` when that file does not exist.

After a successful run, `refresh-pages` dispatches the Pages workflow on `main` so the website changelog picks up the new GitHub release.

## Both release jobs are gated

The `foss-production` and `gplay-production-upload` environments both require a review from @d4rken, so each release job pauses until it is approved or rejected from the run page. The two jobs run in parallel and neither depends on the other: rejecting the Play job marks it red and leaves the FOSS release untouched.

A rejected deployment never executes a step, so nothing reaches Play. Cancelling the run instead would kill the FOSS build with it.

## Play uploads are drafts

`lane :production` uploads with `release_status: 'draft'`. Play requires that until `eu.darken.apl` has a published release, so every upload lands as a draft on Closed testing and is rolled out by hand in the Play Console. Once the app has a published release, remove `release_status` from the lane in `fastlane/Fastfile`.

### First Play upload

The app is created in the Play Console and its first bundle is uploaded there by hand, signed with the same upload key the `gplay-production-upload` secrets hold. Play never accepts a version code twice, so reject the Play job for the tag whose bundle was uploaded by hand; the next release gets a higher version code.

## Dry runs

Run **Tagged releases** from `main` with `dry_run` checked. Tag checks are skipped. The FOSS job builds the APK and the Play job builds the signed bundle with `./gradlew :app:bundleGplayRelease`; nothing is released or uploaded, and the Play job does not need the service account key. The environment reviews still apply.

## Validation guards

- `bump.sh` fails when `version.properties` and `VERSION` disagree. **Release prepare** and `validate-tag` both run it.
- `check-release-tooling` in `code-checks.yml` runs shellcheck on `tools/release/bump.sh` and `tools/release/reconcile-release.sh`, both bats suites in `tools/release/`, and a live `bump.sh --mode=check` on every pull request against `main` and every push to `main`, so `version.properties` and `VERSION` cannot drift apart unnoticed.
- `validate-tag` in `release-tag.yml` rejects a tag that does not match `v<M.m.p>-rc<N>` or the committed version. Both release jobs need it, so a live run of **Tagged releases** from a branch builds nothing.
- The GitHub release job refuses a live run from a ref that is not a tag. Dry runs can still build from a branch.
- **Release prepare** refuses a tag that already exists locally or on the remote. Job 2 repeats that check and re-checks that `main` is still on the version job 1 planned from before it writes anything.

## Reruns after a failure

The release action creates the GitHub release as a draft, attaches the APK, then publishes it. Before building, `tools/release/reconcile-release.sh` checks what an earlier run left for the tag:

- Published with an APK: the build and the release are skipped and the rerun goes green.
- A draft: it is deleted (the tag stays) and rebuilt.
- Published without an APK: the job fails. Attach the APK to the release by hand, or delete the release, then rerun.
- The lookup fails for any reason other than "release not found": the job fails. Rerun once GitHub answers again.

A rerun, or a dispatch on a tag, runs the workflow as it was at that tag's commit, so this applies to tags cut after this change. Older tags keep their old behaviour.

Re-running a Play job whose upload already reached Play fails on the reused version code. Check the Play Console before re-running; shipping another binary needs a new build number via **Release prepare**.

## Running fastlane locally

From the repository root, with Ruby 3.3.6:

```
export BUNDLE_GEMFILE="$PWD/fastlane/Gemfile"
bundle install
export SUPPLY_JSON_KEY=<path to the service account JSON key>
bundle exec fastlane listing_only
```

Instead of `SUPPLY_JSON_KEY`, a local `fastlane/Appfile` can hold `json_key_file("<path to the service account JSON key>")`. That file is gitignored, keep the key path out of tracked files.

- `lane :listing_only` uploads the listing texts from `fastlane/metadata/android`.
- `lane :screenshots_only` uploads only the screenshots in `fastlane/metadata/android/*/images/*Screenshots`.

Neither uploads a binary, but both target `alpha` and need a release there. When `alpha` holds more than one release (for example a draft beside the live one), pick one with `bundle exec fastlane listing_only version_code:<code>`.

Every lane first runs `fastlane/remove_unsupported_languages.sh`, which deletes the metadata folders of locales Play does not support. Locales that Play rejects go in its list. A local run deletes those folders from the working tree, so restore any tracked ones with git afterwards.

## Repo setup

Environment secrets (names only):

| Environment | Secrets |
|---|---|
| `foss-production` | `SIGNING_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |
| `gplay-production-upload` | the same four, holding the Play upload key rather than the FOSS key, plus `GPLAY_SERVICE_ACCOUNT_KEY_JSON_BASE64` |

A dry run of the Play job needs only the signing secrets.
