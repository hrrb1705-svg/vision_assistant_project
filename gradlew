#!/usr/bin/env bash
# Self-contained Gradle wrapper bootstrap (no gradle-wrapper.jar required).
set -e

APP_BASE_NAME=$(basename "$0")
DIRNAME=$(cd "$(dirname "$0")" && pwd)

# Read distributionUrl from gradle-wrapper.properties
PROP_FILE="$DIRNAME/gradle/wrapper/gradle-wrapper.properties"
DISTRIBUTION_URL="https://services.gradle.org/distributions/gradle-8.7-bin.zip"
if [ -f "$PROP_FILE" ]; then
    URL_LINE=$(grep -E '^distributionUrl=' "$PROP_FILE" | head -n 1)
    if [ -n "$URL_LINE" ]; then
        DISTRIBUTION_URL="${URL_LINE#distributionUrl=}"
        DISTRIBUTION_URL="${DISTRIBUTION_URL//\\//}"
    fi
fi

DIST_NAME=$(basename "$DISTRIBUTION_URL")
DIST_ZIP_NAME="${DIST_NAME%.zip}"

GRADLE_HOME_CACHE="$HOME/.gradle/wrapper/dists"
EXTRACT_DIR="$GRADLE_HOME_CACHE/$DIST_ZIP_NAME"

# 1) Prefer gradle from PATH
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# 2) Use previously extracted distribution
if [ -x "$EXTRACT_DIR/$DIST_ZIP_NAME/bin/gradle" ]; then
    exec "$EXTRACT_DIR/$DIST_ZIP_NAME/bin/gradle" "$@"
fi

# 3) Download distribution (curl or wget) and extract
echo "Downloading Gradle distribution: $DISTRIBUTION_URL"
mkdir -p "$EXTRACT_DIR"
ZIP_PATH="$EXTRACT_DIR/$DIST_NAME"
if command -v curl >/dev/null 2>&1; then
    curl -fSL --retry 3 -o "$ZIP_PATH" "$DISTRIBUTION_URL"
elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_PATH" "$DISTRIBUTION_URL"
else
    echo "ERROR: Neither 'gradle', 'curl' nor 'wget' is available." >&2
    exit 1
fi
unzip -q -o "$ZIP_PATH" -d "$EXTRACT_DIR"
rm -f "$ZIP_PATH"

if [ ! -x "$EXTRACT_DIR/$DIST_ZIP_NAME/bin/gradle" ]; then
    echo "ERROR: Gradle was downloaded but not found at $EXTRACT_DIR/$DIST_ZIP_NAME/bin/gradle" >&2
    exit 1
fi
exec "$EXTRACT_DIR/$DIST_ZIP_NAME/bin/gradle" "$@"
