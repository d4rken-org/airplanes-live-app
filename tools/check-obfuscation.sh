#!/usr/bin/env bash
# Verifies that the gplay release is obfuscated, foss is not, and that everything which must survive
# renaming did. Run from the repo root after:
#   ./gradlew :app:assembleGplayRelease :app:assembleFossRelease :app:bundleGplayRelease
# mapping.txt is large, so every access here streams (head/grep/awk); never read it whole.
set -euo pipefail

GPLAY_APK_DIR="app/build/outputs/apk/gplay/release"
GPLAY_BUNDLE_DIR="app/build/outputs/bundle/gplayRelease"
GPLAY_CONFIG="app/build/outputs/mapping/gplayRelease/configuration.txt"
FOSS_CONFIG="app/build/outputs/mapping/fossRelease/configuration.txt"
GPLAY_MAPPING="app/build/outputs/mapping/gplayRelease/mapping.txt"
BUNDLE_MAP_ENTRY="BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"

failures=0

ok() {
    echo "ok: $1"
}

fail() {
    echo "FAIL: $1"
    failures=$((failures + 1))
}

require_file() {
    if [ ! -f "$1" ]; then
        fail "$2 missing: $1"
        return 1
    fi
    return 0
}

# Reads "# pg_map_id: <hex>" from the first lines of a mapping on stdin.
read_map_id() {
    # No early awk exit: under pipefail it would SIGPIPE head and fail the caller.
    head -n 20 | awk '!id && $1 == "#" && $2 == "pg_map_id:" && $3 ~ /^[0-9a-f]+$/ { id = $3 } END { if (id) print id }'
}

gplay_apk() {
    local metadata="$GPLAY_APK_DIR/output-metadata.json"
    if [ ! -f "$metadata" ]; then
        echo "output metadata missing: $metadata" >&2
        return 1
    fi
    python3 -I - "$metadata" <<'PY'
import json, os, sys
path = sys.argv[1]
with open(path) as f:
    elements = json.load(f).get("elements", [])
files = [e.get("outputFile") for e in elements]
if len(files) != 1 or not files[0]:
    sys.stderr.write(f"expected exactly one element with outputFile in {path}, found {len(files)}\n")
    sys.exit(1)
print(os.path.join(os.path.dirname(path), files[0]))
PY
}

gplay_aab() {
    local aabs=()
    local f
    for f in "$GPLAY_BUNDLE_DIR"/*.aab; do
        [ -f "$f" ] && aabs+=("$f")
    done
    if [ "${#aabs[@]}" -ne 1 ]; then
        echo "expected exactly one .aab in $GPLAY_BUNDLE_DIR, found ${#aabs[@]}" >&2
        return 1
    fi
    echo "${aabs[0]}"
}

find_retrace() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/sdk}}"
    local bin="$sdk/cmdline-tools/latest/bin/retrace"
    if [ ! -x "$bin" ]; then
        echo "retrace not found at $bin (set ANDROID_HOME or install the SDK cmdline-tools)" >&2
        return 1
    fi
    echo "$bin"
}

check_dontobfuscate() {
    local count
    if require_file "$GPLAY_CONFIG" "gplayRelease configuration"; then
        count=$(grep -c '^-dontobfuscate' "$GPLAY_CONFIG" || true)
        if [ "$count" -eq 0 ]; then
            ok "gplayRelease has no -dontobfuscate"
        else
            fail "gplayRelease has $count -dontobfuscate rule(s), it must have none"
        fi
    fi

    if require_file "$FOSS_CONFIG" "fossRelease configuration"; then
        count=$(grep -c '^-dontobfuscate' "$FOSS_CONFIG" || true)
        if [ "$count" -ge 1 ]; then
            ok "fossRelease has -dontobfuscate ($count rule(s))"
        else
            fail "fossRelease has no -dontobfuscate, foss must stay unobfuscated"
        fi
    fi
}

check_renamed_ratio() {
    require_file "$GPLAY_MAPPING" "gplayRelease mapping" || return 0

    local counts total renamed
    counts=$(awk -F' -> ' '
        /^eu\.darken\.apl\./ && / -> / && !/R8\$\$REMOVED/ {
            total++
            if ($1 ":" != $2) renamed++
        }
        END { print total+0, renamed+0 }
    ' "$GPLAY_MAPPING")
    total=${counts% *}
    renamed=${counts#* }

    if [ "$total" -gt 0 ] && [ $((renamed * 100)) -ge $((total * 25)) ]; then
        ok "renamed share: $renamed/$total (>= 25%)"
    else
        fail "renamed share: $renamed/$total (expected >= 25%)"
    fi
}

check_identity_mappings() {
    require_file "$GPLAY_MAPPING" "gplayRelease mapping" || return 0

    local kept=(
        'eu.darken.apl.BuildConfig'
        'eu.darken.apl.main.ui.MainActivity'
        'eu.darken.apl.feeder.core.server.FeederMonitorSyncWorker'
        'eu.darken.apl.feeder.core.server.FeederMonitorSyncDeadlineWorker'
        'eu.darken.apl.watch.core.alerts.WatchWorker'
        'eu.darken.apl.watch.core.server.WatchListSyncWorker'
        'eu.darken.apl.watch.core.server.WatchListSyncDeadlineWorker'
        'eu.darken.apl.main.core.db.AircraftRoomDb'
        'eu.darken.apl.main.core.db.AircraftRoomDb_Impl'
        'eu.darken.apl.watch.core.db.WatchRoomDb'
        'eu.darken.apl.watch.core.db.WatchRoomDb_Impl'
        'eu.darken.apl.common.flight.db.FlightRoomDb'
        'eu.darken.apl.common.flight.db.FlightRoomDb_Impl'
    )

    local cls
    for cls in "${kept[@]}"; do
        if grep -qxF "$cls -> $cls:" "$GPLAY_MAPPING"; then
            ok "name kept: $cls"
        else
            fail "name not kept: $cls"
        fi
    done
}

# The SourceFile attribute of an obfuscated class holds "r8-map-id-<pg_map_id>", which ties a stack
# trace to its mapping. The AAB's embedded mapping, mapping.txt and the APK's dex must agree on it.
check_map_id() {
    require_file "$GPLAY_MAPPING" "gplayRelease mapping" || return 0

    local map_id
    map_id=$(read_map_id < "$GPLAY_MAPPING")
    if [ -z "$map_id" ]; then
        fail "no '# pg_map_id: <hex>' header in the first 20 lines of mapping.txt"
        return 0
    fi

    local aab
    if aab=$(gplay_aab); then
        # grep -c, not grep -q: under pipefail an early grep exit SIGPIPEs unzip and fails the pipeline.
        local hits
        hits=$(unzip -l "$aab" "$BUNDLE_MAP_ENTRY" | grep -cF "$BUNDLE_MAP_ENTRY" || true)
        if [ "$hits" -gt 0 ]; then
            ok "bundle carries the mapping: $(basename "$aab")"
            local bundle_map_id
            bundle_map_id=$(unzip -p "$aab" "$BUNDLE_MAP_ENTRY" | read_map_id || true)
            if [ -n "$bundle_map_id" ] && [ "$bundle_map_id" = "$map_id" ]; then
                ok "bundle mapping id matches mapping.txt: $map_id"
            else
                fail "bundle mapping id '${bundle_map_id}' does not match mapping.txt '$map_id'"
            fi
        else
            fail "bundle has no $BUNDLE_MAP_ENTRY: $(basename "$aab")"
        fi
    else
        fail "gplayRelease bundle not usable"
    fi

    local apk
    if ! apk=$(gplay_apk); then
        fail "gplayRelease APK not usable"
        return 0
    fi
    if [ ! -f "$apk" ]; then
        fail "gplayRelease APK listed in output-metadata.json is missing: $apk"
        return 0
    fi

    local dex_files dex total_hits=0 hits
    dex_files=$(unzip -Z1 "$apk" | grep -E '^classes[0-9]*\.dex$' || true)
    if [ -z "$dex_files" ]; then
        fail "no classes*.dex in $(basename "$apk")"
        return 0
    fi
    for dex in $dex_files; do
        hits=$(unzip -p "$apk" "$dex" | grep -caF "r8-map-id-$map_id" || true)
        total_hits=$((total_hits + hits))
    done
    if [ "$total_hits" -gt 0 ]; then
        ok "map id survives in the dex: r8-map-id-$map_id"
    else
        fail "r8-map-id-$map_id not found in any classes*.dex of $(basename "$apk")"
    fi
}

# Picks the first renamed app class that has a line-ranged member whose obfuscated name is unique in
# that class and which is not part of an inline frame group. Prints "origClass obfClass method obfMethod line".
pick_retrace_candidate() {
    awk '
        function flush(   i, key) {
            if (cls == "" || found) return
            for (i = 1; i <= n; i++) {
                key = order[i]
                if (cnt[key] == 1 && key in cand && !(key in bad)) {
                    print cls, obf, cand[key], key, line[key]
                    found = 1
                    return
                }
            }
        }
        /^[^ #]/ {
            flush()
            if (found) exit
            delete cnt; delete cand; delete line; delete order; delete bad
            cls = ""; n = 0; last = ""
            if ($0 ~ /^eu\.darken\.apl\./ && $0 !~ /R8\$\$REMOVED/ && $2 == "->") {
                o = $3; sub(/:$/, "", o)
                if (o != $1 && $1 !~ /\$/) { cls = $1; obf = o }
            }
            next
        }
        cls != "" && /^    [0-9]+:[0-9]+:/ && $NF != "" && $(NF-1) == "->" {
            m = $NF
            last = m
            cnt[m]++
            split($1, r, ":")
            # Member line: "    a:b:ret name(args)[:c:d] -> obf". Plain names only: no inlined
            # "Other.name", no synthetic or lambda helpers.
            sig = $2
            name = sig; sub(/\(.*/, "", name)
            if (r[1] + 0 > 0 && r[2] + 0 >= r[1] + 0 && name ~ /^[a-zA-Z][a-zA-Z0-9]*$/ && name != m) {
                if (!(m in cand)) order[++n] = m
                cand[m] = name
                line[m] = r[1]
            }
            next
        }
        # Outlined or frame-rewritten members do not retrace to a single plain frame.
        cls != "" && /^ +# / && /outline|rewriteFrame/ { if (last != "") bad[last] = 1; next }
        cls != "" && /^    / && $(NF-1) == "->" { cnt[$NF]++; last = "" }
        END { if (!found) flush() }
    ' "$GPLAY_MAPPING"
}

check_line_ranges() {
    require_file "$GPLAY_MAPPING" "gplayRelease mapping" || return 0

    local hits
    hits=$(awk '
        /^[^ #]/ { app = ($0 ~ /^eu\.darken\.apl\./); next }
        app && /^    [0-9]+:[0-9]+:/ { n++ }
        END { print n+0 }
    ' "$GPLAY_MAPPING")
    if [ "$hits" -gt 0 ]; then
        ok "line-ranged member mappings in app classes: $hits"
    else
        fail "no line-ranged member mappings under eu.darken.apl. classes"
    fi
}

check_retrace() {
    require_file "$GPLAY_MAPPING" "gplayRelease mapping" || return 0

    local retrace
    if ! retrace=$(find_retrace); then
        fail "retrace tool not available"
        return 0
    fi

    local map_id
    map_id=$(read_map_id < "$GPLAY_MAPPING")

    local candidate
    candidate=$(pick_retrace_candidate)
    if [ -z "$candidate" ]; then
        fail "no renamed app class with a plain line-ranged method to retrace"
        return 0
    fi

    local orig_cls obf_cls method obf_method line
    read -r orig_cls obf_cls method obf_method line <<< "$candidate"

    local frame output
    frame=$(printf 'java.lang.IllegalStateException: check-obfuscation\n\tat %s.%s(r8-map-id-%s:%s)\n' \
        "$obf_cls" "$obf_method" "$map_id" "$line")
    output=$(printf '%s\n' "$frame" | "$retrace" "$GPLAY_MAPPING" 2>&1 || true)

    if [[ "$output" == *"$orig_cls.$method("* ]]; then
        ok "retrace: $obf_cls.$obf_method:$line -> $orig_cls.$method"
    else
        fail "retrace of $obf_cls.$obf_method:$line did not yield $orig_cls.$method, got: $output"
    fi
}

check_dontobfuscate
check_renamed_ratio
check_identity_mappings
check_map_id
check_line_ranges
check_retrace

if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
fi

echo "all checks passed"
