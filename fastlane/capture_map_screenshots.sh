#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"
PACKAGE="eu.darken.apl"
ACTIVITY="$PACKAGE/.main.ui.MainActivity"
SCREENSHOT_NAME="1_map.png"
WIDTH=1080
HEIGHT=2400
DENSITY=428

SERIAL="${ANDROID_SERIAL:-}"
APK=""
LOCALES="en-US"
# tar1090 zoom (MapLibre zoom + 1), the unit the app stores
CAMERA_LAT="40.66"
CAMERA_LON="-74.12"
CAMERA_ZOOM="9.0"
SETTLE_SECONDS=25
FOCUS_TIMEOUT=30

usage() {
    echo "Usage: $0 --apk <path> [--serial <emulator-serial>] [options]"
    echo ""
    echo "Captures the native map store screenshot on an emulator. Clears the app's data."
    echo ""
    echo "Options:"
    echo "  --apk PATH        Debug APK to install (run-as must work)"
    echo "  --serial SERIAL   Emulator serial, defaults to ANDROID_SERIAL; must start with emulator-"
    echo "  --locales LIST    Comma separated fastlane locales (default: $LOCALES)"
    echo "  --lat LAT         Camera latitude (default: $CAMERA_LAT)"
    echo "  --lon LON         Camera longitude (default: $CAMERA_LON)"
    echo "  --zoom ZOOM       Camera zoom, tar1090 scale (default: $CAMERA_ZOOM)"
    echo "  --settle SECONDS  Wait after launch for tiles and aircraft (default: $SETTLE_SECONDS)"
    echo "  --help            Show this help"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk) APK="$2"; shift 2 ;;
        --serial) SERIAL="$2"; shift 2 ;;
        --locales) LOCALES="$2"; shift 2 ;;
        --lat) CAMERA_LAT="$2"; shift 2 ;;
        --lon) CAMERA_LON="$2"; shift 2 ;;
        --zoom) CAMERA_ZOOM="$2"; shift 2 ;;
        --settle) SETTLE_SECONDS="$2"; shift 2 ;;
        --help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [[ -z "$SERIAL" ]]; then
    echo "ERROR: No device given, use --serial or ANDROID_SERIAL"
    exit 1
fi
if [[ "$SERIAL" != emulator-* ]]; then
    echo "ERROR: $SERIAL is not an emulator, refusing to clear app data on it"
    exit 1
fi
if [[ -z "$APK" || ! -f "$APK" ]]; then
    echo "ERROR: APK not found: ${APK:-<none>}"
    exit 1
fi
IFS=',' read -r -a LOCALE_LIST <<< "$LOCALES"
if [[ ${#LOCALE_LIST[@]} -eq 0 ]]; then
    echo "ERROR: No locales given"
    exit 1
fi

dev() {
    adb -s "$SERIAL" "$@" < /dev/null
}

sh_dev() {
    dev shell "$@"
}

mkdir -p "$PROJECT_DIR/.claude/tmp"
WORK_DIR="$(mktemp -d "$PROJECT_DIR/.claude/tmp/capture-map.XXXXXX")"

if [[ "$(sh_dev getprop sys.boot_completed | tr -d '\r')" != "1" ]]; then
    echo "ERROR: $SERIAL is not booted"
    exit 1
fi

# Snapshots of everything this script changes, restored on exit
size_line="$(sh_dev wm size | tr -d '\r' | grep '^Override size:' || true)"
ORIG_SIZE="${size_line#Override size: }"
density_line="$(sh_dev wm density | tr -d '\r' | grep '^Override density:' || true)"
ORIG_DENSITY="${density_line#Override density: }"
night_line="$(sh_dev cmd uimode night | tr -d '\r')"
ORIG_NIGHT="${night_line#Night mode: }"
ORIG_DEMO_ALLOWED="$(sh_dev settings get global sysui_demo_allowed | tr -d '\r')"
if ! demo_state="$(sh_dev dumpsys activity service com.android.systemui/.SystemUIService DemoModeController)"; then
    echo "ERROR: Could not read the SystemUI demo mode state on $SERIAL"
    exit 1
fi
demo_state="$(printf '%s\n' "$demo_state" | tr -d '\r')"
if printf '%s\n' "$demo_state" | grep -Eq '^[[:space:]]*isInDemoMode=true[[:space:]]*$'; then
    echo "ERROR: SystemUI demo mode is already active on $SERIAL, exit it first"
    exit 1
elif ! printf '%s\n' "$demo_state" | grep -Eq '^[[:space:]]*isInDemoMode=false[[:space:]]*$'; then
    echo "ERROR: Could not tell whether SystemUI demo mode is active on $SERIAL"
    exit 1
fi
ORIG_APP_LOCALES=""
APP_LOCALES_SAVED=false
DEVICE_CHANGED=false

restore() {
    local status=$?
    trap - EXIT
    set +e
    if [[ "$DEVICE_CHANGED" == true ]]; then
        echo "Restoring device settings..."
        sh_dev am broadcast -a com.android.systemui.demo -e command exit > /dev/null
        if [[ "$ORIG_DEMO_ALLOWED" == "null" ]]; then
            sh_dev settings delete global sysui_demo_allowed > /dev/null
        else
            sh_dev settings put global sysui_demo_allowed "$ORIG_DEMO_ALLOWED"
        fi
        if [[ -n "$ORIG_SIZE" ]]; then sh_dev wm size "$ORIG_SIZE"; else sh_dev wm size reset; fi
        if [[ -n "$ORIG_DENSITY" ]]; then sh_dev wm density "$ORIG_DENSITY"; else sh_dev wm density reset; fi
        sh_dev cmd uimode night "$ORIG_NIGHT" > /dev/null || echo "WARNING: Could not restore night mode '$ORIG_NIGHT'"
    fi
    if [[ "$APP_LOCALES_SAVED" == true ]]; then
        if [[ -n "$ORIG_APP_LOCALES" ]]; then
            sh_dev cmd locale set-app-locales "$PACKAGE" --locales "$ORIG_APP_LOCALES"
        else
            sh_dev cmd locale set-app-locales "$PACKAGE"
        fi
    fi
    rm -rf "$WORK_DIR"
    exit "$status"
}
trap restore EXIT
trap 'exit 130' INT TERM

echo "Installing $APK on $SERIAL..."
dev install -r "$APK" > /dev/null

if ! sh_dev run-as "$PACKAGE" true; then
    echo "ERROR: run-as $PACKAGE failed, the APK must be a debug build"
    exit 1
fi

locales_line="$(sh_dev cmd locale get-app-locales "$PACKAGE" | tr -d '\r')"
ORIG_APP_LOCALES="${locales_line##*[}"
ORIG_APP_LOCALES="${ORIG_APP_LOCALES%]}"
APP_LOCALES_SAVED=true

echo "Preparing device..."
DEVICE_CHANGED=true
sh_dev wm size "${WIDTH}x${HEIGHT}"
sh_dev wm density "$DENSITY"
sh_dev cmd uimode night no > /dev/null
sh_dev settings put global sysui_demo_allowed 1
demo() {
    sh_dev am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null
}
demo enter
demo clock -e hhmm 1200
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo battery -e level 100 -e plugged false
demo notifications -e visible false

# Preferences DataStore file: PreferenceMap { map<string, Value> preferences = 1 },
# Value { bool boolean = 1; string string = 5 }
write_preferences() {
    python3 -I - "$@" << 'PYTHON_EOF'
import sys

def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)

def field(number, payload):
    return varint(number << 3 | 2) + varint(len(payload)) + payload

path, entries = sys.argv[1], sys.argv[2:]
data = b""
for i in range(0, len(entries), 3):
    key, kind, value = entries[i:i + 3]
    if kind == "bool":
        encoded = varint(1 << 3) + varint(1 if value == "true" else 0)
    elif kind == "string":
        encoded = field(5, value.encode())
    else:
        sys.exit("unknown type " + kind)
    data += field(1, field(1, key.encode()) + field(2, encoded))
with open(path, "wb") as f:
    f.write(data)
PYTHON_EOF
}

write_preferences "$WORK_DIR/settings_core.preferences_pb" \
    core.onboarding.finished bool true
write_preferences "$WORK_DIR/settings_map.preferences_pb" \
    map.last.camera string "{\"lat\":$CAMERA_LAT,\"lon\":$CAMERA_LON,\"zoom\":$CAMERA_ZOOM}"

push_preferences() {
    local name="$1"
    local staged="/data/local/tmp/capture-map-$name"
    local out
    if ! out="$(dev push "$WORK_DIR/$name" "$staged" 2>&1)"; then
        echo "$out"
        echo "ERROR: Could not push $name"
        exit 1
    fi
    sh_dev chmod 644 "$staged"
    sh_dev run-as "$PACKAGE" cp "$staged" "files/datastore/$name"
    sh_dev rm -f "$staged"
}

png_size_ok() {
    python3 -I - "$1" "$WIDTH" "$HEIGHT" << 'PYTHON_EOF'
import struct, sys
with open(sys.argv[1], "rb") as f:
    head = f.read(24)
ok = (len(head) == 24 and head[:8] == b"\x89PNG\r\n\x1a\n" and head[12:16] == b"IHDR"
      and struct.unpack(">II", head[16:24]) == (int(sys.argv[2]), int(sys.argv[3])))
sys.exit(0 if ok else 1)
PYTHON_EOF
}

wait_for_focus() {
    local waited=0
    while (( waited < FOCUS_TIMEOUT )); do
        if sh_dev dumpsys window | tr -d '\r' | grep 'mCurrentFocus=' | grep -q "$PACKAGE/"; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    return 1
}

for locale in "${LOCALE_LIST[@]}"; do
    echo ""
    echo "=== $locale ==="
    sh_dev am force-stop "$PACKAGE"
    sh_dev pm clear "$PACKAGE" > /dev/null
    sh_dev cmd locale set-app-locales "$PACKAGE" --locales "$locale"

    sh_dev run-as "$PACKAGE" mkdir -p files/datastore
    push_preferences settings_core.preferences_pb
    push_preferences settings_map.preferences_pb

    sh_dev am start -W -n "$ACTIVITY" > /dev/null
    if ! wait_for_focus; then
        echo "ERROR: $ACTIVITY did not get focus within ${FOCUS_TIMEOUT}s, keeping the existing screenshot"
        exit 1
    fi
    echo "Waiting ${SETTLE_SECONDS}s for the map to settle..."
    sleep "$SETTLE_SECONDS"
    if ! wait_for_focus; then
        echo "ERROR: $ACTIVITY lost focus while settling, keeping the existing screenshot"
        exit 1
    fi

    capture="$WORK_DIR/$locale.png"
    dev exec-out screencap -p > "$capture"
    if ! png_size_ok "$capture"; then
        echo "ERROR: Capture is not a ${WIDTH}x${HEIGHT} PNG, keeping the existing screenshot"
        exit 1
    fi

    target_dir="$FASTLANE_DIR/$locale/images/phoneScreenshots"
    mkdir -p "$target_dir"
    mv "$capture" "$target_dir/$SCREENSHOT_NAME"
    echo "Saved ${target_dir#"$PROJECT_DIR"/}/$SCREENSHOT_NAME"
done

echo ""
echo "Done!"
