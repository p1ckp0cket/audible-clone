#!/bin/bash
# Script to build APK - run from project root

set -e

echo "=== Building APK ==="

# Navigate to android directory
cd "$(dirname "$0")/../android"

# Check if gradlew exists
if [ ! -f "gradlew" ]; then
    echo "ERROR: gradlew not found!"
    echo "Make sure you're in the correct directory"
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

# Build Debug APK
echo "Building Debug APK..."
./gradlew assembleDebug

echo "=== Build Complete ==="
echo "APK location:"
echo "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
ls -la "$(pwd)/app/build/outputs/apk/debug/"

# Also build Release APK
echo ""
echo "Building Release APK..."
./gradlew assembleRelease

echo "=== Release APK location ==="
echo "$(pwd)/app/build/outputs/apk/release/app-release-unsigned.apk"
ls -la "$(pwd)/app/build/outputs/apk/release/"