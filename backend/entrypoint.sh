#!/bin/bash
set -e

echo "=== Entrypoint: Setting up permissions ==="

# Fix permissions for mounted volumes - use chown on the mount points
chown -R appuser:appuser /data /mnt/drive2 2>/dev/null || true

# Create database directory if it doesn't exist
mkdir -p /data

# Ensure database file exists and is writable by appuser
touch /data/library.db
chown appuser:appuser /data/library.db

# Verify we can write to the database
echo "=== Entrypoint: Testing database write ==="
gosu appuser python3 -c "
import sqlite3
conn = sqlite3.connect('/data/library.db')
conn.execute('CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)')
conn.execute('INSERT INTO test (id) VALUES (1)')
conn.commit()
conn.close()
print('Database write test: OK')
" 2>&1

echo "=== Entrypoint: Starting application ==="

# Execute the main command as appuser
exec gosu appuser "$@"