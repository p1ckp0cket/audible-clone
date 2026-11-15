#!/bin/bash
# Download high-quality Piper TTS voices for full cast capability
# Run this once during setup

set -e

MODEL_DIR="/home/paperclip/piper/models"
mkdir -p "$MODEL_DIR"

echo "Downloading Piper TTS voices to $MODEL_DIR..."

# Base URL for Piper models
BASE_URL="https://huggingface.co/rhasspy/piper-voices/resolve/main"

# High-quality English voices (multiple speakers for character casting)
declare -A VOICES=(
    # US English - High quality
    ["en_US-lessac-medium"]="en/en_US/lessac/medium/en_US-lessac-medium.onnx"
    ["en_US-lessac-medium.json"]="en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"
    
    ["en_US-amy-medium"]="en/en_US/amy/medium/en_US-amy-medium.onnx"
    ["en_US-amy-medium.json"]="en/en_US/amy/medium/en_US-amy-medium.onnx.json"
    
    ["en_US-ryan-medium"]="en/en_US/ryan/medium/en_US-ryan-medium.onnx"
    ["en_US-ryan-medium.json"]="en/en_US/ryan/medium/en_US-ryan-medium.onnx.json"
    
    ["en_US-kristin-medium"]="en/en_US/kristin/medium/en_US-kristin-medium.onnx"
    ["en_US-kristin-medium.json"]="en/en_US/kristin/medium/en_US-kristin-medium.onnx.json"
    
    ["en_US-joe-medium"]="en/en_US/joe/medium/en_US-joe-medium.onnx"
    ["en_US-joe-medium.json"]="en/en_US/joe/medium/en_US-joe-medium.onnx.json"
    
    # British English
    ["en_GB-northern_english_male-medium"]="en/en_GB/northern_english_male/medium/en_GB-northern_english_male-medium.onnx"
    ["en_GB-northern_english_male-medium.json"]="en/en_GB/northern_english_male/medium/en_GB-northern_english_male-medium.onnx.json"
    
    ["en_GB-southern_english_female-medium"]="en/en_GB/southern_english_female/medium/en_GB-southern_english_female-medium.onnx"
    ["en_GB-southern_english_female-medium.json"]="en/en_GB/southern_english_female/medium/en_GB-southern_english_female-medium.onnx.json"
    
    ["en_GB-alan-medium"]="en/en_GB/alan/medium/en_GB-alan-medium.onnx"
    ["en_GB-alan-medium.json"]="en/en_GB/alan/medium/en_GB-alan-medium.onnx.json"
    
    # Additional diverse voices
    ["en_US-libritts-high"]="en/en_US/libritts/high/en_US-libritts-high.onnx"
    ["en_US-libritts-high.json"]="en/en_US/libritts/high/en_US-libritts-high.onnx.json"
    
    ["en_US-ljspeech-high"]="en/en_US/ljspeech/high/en_US-ljspeech-high.onnx"
    ["en_US-ljspeech-high.json"]="en/en_US/ljspeech/high/en_US-ljspeech-high.onnx.json"
)

# Download function with retry
download_with_retry() {
    local url=$1
    local output=$2
    local max_attempts=3
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        echo "  Downloading $(basename $output) (attempt $attempt/$max_attempts)..."
        if curl -L -f -o "$output" "$url" 2>/dev/null; then
            echo "  ✓ Success"
            return 0
        else
            echo "  ✗ Failed, retrying..."
            attempt=$((attempt + 1))
            sleep 2
        fi
    done
    
    echo "  ✗ Failed after $max_attempts attempts"
    return 1
}

# Download each voice
failed=0
for voice in "${!VOICES[@]}"; do
    path="${VOICES[$voice]}"
    url="$BASE_URL/$path"
    output="$MODEL_DIR/$voice"
    
    if [ -f "$output" ]; then
        echo "  ✓ Already exists: $voice"
    else
        if ! download_with_retry "$url" "$output"; then
            failed=$((failed + 1))
        fi
    fi
done

# Also download Kokoro model (higher quality, newer)
echo ""
echo "Downloading Kokoro v1.0 (high quality multi-speaker)..."
KOKORO_URL="https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx"
KOKORO_OUTPUT="/home/paperclip/kokoro/kokoro-v1.0.onnx"
mkdir -p /home/paperclip/kokoro

if [ -f "$KOKORO_OUTPUT" ]; then
    echo "  ✓ Kokoro already exists"
else
    if download_with_retry "$KOKORO_URL" "$KOKORO_OUTPUT"; then
        echo "  ✓ Kokoro downloaded"
    else
        echo "  ⚠ Kokoro download failed (optional)"
    fi
fi

# Download Kokoro voices config (numpy .bin format)
KOKORO_VOICES_URL="https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin"
KOKORO_VOICES_OUTPUT="/home/paperclip/kokoro/voices-v1.0.bin"
if [ ! -f "$KOKORO_VOICES_OUTPUT" ]; then
    curl -L -f -o "$KOKORO_VOICES_OUTPUT" "$KOKORO_VOICES_URL" 2>/dev/null || echo "  ⚠ Kokoro voices config failed"
fi

echo ""
echo "================================"
if [ $failed -eq 0 ]; then
    echo "✓ All Piper voices downloaded successfully!"
else
    echo "⚠ $failed voices failed to download"
fi
echo ""
echo "Available voices in $MODEL_DIR:"
ls -la "$MODEL_DIR"/*.onnx 2>/dev/null | awk '{print "  " $9 " (" $5 " bytes")}'

echo ""
echo "To use with docker-compose, ensure these volumes are mounted:"
echo "  - /home/paperclip/piper/models:/home/paperclip/piper/models"
echo "  - /home/paperclip/kokoro:/home/paperclip/kokoro"