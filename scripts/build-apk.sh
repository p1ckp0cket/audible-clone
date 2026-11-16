#!/bin/bash
# Build Android APK on paperclip server

set -e

# Configuration
ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
ANDROID_API_LEVEL=${ANDROID_API_LEVEL:-34}
ANDROID_BUILD_TOOLS=${ANDROID_BUILD_TOOLS:-34.0.0}
GRADLE_VERSION=8.5

echo "=== Installing Android SDK for APK building ==="

# Create Android SDK directory on drive2 (has more space)
mkdir -p /mnt/drive2/android-sdk/cmdline-tools

# Download Android command line tools
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip -d /tmp/cmdline-tools-extract
cp -r /tmp/cmdline-tools-extract/cmdline-tools/* /mnt/drive2/android-sdk/cmdline-tools/
mkdir -p /mnt/drive2/android-sdk/cmdline-tools/latest
mv /mnt/drive2/android-sdk/cmdline-tools/* /mnt/drive2/android-sdk/cmdline-tools/latest/ 2>/dev/null || mv /mnt/drive2/android-sdk/cmdline-tools/cmdline-tools /mnt/drive2/android-sdk/cmdline-tools/latest 2>/dev/null || true

# Set environment variables
export ANDROID_HOME=/mnt/drive2/android-sdk
export ANDROID_SDK_ROOT=/mnt/drive2/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept Android licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

# Install Android SDK packages
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-$ANDROID_API_LEVEL" \
    "build-tools;$ANDROID_BUILD_TOOLS" \
    "platform-tools" \
    "ndk;26.1.10909125"

echo "=== Building APK ==="

# Build the APK
cd /home/paperclip/audiobook-project/android
chmod +x ./gradlew

# Build Debug APK
./gradlew assembleDebug

echo "=== APK Location ==="
echo "Debug APK: android/app/build/outputs/apk/debug/app-debug.apk"
ls -la android/app/build/outputs/apk/debug/

echo "=== Done ==="