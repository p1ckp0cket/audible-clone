#!/usr/bin/env python3
import asyncio
import httpx
from app.main import app
from httpx import ASGITransport

async def test():
    transport = ASGITransport(app=__import__("app.main", fromlist=["app"]).app)
    async with httpx.AsyncClient(transport=ASGITransport(app=__import__("app.main", fromlist=["app"]).app), base_url="http://test") as ac:
        resp = await ac.get("/health")
        print(resp.status_code, resp.json())

asyncio.run(test())