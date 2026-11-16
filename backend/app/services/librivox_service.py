import httpx
import asyncio
import os
import shutil
from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime
from urllib.parse import urljoin

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.database import Book, Chapter, DownloadTask, BookStatus, BookSource
from app.core.config import get_settings
from app.services.metadata_fetcher import MetadataFetcher


class LibriVoxService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.settings = get_settings()
        self.metadata_fetcher = MetadataFetcher()
        self.client = httpx.AsyncClient(timeout=60.0, follow_redirects=True)
    
    async def close(self):
        await self.client.aclose()
        await self.metadata_fetcher.close()
    
    async def search(self, query: str, limit: int = 20, offset: int = 0, genre: str = None) -> Dict[str, Any]:
        """Search LibriVox for audiobooks."""
        try:
            params = {
                'q': query,
                'limit': min(limit, 500),
                'offset': offset,
                'format': 'json',
                'extended': 1,
                'coverart': 1,
            }
            if genre:
                params['genre'] = genre
            
            resp = await self.client.get(self.settings.LIBRIVOX_API_BASE, params=params)
            if resp.status_code == 200:
                return resp.json()
            return {"books": [], "total": 0}
        except Exception as e:
            print(f"LibriVox search error: {e}")
            return {"books": [], "total": 0}
    
    async def get_book(self, book_id: int) -> Dict[str, Any]:
        """Get detailed info for a specific LibriVox book."""
        try:
            params = {
                'id': book_id,
                'format': 'json',
                'extended': 1,
                'coverart': 1,
            }
            resp = await self.client.get(self.settings.LIBRIVOX_API_BASE, params=params)
            if resp.status_code == 200:
                return resp.json()
            return {}
        except Exception as e:
            print(f"LibriVox get error: {e}")
            return {}
    
    async def get_genres(self) -> List[Dict[str, Any]]:
        """Get available genres from LibriVox."""
        try:
            # LibriVox doesn't have a genres endpoint, but we can get from search
            # Common genres: Fiction, Non-fiction, Poetry, Drama, Children, etc.
            return [
                {"id": "fiction", "name": "Fiction"},
                {"id": "nonfiction", "name": "Non-Fiction"},
                {"id": "poetry", "name": "Poetry"},
                {"id": "drama", "name": "Drama"},
                {"id": "children", "name": "Children"},
                {"id": "history", "name": "History"},
                {"id": "philosophy", "name": "Philosophy"},
                {"id": "religion", "name": "Religion"},
                {"id": "science", "name": "Science"},
                {"id": "adventure", "name": "Adventure"},
                {"id": "mystery", "name": "Mystery"},
                {"id": "romance", "name": "Romance"},
            ]
        except Exception as e:
            print(f"LibriVox genres error: {e}")
            return []
    
    async def download_book(self, book_id: int, download_task_id: int) -> bool:
        """Download a LibriVox book to the server."""
        task = await self._get_download_task(download_task_id)
        if not task:
            return False
        
        try:
            # Get book details
            book_data = await self.get_book(book_id)
            books = book_data.get('books', [])
            if not books:
                await self._update_task(task, "failed", error="Book not found")
                return False
            
            lv_book = books[0]
            await self._update_task(task, "downloading", progress=0.1, current_step="Fetching book details")
            
            # Create book directory
            safe_title = self._sanitize_filename(lv_book.get('title', f'book_{book_id}'))
            safe_author = self._sanitize_filename(lv_book.get('author', 'Unknown'))
            book_dir_name = f"{safe_title} - {safe_author}"
            book_dir = Path(self.settings.AUDIOBOOKS_PATH) / book_dir_name
            book_dir.mkdir(parents=True, exist_ok=True)
            
            # Download cover
            cover_url = lv_book.get('url_cover_art') or lv_book.get('cover_art_url')
            cover_path = None
            if cover_url:
                cover_path = await self._download_file(cover_url, book_dir / "cover.jpg")
                await self._update_task(task, "downloading", progress=0.2, current_step="Downloaded cover")
            
            # Download audio files (sections)
            sections = lv_book.get('sections', [])
            if not sections:
                # Try to get sections from extended data
                await self._update_task(task, "downloading", progress=0.3, current_step="Fetching sections")
                # Sections might be in a separate call
            
            # For LibriVox, sections contain the download URLs
            # We'll download each section as a chapter
            chapters = []
            total_sections = len(sections)
            
            for i, section in enumerate(sections):
                await self._update_task(
                    task, "downloading", 
                    progress=0.3 + (0.6 * i / max(total_sections, 1)),
                    current_step=f"Downloading chapter {i+1}/{total_sections}"
                )
                
                section_url = section.get('url') or section.get('download_url') or section.get('listen_url')
                if not section_url:
                    continue
                
                # Determine extension from URL or default to mp3
                ext = '.mp3'
                if '.m4b' in section_url:
                    ext = '.m4b'
                elif '.ogg' in section_url:
                    ext = '.ogg'
                
                chapter_filename = f"Chapter {i+1:03d} - {self._sanitize_filename(section.get('title', f'Chapter {i+1}'))}{ext}"
                chapter_path = book_dir / chapter_filename
                
                success = await self._download_file(section_url, chapter_path)
                if success:
                    chapters.append({
                        'index': i,
                        'title': section.get('title', f'Chapter {i+1}'),
                        'file_path': str(chapter_path),
                        'duration_seconds': section.get('duration', 0),
                        'start_offset_seconds': sum(c.get('duration_seconds', 0) for c in chapters),
                    })
            
            # Create book entry in database
            await self._update_task(task, "downloading", progress=0.95, current_step="Creating library entry")
            
            book = await self._create_book_from_librivox(lv_book, book_dir, chapters, cover_path)
            
            # Update task
            task.book_id = book.id
            task.status = "completed"
            task.progress = 1.0
            task.completed_at = datetime.utcnow()
            task.download_path = str(book_dir)
            
            await self.db.commit()
            return True
            
        except Exception as e:
            await self._update_task(task, "failed", error=str(e))
            return False
    
    async def _create_book_from_librivox(self, lv_book: Dict, book_dir: Path, 
                                         chapters: List[Dict], cover_path: Optional[Path]) -> Book:
        """Create a Book entry from LibriVox data."""
        book_hash = f"lv_{lv_book.get('id', 'unknown')}"
        
        # Check if already exists
        existing = await self.db.execute(
            select(Book).where(Book.source_id == book_hash, Book.source == BookSource.LIBRIVOX)
        )
        existing_book = existing.scalar_one_or_none()
        if existing_book:
            return existing_book
        
        # Fetch enriched metadata
        enriched = await self.metadata_fetcher.fetch_metadata(
            lv_book.get('title', ''), 
            lv_book.get('author', '')
        )
        
        total_duration = sum(c.get('duration_seconds', 0) for c in chapters)
        
        book = Book(
            title=lv_book.get('title', 'Unknown'),
            author=lv_book.get('author', 'Unknown'),
            narrator=', '.join(set(a.get('name', '') for a in lv_book.get('readers', []))),
            description=lv_book.get('description', ''),
            genres=lv_book.get('genres', []),
            tags=lv_book.get('tags', []),
            published_year=lv_book.get('year'),
            language=lv_book.get('language', 'en'),
            duration_seconds=int(total_duration) if total_duration else None,
            cover_path=str(cover_path) if cover_path else None,
            cover_url=lv_book.get('url_cover_art'),
            source=BookSource.LIBRIVOX,
            source_id=book_hash,
            source_url=lv_book.get('url_librivox'),
            status=BookStatus.AVAILABLE,
            file_path=str(book_dir),
            file_size=sum(Path(c['file_path']).stat().st_size for c in chapters if Path(c['file_path']).exists()),
            file_format='mp3',
            chapters_count=len(chapters),
            added_at=datetime.utcnow(),
        )
        
        self.db.add(book)
        await self.db.flush()
        
        # Create chapters
        for ch_data in chapters:
            chapter = Chapter(
                book_id=book.id,
                index=ch_data['index'],
                title=ch_data['title'],
                start_offset_seconds=ch_data['start_offset_seconds'],
                end_offset_seconds=ch_data['start_offset_seconds'] + ch_data.get('duration_seconds', 0),
                duration_seconds=ch_data.get('duration_seconds'),
                file_path=ch_data['file_path'],
            )
            self.db.add(chapter)
        
        return book
    
    async def _download_file(self, url: str, dest: Path) -> bool:
        """Download a file with progress tracking."""
        try:
            async with self.client.stream('GET', url) as resp:
                if resp.status_code != 200:
                    return False
                
                dest.parent.mkdir(parents=True, exist_ok=True)
                
                async with aiofiles.open(dest, 'wb') as f:
                    async for chunk in resp.aiter_bytes(chunk_size=8192):
                        await f.write(chunk)
            
            return dest.exists() and dest.stat().st_size > 0
        except Exception as e:
            print(f"Download error for {url}: {e}")
            return False
    
    def _sanitize_filename(self, name: str) -> str:
        """Sanitize filename for filesystem."""
        return "".join(c for c in name if c.isalnum() or c in (' ', '-', '_', '.', '(', ')')).rstrip()
    
    async def _get_download_task(self, task_id: int):
        from sqlalchemy import select
        result = await self.db.execute(select(DownloadTask).where(DownloadTask.id == task_id))
        return result.scalar_one_or_none()
    
    async def _update_task(self, task: DownloadTask, status: str, progress: float = None, 
                          current_step: str = None, error: str = None):
        task.status = status
        if progress is not None:
            task.progress = progress
        if current_step:
            task.current_step = current_step
        if error:
            task.error_message = error
        if status == "downloading" and not task.started_at:
            task.started_at = datetime.utcnow()
        await self.db.commit()


import aiofiles