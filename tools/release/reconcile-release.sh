#!/usr/bin/env bash
# Checks for an existing GitHub release of a tag before release-tag.yml builds it,
# so a rerun of the tagged-release workflow neither duplicates nor overwrites one.
# Called by release-tag.yml.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: reconcile-release.sh <tag> <repo>

Looks up the GitHub release for <tag> in <repo> (owner/name) and appends
published=true or published=false to $GITHUB_OUTPUT:

  no release                       published=false
  draft (from a failed run)        deleted (the tag stays), published=false
  published with an .apk asset     published=true, the build can be skipped
  published without an .apk        error
  lookup fails for another reason  error

Nothing is appended to $GITHUB_OUTPUT on an error.
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  echo "$*" >&2
}

if [[ $# -ne 2 ]]; then
  usage >&2
  exit 2
fi

tag="$1"
repo="$2"

[[ -n "$tag" ]] || die "tag must not be empty"
[[ -n "$repo" ]] || die "repo must not be empty"
[[ -n "${GITHUB_OUTPUT:-}" ]] || die "GITHUB_OUTPUT is not set"

view_stderr="$(mktemp)"
trap 'rm -f "$view_stderr"' EXIT

view_status=0
view_json=$(gh release view "$tag" --repo "$repo" --json isDraft,assets 2>"$view_stderr") || view_status=$?

if [[ "$view_status" -ne 0 ]]; then
  if grep -qF 'release not found' "$view_stderr"; then
    log "No release for $tag yet."
    echo "published=false" >> "$GITHUB_OUTPUT"
    exit 0
  fi
  echo "::error::Looking up the release for $tag failed (gh exit $view_status)." >&2
  cat "$view_stderr" >&2
  exit 1
fi

if ! jq -e '(.isDraft | type == "boolean") and (.assets | type == "array")' <<<"$view_json" >/dev/null 2>&1; then
  echo "::error::Unexpected answer from gh release view for $tag:" >&2
  log "$view_json"
  exit 1
fi

is_draft=$(jq -r '.isDraft' <<<"$view_json")
apk_count=$(jq '[.assets[] | objects | .name | strings | select(endswith(".apk"))] | length' <<<"$view_json")

if [[ "$is_draft" == "true" ]]; then
  log "Release $tag is a stale draft from a failed run. Deleting it so this run can rebuild it."
  gh release delete "$tag" --repo "$repo" --yes
  echo "published=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

if [[ "$apk_count" -gt 0 ]]; then
  log "Release $tag is already published with an APK. Skipping the build and the release."
  echo "published=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

echo "::error::Release $tag is published but has no APK. Attach the APK to the release by hand or delete the release, then rerun this workflow." >&2
exit 1
