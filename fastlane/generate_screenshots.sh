#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

LOCALES_FILE="$PROJECT_DIR/app/src/screenshotTest/kotlin/eu/darken/apl/screenshots/PlayStoreLocales.kt"
REFERENCE_DIR="$PROJECT_DIR/app/src/screenshotTestFossDebug/reference"
GRADLE="$PROJECT_DIR/gradlew"
GRADLE_TASK="updateFossDebugScreenshotTest"
BATCH_SIZE=6

usage() {
    echo "Usage: $0 [--smoke] [--batch-size N] [--clean]"
    echo ""
    echo "Options:"
    echo "  --smoke        Use smoke locales only (2 locales for fast iteration)"
    echo "  --batch-size N Number of locales per Gradle invocation (default: $BATCH_SIZE)"
    echo "  --clean        Remove existing reference images before generating"
    echo "  --help         Show this help"
    exit 0
}

SMOKE=false
CLEAN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --smoke) SMOKE=true; shift ;;
        --batch-size) BATCH_SIZE="$2"; shift 2 ;;
        --clean) CLEAN=true; shift ;;
        --help) usage ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

if [[ "$CLEAN" == true && -d "$REFERENCE_DIR" ]]; then
    echo "Cleaning reference directory..."
    rm -rf "$REFERENCE_DIR"
fi

# Determine which annotation class to extract locales from
if [[ "$SMOKE" == true ]]; then
    echo "Using smoke locales (PlayStoreLocalesSmoke)..."
    TARGET_CLASS="PlayStoreLocalesSmoke"
else
    echo "Using all locales (PlayStoreLocales)..."
    TARGET_CLASS="PlayStoreLocales"
fi

# Parse @Preview lines for the target annotation class.
LOCALE_LINES=()
BUFFER=()
while IFS= read -r line; do
    trimmed="${line#"${line%%[![:space:]]*}"}"

    if [[ "$trimmed" =~ ^@Preview\( ]]; then
        BUFFER+=("$trimmed")
    elif [[ "$trimmed" == "annotation class ${TARGET_CLASS}" ]]; then
        LOCALE_LINES=("${BUFFER[@]}")
        break
    elif [[ "$trimmed" =~ ^annotation\ class ]]; then
        BUFFER=()
    fi
done < "$LOCALES_FILE"

NUM_LOCALES=${#LOCALE_LINES[@]}
echo "Found $NUM_LOCALES locales to process (batch size: $BATCH_SIZE)"

if [[ "$NUM_LOCALES" -eq 0 ]]; then
    echo "ERROR: No locales found for annotation class $TARGET_CLASS"
    exit 1
fi

# Backup original file
cp "$LOCALES_FILE" "${LOCALES_FILE}.bak"

restore_original() {
    if [[ -f "${LOCALES_FILE}.bak" ]]; then
        echo "Restoring original PlayStoreLocales.kt..."
        mv "${LOCALES_FILE}.bak" "$LOCALES_FILE"
    fi
}
trap restore_original EXIT

# Process in batches
TOTAL_BATCHES=$(( (NUM_LOCALES + BATCH_SIZE - 1) / BATCH_SIZE ))
BATCH_NUM=0

for ((i = 0; i < NUM_LOCALES; i += BATCH_SIZE)); do
    BATCH_NUM=$((BATCH_NUM + 1))
    END=$((i + BATCH_SIZE))
    if [[ "$END" -gt "$NUM_LOCALES" ]]; then
        END=$NUM_LOCALES
    fi

    BATCH_LOCALES=("${LOCALE_LINES[@]:i:BATCH_SIZE}")

    # Build @Preview lines for batch
    PREVIEWS=""
    for preview_line in "${BATCH_LOCALES[@]}"; do
        PREVIEWS+="${preview_line}"$'\n'
    done

    echo ""
    echo "=== Batch $BATCH_NUM/$TOTAL_BATCHES (locales $((i+1))-$END of $NUM_LOCALES) ==="

    # Write temporary PlayStoreLocales.kt with only the batch locales
    cat > "$LOCALES_FILE" << KOTLIN_EOF
package eu.darken.apl.screenshots

import androidx.compose.ui.tooling.preview.Preview

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
${PREVIEWS}annotation class PlayStoreLocales

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
${PREVIEWS}annotation class PlayStoreLocalesSmoke
KOTLIN_EOF

    # Run Gradle screenshot generation
    echo "Running $GRADLE_TASK..."
    (cd "$PROJECT_DIR" && "$GRADLE" "$GRADLE_TASK" --no-configuration-cache -q) || {
        echo "WARNING: Gradle task failed for batch $BATCH_NUM, continuing..."
    }
done

echo ""
echo "Screenshot generation complete!"
echo "Reference images: $REFERENCE_DIR"

if [[ -d "$REFERENCE_DIR" ]]; then
    TOTAL_IMAGES=$(find "$REFERENCE_DIR" -name "*.png" | wc -l)
    echo "Total images generated: $TOTAL_IMAGES"
fi
