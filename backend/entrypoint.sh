#!/bin/bash
set -e

echo "=== Entrypoint: Setting up permissions ==="

# Fix permissions for mounted volumes
chown -R appuser:appuser /data /mnt/drive2 2>/dev/null || true

# Create data directories if they don't exist
mkdir -p /data /mnt/drive2/audiobooks /mnt/drive2/ebooks /mnt/drive2/covers /mnt/drive2/tts_output

# Pre-create database file with proper permissions
touch /data/library.db 2>/dev/null || true
chown appuser:appuser /data/library.db 2>/dev/null || true

echo "=== Entrypoint: Starting application ==="

# Execute the main command as appuser
exec gosu appuser "$@"