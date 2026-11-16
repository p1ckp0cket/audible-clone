#!/usr/bin/env python3
"""
Simple web interface for Audiobook Library
Run this on your paperclip server to get a web version
"""

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.middleware.cors import CORSMiddleware
import httpx
import os
import json

app = FastAPI(title="Audible Clone Web")

# Allow CORS from your network
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Get API URL from environment or use default
API_URL = os.getenv("API_URL", "http://localhost:8000")

# Templates directory
TEMPLATES_DIR = os.path.join(os.path.dirname(__file__), "templates")
os.makedirs(TEMPLATES_DIR, exist_ok=True)

# Create templates
HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Audible Clone - Web</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #1a1a1a;
            color: #fff;
            min-height: 100vh;
        }
        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
        header {
            background: linear-gradient(135deg, #6200ee, #3700b3);
            padding: 20px;
            text-align: center;
            border-radius: 10px;
            margin-bottom: 20px;
        }
        h1 { font-size: 2rem; margin-bottom: 5px; }
        .subtitle { opacity: 0.9; font-size: 0.9rem; }
        .books { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
        .book-card {
            background: #2a2a2a;
            border-radius: 10px;
            overflow: hidden;
            transition: transform 0.2s;
        }
        .book-card:hover { transform: translateY(-2px); }
        .book-cover {
            width: 100%;
            height: 200px;
            object-fit: cover;
            background: #3a3a3a;
        }
        .book-info { padding: 15px; }
        .book-title { font-size: 1.1rem; font-weight: 500; margin-bottom: 8px; }
        .book-author { color: #aaa; font-size: 0.9rem; margin-bottom: 8px; }
        .book-meta { color: #888; font-size: 0.8rem; }
        .search-bar {
            background: #2a2a2a;
            border-radius: 8px;
            padding: 12px 16px;
            margin-bottom: 20px;
            display: flex;
            gap: 10px;
        }
        .search-bar input {
            flex: 1;
            background: #1a1a1a;
            border: 1px solid #444;
            border-radius: 5px;
            padding: 8px 12px;
            color: #fff;
        }
        .search-bar input::placeholder { color: #666; }
        .search-btn {
            background: #6200ee;
            border: none;
            border-radius: 5px;
            padding: 8px 16px;
            color: #fff;
            cursor: pointer;
        }
        .loading { text-align: center; padding: 40px; color: #888; }
        .error { text-align: center; padding: 40px; color: #ff6b6b; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>📚 Audiobook Library</h1>
            <div class="subtitle">Your personal audiobook collection</div>
        </header>
        
        <div class="search-bar">
            <input type="text" id="searchInput" placeholder="Search books...">
            <button class="search-btn" onclick="searchBooks()">Search</button>
        </div>
        
        <div id="booksContainer" class="books">
            <div class="loading">Loading books...</div>
        </div>
    </div>

    <script>
        async function loadBooks(search = '') {
            const container = document.getElementById('booksContainer');
            
            try {
                const response = await fetch('/api/books');
                if (!response.ok) throw new Error('Failed to load books');
                
                const data = await response.json();
                const books = data.books || [];
                
                if (books.length === 0) {
                    container.innerHTML = '<div class="error">No books found</div>';
                    return;
                }
                
                container.innerHTML = books.map(book => `
                    <div class="book-card">
                        <img src="/covers/${book.cover_path?.split('/').pop() || 'default.jpg'}" 
                             class="book-cover" 
                             onerror="this.src='https://via.placeholder.com/280x200/3a3a3a/ffffff?text=No+Cover'"
                             alt="${book.title}">
                        <div class="book-info">
                            <div class="book-title">${book.title || 'Unknown'}</div>
                            <div class="book-author">${book.author || ''}</div>
                            <div class="book-meta">${formatDuration(book.duration_seconds)}</div>
                        </div>
                    </div>
                `).join('');
            } catch (error) {
                container.innerHTML = '<div class="error">Error loading books: ' + error.message + '</div>';
            }
        }
        
        function formatDuration(seconds) {
            if (!seconds) return '';
            const mins = Math.floor(seconds / 60);
            const secs = seconds % 60;
            return `${mins}:${secs.toString().padStart(2, '0')}`;
        }
        
        function searchBooks() {
            const query = document.getElementById('searchInput').value;
            loadBooks(query);
        }
        
        // Load books on page load
        loadBooks();
    </script>
</body>
</html>
"""

@app.on_event("startup")
async def create_template():
    """Create the HTML template at startup"""
    template_path = os.path.join(TEMPLATES_DIR, "index.html")
    with open(template_path, "w") as f:
        f.write(HTML_TEMPLATE)

@app.get("/", response_class=HTMLResponse)
async def read_root(request: Request):
    """Serve the main HTML page"""
    template_path = os.path.join(TEMPLATES_DIR, "index.html")
    with open(template_path, "r") as f:
        return HTMLResponse(content=f.read())

@app.get("/api/books")
async def get_books():
    """Proxy to backend API for books"""
    async with httpx.AsyncClient() as client:
        response = await client.get(f"{API_URL}/api/v1/library")
        return JSONResponse(content=response.json())

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy", "service": "audiobook-web"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=3000)