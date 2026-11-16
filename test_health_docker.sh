#!/bin/bash
set -e

echo "Testing health endpoint..."
docker run --rm --entrypoint sh audiobook-project-audiobook-api:latest -c '
python3 << "EOF"
import asyncio
import httpx
from app.main import app
from httpx import ASGITransport

async def test():
    transport = ASGITransport(app=__import__("app.main", fromlist=["app"]).app)
    async with httpx.AsyncClient(transport=ASGITransport(app=__import__("app.main", fromlist=["app"]).app), base_url="http://test") as ac:
        resp = await ac.get("/health")
        print(f"Status: {resp.status_code}")
        print(f"Response: {resp.json()}")

asyncio.run(test())
EOF
'