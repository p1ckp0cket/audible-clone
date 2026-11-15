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

# Verify we can write to the database with basic sqlite3
echo "=== Entrypoint: Testing database write (sqlite3) ==="
gosu appuser python3 -c "
import sqlite3
conn = sqlite3.connect('/data/library.db')
conn.execute('CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)')
conn.execute('INSERT OR IGNORE INTO test (id) VALUES (1)')
conn.commit()
conn.close()
print('sqlite3 write test: OK')
" 2>&1

# Verify we can write to the database with aiosqlite (what the app uses)
echo "=== Entrypoint: Testing database write (aiosqlite) ==="
gosu appuser python3 -c "
import asyncio
import aiosqlite

async def test():
    async with aiosqlite.connect('/data/library.db') as db:
        await db.execute('CREATE TABLE IF NOT EXISTS test_async (id INTEGER PRIMARY KEY)')
        await db.execute('INSERT OR IGNORE INTO test_async (id) VALUES (1)')
        await db.commit()
    print('aiosqlite write test: OK')

asyncio.run(test())
" 2>&1

echo "=== Entrypoint: Starting application ==="

# Execute the main command as appuser
exec gosu appuser "$@"