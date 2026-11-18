#!/usr/bin/env bats
# Unit tests for reconcile-release.sh. Run with: bats tools/release/reconcile-release.bats
# A stub gh on PATH answers from per-test fixtures and logs each call, one per line.

TAG="v1.0.0-rc1"
REPO="owner/repo"

setup() {
  RECONCILE_SH="${BATS_TEST_DIRNAME}/reconcile-release.sh"

  mkdir -p "$BATS_TEST_TMPDIR/bin"
  cat > "$BATS_TEST_TMPDIR/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$GH_LOG"
case "$1 $2" in
  "release view")
    [[ -f "$GH_VIEW_STDOUT" ]] && cat "$GH_VIEW_STDOUT"
    [[ -f "$GH_VIEW_STDERR" ]] && cat "$GH_VIEW_STDERR" >&2
    exit "${GH_VIEW_EXIT:-0}"
    ;;
  "release delete")
    exit "${GH_DELETE_EXIT:-0}"
    ;;
  *)
    echo "stub gh: unexpected call: $*" >&2
    exit 99
    ;;
esac
EOF
  chmod +x "$BATS_TEST_TMPDIR/bin/gh"
  PATH="$BATS_TEST_TMPDIR/bin:$PATH"

  export GH_LOG="$BATS_TEST_TMPDIR/gh.log"
  export GH_VIEW_STDOUT="$BATS_TEST_TMPDIR/view.stdout"
  export GH_VIEW_STDERR="$BATS_TEST_TMPDIR/view.stderr"
  export GH_VIEW_EXIT=0
  export GH_DELETE_EXIT=0
  export GITHUB_OUTPUT="$BATS_TEST_TMPDIR/github_output"
  : > "$GH_LOG"
  : > "$GITHUB_OUTPUT"
}

# view_answers <stdout> <stderr> <exit code>
view_answers() {
  printf '%s' "$1" > "$GH_VIEW_STDOUT"
  printf '%s' "$2" > "$GH_VIEW_STDERR"
  export GH_VIEW_EXIT="$3"
}

reconcile() {
  "$RECONCILE_SH" "$TAG" "$REPO"
}

view_call="release view $TAG --repo $REPO --json isDraft,assets"
delete_call="release delete $TAG --repo $REPO --yes"

delete_count() {
  grep -c '^release delete' "$GH_LOG" || true
}

@test "no release: published=false, nothing deleted" {
  view_answers "" "release not found" 1
  run reconcile
  [ "$status" -eq 0 ]
  [ "$(< "$GITHUB_OUTPUT")" = "published=false" ]
  [ "$(< "$GH_LOG")" = "$view_call" ]
}

@test "draft: deleted once without removing the tag, published=false" {
  view_answers '{"isDraft":true,"assets":[{"name":"eu.darken.apl-v1.0.0-rc1-10000010-FOSS-RELEASE.apk"}]}' "" 0
  run reconcile
  [ "$status" -eq 0 ]
  [ "$(< "$GITHUB_OUTPUT")" = "published=false" ]
  [ "$(delete_count)" -eq 1 ]
  [ "$(grep '^release delete' "$GH_LOG")" = "$delete_call" ]
  [[ "$(< "$GH_LOG")" != *"--cleanup-tag"* ]]
}

@test "draft whose delete fails: fails, no output" {
  view_answers '{"isDraft":true,"assets":[]}' "" 0
  export GH_DELETE_EXIT=1
  run reconcile
  [ "$status" -ne 0 ]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 1 ]
}

@test "published with an APK: published=true, nothing deleted" {
  view_answers '{"isDraft":false,"assets":[{"name":"eu.darken.apl-v1.0.0-rc1-10000010-FOSS-RELEASE.apk"}]}' "" 0
  run reconcile
  [ "$status" -eq 0 ]
  [ "$(< "$GITHUB_OUTPUT")" = "published=true" ]
  [ "$(< "$GH_LOG")" = "$view_call" ]
}

@test "published without assets: fails, nothing deleted, no output" {
  view_answers '{"isDraft":false,"assets":[]}' "" 0
  run reconcile
  [ "$status" -ne 0 ]
  [[ "$output" == *"::error::"*"no APK"* ]]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 0 ]
}

@test "published with only non-APK assets: fails, nothing deleted, no output" {
  view_answers '{"isDraft":false,"assets":[{"name":"notes.txt"}]}' "" 0
  run reconcile
  [ "$status" -ne 0 ]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 0 ]
}

@test "lookup error other than not found: fails with gh's error, nothing deleted, no output" {
  view_answers "" "HTTP 502: Bad Gateway" 1
  run reconcile
  [ "$status" -ne 0 ]
  [[ "$output" == *"::error::"* ]]
  [[ "$output" == *"HTTP 502: Bad Gateway"* ]]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 0 ]
}

@test "malformed JSON on success: fails, nothing deleted, no output" {
  view_answers 'not json' "" 0
  run reconcile
  [ "$status" -ne 0 ]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 0 ]
}

@test "JSON without the expected fields: fails, no output" {
  view_answers '{"isDraft":"false"}' "" 0
  run reconcile
  [ "$status" -ne 0 ]
  [ ! -s "$GITHUB_OUTPUT" ]
  [ "$(delete_count)" -eq 0 ]
}

@test "missing arguments: fails without calling gh" {
  run "$RECONCILE_SH" "$TAG"
  [ "$status" -ne 0 ]
  [ ! -s "$GH_LOG" ]
  [ ! -s "$GITHUB_OUTPUT" ]
}

@test "missing GITHUB_OUTPUT: fails without calling gh" {
  unset GITHUB_OUTPUT
  run reconcile
  [ "$status" -ne 0 ]
  [ ! -s "$GH_LOG" ]
}
