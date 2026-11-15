from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import os
from pathlib import Path

from app.core.config import get_settings
from app.models import init_db, close_db
from app.api import library, librivox, audiobookbay, tts, upload, highlights, ota


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await init_db()
    
    # Create necessary directories
    settings = get_settings()
    for path in [
        settings.AUDIOBOOKS_PATH,
        settings.EBOOKS_UPLOAD_PATH,
        settings.COVERS_PATH,
        settings.TTS_OUTPUT_PATH,
    ]:
        Path(path).mkdir(parents=True, exist_ok=True)
    
    # Start background file watcher
    # (would be implemented here)
    
    yield
    
    # Shutdown
    await close_db()


settings = get_settings()

app = FastAPI(
    title=settings.APP_NAME,
    description="Audible Clone API - Personal audiobook server with LibriVox, AudioBookBay, and TTS",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files for covers
covers_dir = Path(settings.COVERS_PATH)
covers_dir.mkdir(parents=True, exist_ok=True)
app.mount("/covers", StaticFiles(directory=str(covers_dir)), name="covers")

# Include routers
app.include_router(library.router, prefix=settings.API_V1_PREFIX, tags=["Library"])
app.include_router(librivox.router, prefix=settings.API_V1_PREFIX, tags=["LibriVox"])
app.include_router(audiobookbay.router, prefix=settings.API_V1_PREFIX, tags=["AudioBookBay"])
app.include_router(tts.router, prefix=settings.API_V1_PREFIX, tags=["TTS"])
app.include_router(upload.router, prefix=settings.API_V1_PREFIX, tags=["Upload"])
app.include_router(highlights.router, prefix=settings.API_V1_PREFIX, tags=["Highlights"])
app.include_router(ota.router, prefix=settings.API_V1_PREFIX, tags=["OTA"])


@app.get("/")
async def root():
    return {
        "name": settings.APP_NAME,
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/health",
    }


@app.get("/health")
async def health_check():
    return {"status": "healthy"}


@app.get("/api/v1/version")
async def get_version():
    return {"version": "1.0.0", "api_version": "v1"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
    )