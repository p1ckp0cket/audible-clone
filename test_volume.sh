#!/bin/bash
set -e
docker run --rm -v audiobook-project_audiobook-data:/data --entrypoint sh audiobook-project-audiobook-api:latest << 'EOF'
ls -la /data/
gosu appuser touch /data/library.db
ls -la /data/
python3 << 'PYEOF'
import asyncio
import aiosqlite

async def test():
    async with aiosqlite.connect("/data/library.db") as db:
        await db.execute("CREATE TABLE IF NOT EXISTS test_async (id INTEGER PRIMARY KEY)")
        await db.execute("INSERT OR IGNORE INTO test_async (id) VALUES (1)")
        await db.commit()
    print("aiosqlite write test: OK")

asyncio.run(test())
PYEOF
EOF