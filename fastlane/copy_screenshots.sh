#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

REFERENCE_DIR="$PROJECT_DIR/app/src/screenshotTestFossDebug/reference/eu/darken/apl/screenshots/PlayStoreScreenshotsKt"
FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"

usage() {
    echo "Usage: $0 [--clean] [--dry-run]"
    echo ""
    echo "Options:"
    echo "  --clean    Remove existing phoneScreenshots before copying"
    echo "  --dry-run  Show what would be copied without copying"
    echo "  --help     Show this help"
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

# Map composable function names to fastlane screenshot filenames
declare -A SCREEN_MAP=(
    [Map]="1_map"
    [Search]="2_search"
    [Watch]="3_watch"
    [Feeders]="4_feeders"
)

if [[ ! -d "$REFERENCE_DIR" ]]; then
    echo "ERROR: Reference directory not found: $REFERENCE_DIR"
    echo "Run generate_screenshots.sh first."
    exit 1
fi

# Clean all phoneScreenshots dirs upfront if requested
if [[ "$CLEAN" == true && "$DRY_RUN" == false ]]; then
    echo "Cleaning existing phoneScreenshots..."
    find "$FASTLANE_DIR" -path "*/images/phoneScreenshots" -type d -exec rm -rf {} + 2>/dev/null || true
fi

COPIED=0
SKIPPED=0

# Filename format: {FunctionName}_{PreviewName}_{hash}_{index}.png
for png_file in "$REFERENCE_DIR"/*.png; do
    [[ -f "$png_file" ]] || continue

    filename=$(basename "$png_file" .png)

    # Find which screen this belongs to
    SCREEN_NAME=""
    REMAINDER=""
    for func_name in "${!SCREEN_MAP[@]}"; do
        if [[ "$filename" == "${func_name}_"* ]]; then
            SCREEN_NAME="$func_name"
            REMAINDER="${filename#${func_name}_}"
            break
        fi
    done

    if [[ -z "$SCREEN_NAME" || -z "$REMAINDER" ]]; then
        echo "SKIP: Cannot parse filename: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # REMAINDER is e.g. "en-US_9970be89_0" or "de-DE_36d07f31_0"
    # Strip _{index} (last _N)
    temp="${REMAINDER%_*}"
    # Strip _{hash} (last _XXXXXXXX)
    LOCALE_NAME="${temp%_*}"

    if [[ -z "$LOCALE_NAME" ]]; then
        echo "SKIP: Cannot extract locale from: $filename.png"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    SCREENSHOT_NAME="${SCREEN_MAP[$SCREEN_NAME]}"
    TARGET_DIR="$FASTLANE_DIR/$LOCALE_NAME/images/phoneScreenshots"

    # Check if the fastlane locale directory exists
    if [[ ! -d "$FASTLANE_DIR/$LOCALE_NAME" ]]; then
        echo "SKIP: No fastlane directory for locale: $LOCALE_NAME"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    if [[ "$DRY_RUN" == true ]]; then
        echo "COPY: $filename.png -> $LOCALE_NAME/images/phoneScreenshots/$SCREENSHOT_NAME.png"
    else
        mkdir -p "$TARGET_DIR"
        cp "$png_file" "$TARGET_DIR/$SCREENSHOT_NAME.png"
    fi
    COPIED=$((COPIED + 1))
done

echo ""
echo "Done! Copied: $COPIED, Skipped: $SKIPPED"

if [[ "$DRY_RUN" == true ]]; then
    echo "(dry run - no files were actually copied)"
fi
