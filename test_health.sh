#!/bin/bash
set -e

echo "Testing health endpoint..."
cd /app
python3 -c "
import asyncio
import httpx
from app.main import app
from httpx import ASGITransport

async def test():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=ASGITransport(app=app), base_url='http://test') as ac:
        resp = await ac.get('/health')
        print(f'Status: {resp.status_code}')
        print(f'Response: {resp.json()}')

asyncio.run(test())
"