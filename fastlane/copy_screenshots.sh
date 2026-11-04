#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

REFERENCE_DIR="${SCREENSHOT_REFERENCE_DIR:-$PROJECT_DIR/app/src/screenshotTestFossDebug/reference/eu/darken/apl/screenshots/PlayStoreScreenshotsKt}"
FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"

usage() {
    echo "Usage: $0 [--clean] [--dry-run]"
    echo ""
    echo "Options:"
    echo "  --clean    Remove the generated screenshots from every phoneScreenshots dir before copying"
    echo "  --dry-run  Show what would be copied without copying"
    echo "  --help     Show this help"
    echo ""
    echo "SCREENSHOT_REFERENCE_DIR overrides the directory the renders are read from."
    exit 0
}

CLEAN=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean) CLEAN=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --help) usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

# Map composable function names to fastlane screenshot filenames.
# 1_map.png is a device capture, not a render, so it is not listed here.
declare -A SCREEN_MAP=(
    [Search]="2_search"
    [Watch]="3_watch"
    [Feeders]="4_feeders"
)
SCREENS=(Search Watch Feeders)

if [[ ! -d "$REFERENCE_DIR" ]]; then
    echo "ERROR: Reference directory not found: $REFERENCE_DIR"
    echo "Run generate_screenshots.sh first."
    exit 1
fi

# Filename format: {FunctionName}_{PreviewName}_{hash}_{index}.png
# e.g. Search_en-US_9970be89_0.png
declare -A RENDER_COUNT=()
declare -A RENDER_FILE=()
declare -A LOCALES=()
SKIPPED=0

for png_file in "$REFERENCE_DIR"/*.png; do
    [[ -f "$png_file" ]] || continue

    filename=$(basename "$png_file" .png)

    SCREEN_NAME=""
    REMAINDER=""
    for func_name in "${SCREENS[@]}"; do
        if [[ "$filename" == "${func_name}_"* ]]; then
            SCREEN_NAME="$func_name"
            REMAINDER="${filename#"${func_name}"_}"
            break
        fi
    done

    if [[ -z "$SCREEN_NAME" || -z "$REMAINDER" ]]; then
        echo "SKIP: Cannot parse filename: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # Strip _{index}, then _{hash}
    temp="${REMAINDER%_*}"
    LOCALE_NAME="${temp%_*}"

    if [[ -z "$LOCALE_NAME" || "$LOCALE_NAME" == "$temp" ]]; then
        echo "SKIP: Cannot extract locale from: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    LOCALES["$LOCALE_NAME"]=1
    key="$SCREEN_NAME|$LOCALE_NAME"
    RENDER_COUNT["$key"]=$(( ${RENDER_COUNT["$key"]:-0} + 1 ))
    RENDER_FILE["$key"]="$png_file"
done

# Validate the whole set before any destination is touched
if [[ ${#LOCALES[@]} -eq 0 ]]; then
    echo "ERROR: No renders found in $REFERENCE_DIR"
    echo "Nothing was changed."
    exit 1
fi

mapfile -t SORTED_LOCALES < <(printf '%s\n' "${!LOCALES[@]}" | sort)

PROBLEMS=0
for locale in "${SORTED_LOCALES[@]}"; do
    for screen in "${SCREENS[@]}"; do
        count=${RENDER_COUNT["$screen|$locale"]:-0}
        if [[ "$count" -ne 1 ]]; then
            echo "ERROR: $locale has $count render(s) of $screen, expected exactly 1"
            PROBLEMS=$((PROBLEMS + 1))
        fi
    done
done

if [[ "$PROBLEMS" -gt 0 ]]; then
    echo "Nothing was changed."
    exit 1
fi

if [[ "$CLEAN" == true ]]; then
    echo "Cleaning generated screenshots..."
    while IFS= read -r -d '' shots_dir; do
        for screen in "${SCREENS[@]}"; do
            target="$shots_dir/${SCREEN_MAP[$screen]}.png"
            [[ -f "$target" ]] || continue
            if [[ "$DRY_RUN" == true ]]; then
                echo "REMOVE: ${target#"$FASTLANE_DIR"/}"
            else
                rm -f "$target"
            fi
        done
    done < <(find "$FASTLANE_DIR" -path "*/images/phoneScreenshots" -type d -print0)
fi

COPIED=0

for locale in "${SORTED_LOCALES[@]}"; do
    if [[ ! -d "$FASTLANE_DIR/$locale" ]]; then
        echo "SKIP: No fastlane directory for locale: $locale"
        SKIPPED=$((SKIPPED + ${#SCREENS[@]}))
        continue
    fi

    TARGET_DIR="$FASTLANE_DIR/$locale/images/phoneScreenshots"

    for screen in "${SCREENS[@]}"; do
        png_file="${RENDER_FILE["$screen|$locale"]}"
        SCREENSHOT_NAME="${SCREEN_MAP[$screen]}"

        if [[ "$DRY_RUN" == true ]]; then
            echo "COPY: $(basename "$png_file") -> $locale/images/phoneScreenshots/$SCREENSHOT_NAME.png"
        else
            mkdir -p "$TARGET_DIR"
            cp "$png_file" "$TARGET_DIR/$SCREENSHOT_NAME.png"
        fi
        COPIED=$((COPIED + 1))
    done
done

echo ""
echo "Done! Copied: $COPIED, Skipped: $SKIPPED"

if [[ "$DRY_RUN" == true ]]; then
    echo "(dry run - no files were actually copied)"
fi
