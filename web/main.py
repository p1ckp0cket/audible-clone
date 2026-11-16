#!/usr/bin/env python3
"""
Production-grade Audible Clone Web Interface
Full clone of the Audible Android app with all features from the APK
"""

from fastapi import FastAPI, Request, HTTPException, Query, UploadFile, File, Form, BackgroundTasks
from fastapi.responses import HTMLResponse, JSONResponse, StreamingResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
import httpx
import os
import json
import asyncio
import uuid
import hashlib
from pathlib import Path
from typing import Optional, List, Dict, Any
from datetime import datetime, timedelta
import sqlite3

# Color constants for templates
DARK_CHARCOAL = "#1A2523"
FOREST_GREEN = "#2E4A43"
DEEP_TEAL = "#155D7A"
STEEL_BLUE = "#5B9BB5"
ICY_BLUE = "#CDE6F0"
BG_PRIMARY = "#1A1A2E"
BG_SECONDARY = "#16213E"
BG_CARD = "#1F2A44"
ACCENT = "#5B9BB5"
ACCENT_HOVER = "#4A8AA3"
TEXT_PRIMARY = "#FFFFFF"
TEXT_SECONDARY = "#A0B8D0"
TEXT_MUTED = "#6B8BA4"
BORDER = "#2A3A5E"
SUCCESS = "#2E4A43"
WARNING = "#D4A537"
ERROR = "#C0392B"

# Config
API_URL = os.getenv("API_URL", "http://localhost:8000")
AUDIOBOOKS_PATH = "/mnt/drive2/audiobooks"
COVERS_PATH = "/mnt/drive2/covers"
EBOOKS_PATH = "/mnt/drive2/ebooks"
TTS_OUTPUT_PATH = "/mnt/drive2/tts_output"
DB_PATH = "/data/audible_web.db"

# FastAPI app
app = FastAPI(title="Audible Clone", version="1.0.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Config
API_URL = os.getenv("API_URL", "http://localhost:8000")
AUDIOBOOKS_PATH = "/mnt/drive2/audiobooks"
COVERS_PATH = "/mnt/drive2/covers"
EBOOKS_PATH = "/mnt/drive2/ebooks"
TTS_OUTPUT_PATH = "/mnt/drive2/tts_output"
DB_PATH = "/data/audible_web.db"

# Static files
app.mount("/covers", StaticFiles(directory=COVERS_PATH), name="covers")
app.mount("/audio", StaticFiles(directory=AUDIOBOOKS_PATH), name="audio")

# Create Jinja2 environment with completely disabled cache
from jinja2 import Environment, FileSystemLoader, select_autoescape

templates_dir = Path(__file__).parent / "templates"
templates_dir.mkdir(exist_ok=True)

# Create Jinja2 environment with completely disabled cache
jinja_env = Environment(
    loader=FileSystemLoader(str(templates_dir)),
    autoescape=select_autoescape(['html', 'xml']),
    auto_reload=False,
    cache_size=0,
    enable_async=False,
)

# Override cache to completely disable it
class NoCache(dict):
    def __getitem__(self, key):
        raise KeyError(key)
    def __setitem__(self, key, value):
        pass
    def __contains__(self, key):
        return False
    def get(self, key, default=None):
        return default
    def __len__(self):
        return 0
    def __iter__(self):
        return iter([])
    def keys(self):
        return iter([])
    def values(self):
        return iter([])
    def items(self):
        return iter([])

# Create Jinja2 environment with completely disabled cache
jinja_env = Environment(
    loader=FileSystemLoader(str(Path(__file__).parent / "templates")),
    autoescape=select_autoescape(['html', 'xml']),
    auto_reload=False,
    cache_size=0,
    enable_async=False,
)

# Override cache
class NoCache(dict):
    def __getitem__(self, key):
        raise KeyError(key)
    def __setitem__(self, key, value):
        pass
    def __contains__(self, key):
        return False
    def get(self, key, default=None):
        return default
    def __len__(self):
        return 0
    def __iter__(self):
        return iter([])
    def keys(self):
        return iter([])
    def values(self):
        return iter([])
    def items(self):
        return iter([])

jinja_env.cache = {}

# FastAPI app
app = FastAPI(title="Audible Clone", version="1.0.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Config
API_URL = os.getenv("API_URL", "http://localhost:8000")
AUDIOBOOKS_PATH = "/mnt/drive2/audiobooks"
COVERS_PATH = "/mnt/drive2/covers"
EBOOKS_PATH = "/mnt/drive2/ebooks"
TTS_OUTPUT_PATH = "/mnt/drive2/tts_output"
DB_PATH = "/data/audible_web.db"

# Static files
app.mount("/covers", StaticFiles(directory=COVERS_PATH), name="covers")
app.mount("/audio", StaticFiles(directory=AUDIOBOOKS_PATH), name="audio")

# Helper functions
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

async def fetch_from_api(endpoint: str):
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.get(f"{API_URL}{endpoint}")
        response.raise_for_status()
        return response.json()

# Color constants for templates
DARK_CHARCOAL = "#1A2523"
FOREST_GREEN = "#2E4A43"
DEEP_TEAL = "#155D7A"
STEEL_BLUE = "#5B9BB5"
ICY_BLUE = "#CDE6F0"
BG_PRIMARY = "#1A1A2E"
BG_SECONDARY = "#16213E"
BG_CARD = "#1F2A44"
ACCENT = "#5B9BB5"
ACCENT_HOVER = "#4A8AA3"
TEXT_PRIMARY = "#FFFFFF"
TEXT_SECONDARY = "#A0B8D0"
TEXT_MUTED = "#6B8BA4"
BORDER = "#2A3A5E"
SUCCESS = "#2E4A43"
WARNING = "#D4A537"
ERROR = "#C0392B"

# Common context for templates
COMMON_CONTEXT = {
    "DARK_CHARCOAL": "#1A2523",
    "FOREST_GREEN": "#2E4A43",
    "DEEP_TEAL": "#155D7A",
    "STEEL_BLUE": "#5B9BB5",
    "ICY_BLUE": "#CDE6F0",
    "BG_PRIMARY": "#1A1A2E",
    "BG_SECONDARY": "#16213E",
    "BG_CARD": "#1F2A44",
    "ACCENT": "#5B9BB5",
    "ACCENT_HOVER": "#4A8AA3",
    "TEXT_PRIMARY": "#FFFFFF",
    "TEXT_SECONDARY": "#A0B8D0",
    "TEXT_MUTED": "#6B8BA4",
    "BORDER": "#2A3A5E",
    "SUCCESS": "#2E4A43",
    "WARNING": "#D4A537",
    "ERROR": "#C0392B",
}

def render_template(template_name: str, extra_context: dict = None) -> HTMLResponse:
    """Render template manually to bypass Starlette's TemplateResponse cache issues."""
    context = COMMON_CONTEXT.copy()
    if extra_context:
        context.update(extra_context)
    template = jinja_env.get_template(template_name)
    html = template.render(context)
    return HTMLResponse(content=html)

# Initialize database
def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asin TEXT UNIQUE,
            title TEXT NOT NULL,
            author TEXT,
            narrator TEXT,
            series TEXT,
            series_number INTEGER,
            description TEXT,
            genres TEXT,
            tags TEXT,
            published_year INTEGER,
            publisher TEXT,
            isbn TEXT,
            language TEXT DEFAULT 'en',
            duration_seconds INTEGER,
            cover_url TEXT,
            cover_path TEXT,
            source TEXT DEFAULT 'local',
            source_id TEXT,
            status TEXT DEFAULT 'available',
            file_path TEXT,
            file_size INTEGER,
            file_format TEXT,
            bitrate INTEGER,
            sample_rate INTEGER,
            chapters_count INTEGER DEFAULT 0,
            rating REAL,
            user_rating REAL,
            progress_seconds INTEGER DEFAULT 0,
            completed BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_played_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS chapters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_index INTEGER NOT NULL,
            title TEXT,
            start_offset_seconds INTEGER DEFAULT 0,
            end_offset_seconds INTEGER,
            duration_seconds INTEGER,
            file_path TEXT,
            FOREIGN KEY (book_id) REFERENCES books (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS highlights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_id INTEGER,
            text TEXT NOT NULL,
            note TEXT,
            color TEXT DEFAULT '#FFD700',
            location_type TEXT,
            location_value REAL,
            location_end REAL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (book_id) REFERENCES books (id),
            FOREIGN KEY (chapter_id) REFERENCES chapters (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS tts_tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ebook_id INTEGER,
            voice TEXT,
            status TEXT DEFAULT 'pending',
            progress INTEGER DEFAULT 0,
            output_path TEXT,
            error TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            completed_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS librivox_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            query TEXT,
            data TEXT,
            cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    
    conn.commit()
    conn.close()

init_db()

# Color constants for templates
DARK_CHARCOAL = "#1A2523"
FOREST_GREEN = "#2E4A43"
DEEP_TEAL = "#155D7A"
STEEL_BLUE = "#5B9BB5"
ICY_BLUE = "#CDE6F0"
BG_PRIMARY = "#1A1A2E"
BG_SECONDARY = "#16213E"
BG_CARD = "#1F2A44"
ACCENT = "#5B9BB5"
ACCENT_HOVER = "#4A8AA3"
TEXT_PRIMARY = "#FFFFFF"
TEXT_SECONDARY = "#A0B8D0"
TEXT_MUTED = "#6B8BA4"
BORDER = "#2A3A5E"
SUCCESS = "#2E4A43"
WARNING = "#D4A537"
ERROR = "#C0392B"

# Common context for templates
COMMON_CONTEXT = {
    "DARK_CHARCOAL": "#1A2523",
    "FOREST_GREEN": "#2E4A43",
    "DEEP_TEAL": "#155D7A",
    "STEEL_BLUE": "#5B9BB5",
    "ICY_BLUE": "#CDE6F0",
    "BG_PRIMARY": "#1A1A2E",
    "BG_SECONDARY": "#16213E",
    "BG_CARD": "#1F2A44",
    "ACCENT": "#5B9BB5",
    "ACCENT_HOVER": "#4A8AA3",
    "TEXT_PRIMARY": "#FFFFFF",
    "TEXT_SECONDARY": "#A0B8D0",
    "TEXT_MUTED": "#6B8BA4",
    "BORDER": "#2A3A5E",
    "SUCCESS": "#2E4A43",
    "WARNING": "#D4A537",
    "ERROR": "#C0392B",
}

def render_template(template_name: str, extra_context: dict = None) -> HTMLResponse:
    """Render template manually to bypass Starlette's TemplateResponse cache issues."""
    context = COMMON_CONTEXT.copy()
    if extra_context:
        context.update(extra_context)
    template = jinja_env.get_template(template_name)
    html = template.render(context)
    return HTMLResponse(content=html)

# Initialize database
def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asin TEXT UNIQUE,
            title TEXT NOT NULL,
            author TEXT,
            narrator TEXT,
            series TEXT,
            series_number INTEGER,
            description TEXT,
            genres TEXT,
            tags TEXT,
            published_year INTEGER,
            publisher TEXT,
            isbn TEXT,
            language TEXT DEFAULT 'en',
            duration_seconds INTEGER,
            cover_url TEXT,
            cover_path TEXT,
            source TEXT DEFAULT 'local',
            source_id TEXT,
            status TEXT DEFAULT 'available',
            file_path TEXT,
            file_size INTEGER,
            file_format TEXT,
            bitrate INTEGER,
            sample_rate INTEGER,
            chapters_count INTEGER DEFAULT 0,
            rating REAL,
            user_rating REAL,
            progress_seconds INTEGER DEFAULT 0,
            completed BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_played_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS chapters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_index INTEGER NOT NULL,
            title TEXT,
            start_offset_seconds INTEGER DEFAULT 0,
            end_offset_seconds INTEGER,
            duration_seconds INTEGER,
            file_path TEXT,
            FOREIGN KEY (book_id) REFERENCES books (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS highlights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_id INTEGER,
            text TEXT NOT NULL,
            note TEXT,
            color TEXT DEFAULT '#FFD700',
            location_type TEXT,
            location_value REAL,
            location_end REAL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (book_id) REFERENCES books (id),
            FOREIGN KEY (chapter_id) REFERENCES chapters (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS tts_tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ebook_id INTEGER,
            voice TEXT,
            status TEXT DEFAULT 'pending',
            progress INTEGER DEFAULT 0,
            output_path TEXT,
            error TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            completed_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS librivox_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            query TEXT,
            data TEXT,
            cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    
    conn.commit()
    conn.close()

init_db()

# Color constants for templates
DARK_CHARCOAL = "#1A2523"
FOREST_GREEN = "#2E4A43"
DEEP_TEAL = "#155D7A"
STEEL_BLUE = "#5B9BB5"
ICY_BLUE = "#CDE6F0"
BG_PRIMARY = "#1A1A2E"
BG_SECONDARY = "#16213E"
BG_CARD = "#1F2A44"
ACCENT = "#5B9BB5"
ACCENT_HOVER = "#4A8AA3"
TEXT_PRIMARY = "#FFFFFF"
TEXT_SECONDARY = "#A0B8D0"
TEXT_MUTED = "#6B8BA4"
BORDER = "#2A3A5E"
SUCCESS = "#2E4A43"
WARNING = "#D4A537"
ERROR = "#C0392B"

# Common context for templates
COMMON_CONTEXT = {
    "DARK_CHARCOAL": "#1A2523",
    "FOREST_GREEN": "#2E4A43",
    "DEEP_TEAL": "#155D7A",
    "STEEL_BLUE": "#5B9BB5",
    "ICY_BLUE": "#CDE6F0",
    "BG_PRIMARY": "#1A1A2E",
    "BG_SECONDARY": "#16213E",
    "BG_CARD": "#1F2A44",
    "ACCENT": "#5B9BB5",
    "ACCENT_HOVER": "#4A8AA3",
    "TEXT_PRIMARY": "#FFFFFF",
    "TEXT_SECONDARY": "#A0B8D0",
    "TEXT_MUTED": "#6B8BA4",
    "BORDER": "#2A3A5E",
    "SUCCESS": "#2E4A43",
    "WARNING": "#D4A537",
    "ERROR": "#C0392B",
}

def render_template(template_name: str, extra_context: dict = None) -> HTMLResponse:
    """Render template manually to bypass Starlette's TemplateResponse cache issues."""
    context = COMMON_CONTEXT.copy()
    if extra_context:
        context.update(extra_context)
    template = jinja_env.get_template(template_name)
    html = template.render(context)
    return HTMLResponse(content=html)

# Initialize database
def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS books (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            asin TEXT UNIQUE,
            title TEXT NOT NULL,
            author TEXT,
            narrator TEXT,
            series TEXT,
            series_number INTEGER,
            description TEXT,
            genres TEXT,
            tags TEXT,
            published_year INTEGER,
            publisher TEXT,
            isbn TEXT,
            language TEXT DEFAULT 'en',
            duration_seconds INTEGER,
            cover_url TEXT,
            cover_path TEXT,
            source TEXT DEFAULT 'local',
            source_id TEXT,
            status TEXT DEFAULT 'available',
            file_path TEXT,
            file_size INTEGER,
            file_format TEXT,
            bitrate INTEGER,
            sample_rate INTEGER,
            chapters_count INTEGER DEFAULT 0,
            rating REAL,
            user_rating REAL,
            progress_seconds INTEGER DEFAULT 0,
            completed BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_played_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS chapters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_index INTEGER NOT_NULL,
            title TEXT,
            start_offset_seconds INTEGER DEFAULT 0,
            end_offset_seconds INTEGER,
            duration_seconds INTEGER,
            file_path TEXT,
            FOREIGN KEY (book_id) REFERENCES books (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS highlights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            chapter_id INTEGER,
            text TEXT NOT NULL,
            note TEXT,
            color TEXT DEFAULT '#FFD700',
            location_type TEXT,
            location_value REAL,
            location_end REAL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (book_id) REFERENCES books (id),
            FOREIGN KEY (chapter_id) REFERENCES chapters (id)
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS tts_tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ebook_id INTEGER,
            voice TEXT,
            status TEXT DEFAULT 'pending',
            progress INTEGER DEFAULT 0,
            output_path TEXT,
            error TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            completed_at TIMESTAMP
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS librivox_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            query TEXT,
            data TEXT,
            cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    
    conn.commit()
    conn.close()

init_db()

# Static files
app.mount("/covers", StaticFiles(directory=COVERS_PATH), name="covers")
app.mount("/audio", StaticFiles(directory=AUDIOBOOKS_PATH), name="audio")

# Helper functions
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

async def fetch_from_api(endpoint: str):
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.get(f"{API_URL}{endpoint}")
        response.raise_for_status()
        return response.json()

# Routes
@app.get("/", response_class=HTMLResponse)
async def home(request: Request):
    template = jinja_env.get_template("home.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/library", response_class=HTMLResponse)
async def library(request: Request):
    template = jinja_env.get_template("library.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/discover", response_class=HTMLResponse)
async def discover(request: Request):
    template = jinja_env.get_template("discover.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/profile", response_class=HTMLResponse)
async def profile(request: Request):
    template = jinja_env.get_template("profile.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/player", response_class=HTMLResponse)
async def player(request: Request):
    template = jinja_env.get_template("player.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/search", response_class=HTMLResponse)
async def search_page(request: Request):
    template = jinja_env.get_template("search.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/settings", response_class=HTMLResponse)
async def settings(request: Request):
    template = jinja_env.get_template("settings.html")
    html = template.render(COMMON_CONTEXT)
    return HTMLResponse(content=html)

@app.get("/book/{book_id}", response_class=HTMLResponse)
async def book_detail(request: Request, book_id: int):
    template = jinja_env.get_template("book_detail.html")
    context = COMMON_CONTEXT.copy()
    context["book_id"] = book_id
    html = jinja_env.get_template("book_detail.html").render(context)
    return HTMLResponse(content=html)

# API Routes
@app.get("/api/books")
async def get_books(
    page: int = 1,
    per_page: int = 50,
    search: Optional[str] = None,
    author: Optional[str] = None,
    genre: Optional[str] = None,
    source: Optional[str] = None,
    status: Optional[str] = None,
    sort: str = "added_at",
    order: str = "desc"
):
    params = {"page": page, "per_page": per_page, "sort": sort, "order": order}
    if search:
        params["search"] = search
    if author:
        params["author"] = author
    if genre:
        params["genre"] = genre
    if source:
        params["source"] = source
    if status:
        params["status"] = status
    
    query_string = "&".join(f"{k}={v}" for k, v in params.items())
    data = await fetch_from_api(f"/api/v1/library?{query_string}")
    return JSONResponse(content=data)

@app.get("/api/books/{book_id}")
async def get_book(book_id: int):
    data = await fetch_from_api(f"/api/v1/library/{book_id}")
    return JSONResponse(content=data)

@app.get("/api/books/{book_id}/stream")
async def stream_book(book_id: int, range: Optional[str] = None):
    async with httpx.AsyncClient() as client:
        headers = {}
        if range:
            headers["Range"] = range
        response = await client.get(f"{API_URL}/api/v1/library/{book_id}/stream", headers=headers)
        return StreamingResponse(
            response.aiter_bytes(),
            status_code=response.status_code,
            headers=dict(response.headers),
            media_type="audio/mpeg"
        )

@app.get("/api/books/{book_id}/chapters")
async def get_chapters(book_id: int):
    data = await fetch_from_api(f"/api/v1/library/{book_id}")
    chapters = data.get("chapters", [])
    return JSONResponse(content={"chapters": chapters})

@app.get("/api/librivox/search")
async def search_librivox(q: str = "", page: int = 1, limit: int = 20):
    data = await fetch_from_api(f"/api/v1/librivox/search?q={q}&page={page}&limit={limit}")
    return JSONResponse(content=data)

@app.get("/api/abb/search")
async def search_abb(q: str = "", page: int = 1, limit: int = 20):
    data = await fetch_from_api(f"/api/v1/audiobookbay/search?q={q}&page={page}&limit={limit}")
    return JSONResponse(content=data)

@app.post("/api/abb/download")
async def download_abb(asin: str = Form(...), title: str = Form(...)):
    data = await fetch_from_api(f"/api/v1/audiobookbay/download?asin={asin}&title={title}")
    return JSONResponse(content=data)

@app.post("/api/upload/ebook")
async def upload_ebook(file: UploadFile = File(...)):
    file_id = str(uuid.uuid4())
    ext = Path(file.filename).suffix
    save_path = Path(EBOOKS_PATH) / f"{file_id}{ext}"
    
    with open(save_path, "wb") as f:
        content = await file.read()
        f.write(content)
    
    # Create TTS task
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO tts_tasks (ebook_id, voice, status) VALUES (?, ?, 'pending')",
        (file_id, "auto")
    )
    conn.commit()
    conn.close()
    
    return JSONResponse(content={"task_id": file_id, "status": "pending"})

@app.post("/api/tts/convert")
async def convert_ebook_to_audiobook(
    ebook_id: str = Form(...),
    voice: str = Form("auto"),
    character_voices: str = Form("{}")
):
    # This would trigger the TTS conversion
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE tts_tasks SET status='processing', voice=? WHERE ebook_id=?",
        (voice, ebook_id)
    )
    conn.commit()
    conn.close()
    
    return JSONResponse(content={"status": "processing"})

@app.get("/api/tts/tasks")
async def get_tts_tasks():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM tts_tasks ORDER BY created_at DESC")
    tasks = [dict(row) for row in cursor.fetchall()]
    conn.close()
    return JSONResponse(content={"tasks": tasks})

@app.get("/api/tts/voices")
async def get_tts_voices():
    voices = {
        "piper": [
            {"id": "en_US-lessac-medium", "name": "Lessac (US)", "lang": "en_US", "quality": "medium"},
            {"id": "en_US-amy-medium", "name": "Amy (US)", "lang": "en_US", "quality": "medium"},
            {"id": "en_US-joe-medium", "name": "Joe (US)", "lang": "en_US", "quality": "medium"},
            {"id": "en_GB-alan-medium", "name": "Alan (UK)", "lang": "en_GB", "quality": "medium"},
            {"id": "en_GB-northern_english_male-medium", "name": "Northern English Male (UK)", "lang": "en_GB", "quality": "medium"},
            {"id": "en_GB-southern_english_female-low", "name": "Southern English Female (UK)", "lang": "en_GB", "quality": "low"},
        ],
        "kokoro": [
            {"id": "af", "name": "af (Female)", "lang": "en", "quality": "high"},
            {"id": "am", "name": "am (Male)", "lang": "en", "quality": "high"},
            {"id": "bf", "name": "bf (Female)", "lang": "en", "quality": "high"},
            {"id": "bm", "name": "bm (Male)", "lang": "en", "quality": "high"},
        ]
    }
    return JSONResponse(content=voices)

@app.get("/api/highlights/{book_id}")
async def get_highlights(book_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT * FROM highlights WHERE book_id = ? ORDER BY location_value",
        (book_id,)
    )
    highlights = [dict(row) for row in cursor.fetchall()]
    conn.close()
    return JSONResponse(content={"highlights": highlights})

@app.post("/api/highlights")
async def create_highlight(
    book_id: int = Form(...),
    chapter_id: Optional[int] = Form(None),
    text: str = Form(...),
    note: str = Form(""),
    color: str = Form("#FFD700"),
    location_type: str = Form("percent"),
    location_value: float = Form(...),
    location_end: Optional[float] = Form(None)
):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO highlights (book_id, chapter_id, text, note, color, location_type, location_value, location_end)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        (book_id, chapter_id, text, note, color, location_type, location_value, location_end)
    )
    conn.commit()
    highlight_id = cursor.lastrowid
    conn.close()
    return JSONResponse(content={"id": highlight_id, "status": "created"})

@app.delete("/api/highlights/{highlight_id}")
async def delete_highlight(highlight_id: int):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM highlights WHERE id = ?", (highlight_id,))
    conn.commit()
    conn.close()
    return JSONResponse(content={"status": "deleted"})

@app.post("/api/library/scan")
async def scan_library(background_tasks: BackgroundTasks, force_refresh: bool = False):
    data = await fetch_from_api(f"/api/v1/library/scan?force_refresh={force_refresh}")
    return JSONResponse(content=data)

@app.get("/api/stats")
async def get_stats():
    data = await fetch_from_api("/api/v1/library/stats")
    return JSONResponse(content=data)

@app.get("/health")
async def health():
    return {"status": "healthy", "service": "audiobook-web"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=3000)