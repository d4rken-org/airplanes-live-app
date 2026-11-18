#!/usr/bin/env bats
# Unit tests for bump.sh. Run with: bats tools/release/bump.bats

setup() {
  BUMP_SH="${BATS_TEST_DIRNAME}/bump.sh"
  TMP_REPO="$BATS_TEST_TMPDIR/repo"
  mkdir -p "$TMP_REPO"
  cat > "$TMP_REPO/version.properties" <<'EOF'
### Updated by tools/release/bump.sh ###
project.versioning.major=1
project.versioning.minor=7
project.versioning.patch=1
project.versioning.build=0
#############################
EOF
  echo "1.7.1-rc0 10701000" > "$TMP_REPO/VERSION"
}

bump() {
  "$BUMP_SH" --repo-root="$TMP_REPO" "$@"
}

# ----- check mode ----------------------------------------------------------

@test "check: passes on consistent state" {
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=1.7.1-rc0"* ]]
  [[ "$output" == *"current_code=10701000"* ]]
}

@test "check: fails on VERSION/version.properties drift (name)" {
  echo "1.7.0-rc0 10700000" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on VERSION/version.properties drift (code)" {
  echo "1.7.1-rc0 99999999" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on duplicate key" {
  echo "project.versioning.major=2" >> "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"major"* ]]
  [[ "$output" == *"found 2"* ]]
}

@test "check: fails on missing key" {
  sed -i '/project\.versioning\.build=/d' "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"build"* ]]
  [[ "$output" == *"found 0"* ]]
}

@test "check: fails on malformed VERSION" {
  echo "garbage" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"VERSION file does not match"* ]]
}

@test "check: fails on a second VERSION line" {
  echo "extra" >> "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"exactly one line"* ]]
}

@test "check: fails on leading zero in version.properties" {
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=08/' "$TMP_REPO/version.properties"
  echo "1.7.1-rc8 10701080" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"leading zero"* ]]
}

# ----- plan: bump kinds ----------------------------------------------------

@test "plan: build bump increments build" {
  run bump --mode=plan --bump-kind=build
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.1-rc1"* ]]
  [[ "$output" == *"new_code=10701010"* ]]
}

@test "plan: patch bump zeros build" {
  run bump --mode=plan --bump-kind=patch
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.2-rc0"* ]]
  [[ "$output" == *"new_code=10702000"* ]]
}

@test "plan: minor bump zeros patch and build" {
  run bump --mode=plan --bump-kind=minor
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.8.0-rc0"* ]]
  [[ "$output" == *"new_code=10800000"* ]]
}

@test "plan: major bump zeros minor, patch, build" {
  run bump --mode=plan --bump-kind=major
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=2.0.0-rc0"* ]]
  [[ "$output" == *"new_code=20000000"* ]]
}

# ----- plan: versionCode formula -------------------------------------------

@test "plan: build bump from 0.0.0-rc0 gives versionCode 10" {
  sed -i 's/^project\.versioning\.major=.*/project.versioning.major=0/' "$TMP_REPO/version.properties"
  sed -i 's/^project\.versioning\.minor=.*/project.versioning.minor=0/' "$TMP_REPO/version.properties"
  sed -i 's/^project\.versioning\.patch=.*/project.versioning.patch=0/' "$TMP_REPO/version.properties"
  echo "0.0.0-rc0 0" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=0.0.0-rc1"* ]]
  [[ "$output" == *"new_code=10"* ]]
}

@test "plan: versionCode sample 1.7.1-rc0 -> 10701000" {
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_code=10701000"* ]]
}

# ----- plan: override ------------------------------------------------------

@test "plan: version-override accepts valid version" {
  run bump --mode=plan --version-override=1.8.0-rc0
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.8.0-rc0"* ]]
  [[ "$output" == *"new_code=10800000"* ]]
}

@test "plan: version-override rejects build=100 (out of 1-2 digit range)" {
  run bump --mode=plan --version-override=1.7.2-rc100
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "plan: version-override rejects a non-rc version" {
  run bump --mode=plan --version-override=1.8.0-beta0
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "plan: version-override rejects leading zero" {
  run bump --mode=plan --version-override=1.07.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"leading zero"* ]]
}

@test "plan: version-override rejects no-op identity" {
  run bump --mode=plan --version-override=1.7.1-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"no-op"* ]]
}

@test "plan: version-override rejects monotonicity break" {
  run bump --mode=plan --version-override=1.7.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"monotonicity"* ]]
}

# ----- plan: bounds --------------------------------------------------------

@test "plan: rejects build overflow past 99" {
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=99/' "$TMP_REPO/version.properties"
  echo "1.7.1-rc99 10701990" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

@test "plan: rejects patch overflow past 99" {
  sed -i 's/^project\.versioning\.patch=.*/project.versioning.patch=99/' "$TMP_REPO/version.properties"
  echo "1.7.99-rc0 10799000" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=patch
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

# ----- plan: expected-current ----------------------------------------------

@test "plan: expected-current matches passes" {
  run bump --mode=plan --bump-kind=build --expected-current=1.7.1-rc0
  [ "$status" -eq 0 ]
}

@test "plan: expected-current mismatch fails" {
  run bump --mode=plan --bump-kind=build --expected-current=1.0.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"expected-current"* ]]
}

# ----- write mode ----------------------------------------------------------

@test "write: rewrites both files correctly (build bump)" {
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.build=1$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "1.7.1-rc1 10701010" ]
}

@test "write: patch bump zeros build in file" {
  run bump --mode=write --bump-kind=patch
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.patch=2$' "$TMP_REPO/version.properties"
  grep -q '^project\.versioning\.build=0$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "1.7.2-rc0 10702000" ]
}

@test "write: preserves comment block" {
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
  grep -q '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties"
  grep -q '^#############################$' "$TMP_REPO/version.properties"
}

@test "write: preserves key order" {
  run bump --mode=write --bump-kind=patch
  [ "$status" -eq 0 ]
  grep -nE '^project\.versioning\.' "$TMP_REPO/version.properties" > "$TMP_REPO/order.txt"
  run cat "$TMP_REPO/order.txt"
  [ "${#lines[@]}" -eq 4 ]
  [[ "${lines[0]}" == *"major="* ]]
  [[ "${lines[1]}" == *"minor="* ]]
  [[ "${lines[2]}" == *"patch="* ]]
  [[ "${lines[3]}" == *"build="* ]]
}

@test "write: post-write check passes" {
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=1.7.1-rc1"* ]]
}

@test "write: header refresh idempotent when already updated" {
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
  [ "$(grep -c '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties")" -eq 1 ]
}

@test "write: header refresh migrates old release.sh header" {
  sed -i 's|^### Updated by tools/release/bump.sh ###$|### Updated by release.sh ###|' "$TMP_REPO/version.properties"
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
  grep -q '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties"
  [ "$(grep -c '### Updated by' "$TMP_REPO/version.properties")" -eq 1 ]
}

@test "write: succeeds without --expected-current" {
  run bump --mode=write --bump-kind=build
  [ "$status" -eq 0 ]
}

@test "write: rejects stale --expected-current" {
  run bump --mode=write --bump-kind=build --expected-current=5.0.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"expected-current"* ]]
}

# ----- argument parsing ----------------------------------------------------

@test "rejects missing --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO"
  [ "$status" -ne 0 ]
}

@test "rejects invalid --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=foo
  [ "$status" -ne 0 ]
}

@test "rejects unknown flag" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=check --frobnicate=yes
  [ "$status" -ne 0 ]
}

@test "rejects --version-type as an unknown argument" {
  run bump --mode=plan --version-type=rc
  [ "$status" -ne 0 ]
  [[ "$output" == *"unknown argument"* ]]
}
