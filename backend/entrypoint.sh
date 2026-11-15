#!/bin/bash
set -e

# Fix permissions for mounted volumes
chown -R appuser:appuser /data /mnt/drive2 2>/dev/null || true

# Create database directory if it doesn't exist
mkdir -p /data

# Ensure database file exists and is writable
touch /data/library.db
chown appuser:appuser /data/library.db

# Execute the main command as appuser
exec gosu appuser "$@"