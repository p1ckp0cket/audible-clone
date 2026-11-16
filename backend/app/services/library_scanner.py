import os
import asyncio
from pathlib import Path
from typing import List, Optional, Dict, Any
from datetime import datetime
import hashlib
import json

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
from mutagen import File as MutagenFile
from tinytag import TinyTag

from app.models.database import Book, Chapter, BookStatus, BookSource
from app.core.config import get_settings
from app.services.metadata_fetcher import MetadataFetcher


class LibraryScanner:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.settings = get_settings()
        self.metadata_fetcher = MetadataFetcher()
        self.supported_extensions = {'.mp3', '.m4b', '.m4a', '.flac', '.ogg', '.wav', '.aac', '.opus'}
    
    async def scan_library(self, force_refresh: bool = False) -> Dict[str, Any]:
        """Scan the audiobooks directory for new or changed books."""
        stats = {
            "scanned": 0,
            "added": 0,
            "updated": 0,
            "errors": 0,
            "skipped": 0,
        }
        
        audiobooks_path = Path(self.settings.AUDIOBOOKS_PATH)
        if not audiobooks_path.exists():
            return {"error": f"Audiobooks path does not exist: {audiobooks_path}", **stats}
        
        # Get all directories in audiobooks path (each book is a directory)
        book_dirs = [d for d in audiobooks_path.iterdir() if d.is_dir()]
        
        for book_dir in book_dirs:
            try:
                result = await self._process_book_directory(book_dir, force_refresh)
                if result == "added":
                    stats["added"] += 1
                elif result == "updated":
                    stats["updated"] += 1
                elif result == "skipped":
                    stats["skipped"] += 1
                stats["scanned"] += 1
            except Exception as e:
                stats["errors"] += 1
                print(f"Error processing {book_dir}: {e}")
                # Rollback the session to recover from the error
                await self.db.rollback()
        
        await self.db.commit()
        return stats
    
    async def _process_book_directory(self, book_dir: Path, force_refresh: bool) -> str:
        """Process a single book directory."""
        # Look for audio files and metadata files
        audio_files = []
        cover_file = None
        cue_file = None
        nfo_file = None
        
        for f in book_dir.rglob('*'):
            if f.is_file():
                ext = f.suffix.lower()
                if ext in self.supported_extensions:
                    audio_files.append(f)
                elif ext in {'.jpg', '.jpeg', '.png', '.webp'} and 'cover' in f.name.lower():
                    cover_file = f
                elif ext == '.cue':
                    cue_file = f
                elif ext == '.nfo':
                    nfo_file = f
        
        if not audio_files:
            return "skipped"
        
        # Generate a unique ID for this book based on path
        book_hash = hashlib.md5(str(book_dir).encode()).hexdigest()[:16]
        
        # Check if book already exists
        existing_book = await self._get_book_by_source_id(book_hash)
        
        if existing_book and not force_refresh:
            # Check if files have changed
            if await self._files_unchanged(existing_book, audio_files):
                return "skipped"
            return await self._update_book(existing_book, book_dir, audio_files, cover_file, cue_file, nfo_file)
        
        return await self._create_book(book_dir, book_hash, audio_files, cover_file, cue_file, nfo_file)
    
    async def _get_book_by_source_id(self, source_id: str) -> Optional[Book]:
        result = await self.db.execute(
            select(Book).where(Book.source_id == source_id, Book.source == BookSource.LOCAL)
        )
        return result.scalar_one_or_none()
    
    async def _files_unchanged(self, book: Book, audio_files: List[Path]) -> bool:
        """Check if the audio files have changed since last scan."""
        if not book.file_path:
            return False
        
        # Quick check: compare file count and total size
        current_size = sum(f.stat().st_size for f in audio_files)
        if book.file_size != current_size:
            return False
        
        # Could do more thorough check with mtime or hash
        return True
    
    async def _create_book(self, book_dir: Path, book_hash: str, audio_files: List[Path], 
                          cover_file: Optional[Path], cue_file: Optional[Path], 
                          nfo_file: Optional[Path]) -> str:
        """Create a new book entry."""
        # Parse metadata from audio files
        metadata = await self._extract_metadata(audio_files, cue_file, nfo_file)
        
        # Try to fetch additional metadata from online sources
        if metadata.get('title') and metadata.get('author'):
            enriched = await self.metadata_fetcher.fetch_metadata(
                metadata['title'], metadata['author']
            )
            metadata.update(enriched)
        
        # Calculate total duration
        total_duration = sum(f.get('duration', 0) for f in metadata.get('files', []))
        
        # Determine file format
        formats = set(f.suffix.lower().lstrip('.') for f in audio_files)
        file_format = ', '.join(sorted(formats))
        
        # Copy cover to covers directory if needed
        cover_path = None
        if cover_file:
            cover_path = await self._save_cover(cover_file, book_hash)
        
        book = Book(
            title=metadata.get('title', book_dir.name),
            author=metadata.get('author', 'Unknown'),
            narrator=metadata.get('narrator'),
            series=metadata.get('series'),
            series_number=metadata.get('series_number'),
            description=metadata.get('description'),
            genres=metadata.get('genres'),
            tags=metadata.get('tags'),
            published_year=metadata.get('published_year'),
            publisher=metadata.get('publisher'),
            isbn=metadata.get('isbn'),
            asin=metadata.get('asin'),
            language=metadata.get('language', 'en'),
            duration_seconds=int(total_duration) if total_duration else None,
            cover_path=cover_path,
            source=BookSource.LOCAL,
            source_id=book_hash,
            status=BookStatus.AVAILABLE,
            file_path=str(book_dir),
            file_size=sum(f.stat().st_size for f in audio_files),
            file_format=file_format,
            bitrate=metadata.get('bitrate'),
            sample_rate=metadata.get('sample_rate'),
            chapters_count=len(metadata.get('chapters', [])),
            added_at=datetime.utcnow(),
        )
        
        self.db.add(book)
        await self.db.flush()
        
        # Create chapters
        await self._create_chapters(book, metadata.get('chapters', []), audio_files)
        
        return "added"
    
    async def _update_book(self, book: Book, book_dir: Path, audio_files: List[Path],
                          cover_file: Optional[Path], cue_file: Optional[Path],
                          nfo_file: Optional[Path]) -> str:
        """Update an existing book entry."""
        metadata = await self._extract_metadata(audio_files, cue_file, nfo_file)
        
        # Update fields
        book.title = metadata.get('title', book.title)
        book.author = metadata.get('author', book.author)
        book.narrator = metadata.get('narrator', book.narrator)
        book.description = metadata.get('description', book.description)
        book.duration_seconds = int(sum(f.get('duration', 0) for f in metadata.get('files', [])))
        book.file_size = sum(f.stat().st_size for f in audio_files)
        book.chapters_count = len(metadata.get('chapters', []))
        book.updated_at = datetime.utcnow()
        
        if cover_file:
            cover_path = await self._save_cover(cover_file, book.source_id)
            book.cover_path = cover_path
        
        # Update chapters (delete and recreate for simplicity)
        await self._create_chapters(book, metadata.get('chapters', []), audio_files)
        
        return "updated"
    
    async def _extract_metadata(self, audio_files: List[Path], 
                               cue_file: Optional[Path], 
                               nfo_file: Optional[Path]) -> Dict[str, Any]:
        """Extract metadata from audio files and cue/nfo files."""
        metadata = {
            'title': None,
            'author': None,
            'narrator': None,
            'series': None,
            'series_number': None,
            'genres': [],
            'tags': [],
            'published_year': None,
            'publisher': None,
            'isbn': None,
            'asin': None,
            'language': 'en',
            'bitrate': None,
            'sample_rate': None,
            'files': [],
            'chapters': [],
        }
        
        # Parse CUE file for chapters
        if cue_file and cue_file.exists():
            chapters = await self._parse_cue_file(cue_file)
            metadata['chapters'] = chapters
        
        # Parse NFO file
        if nfo_file and nfo_file.exists():
            nfo_data = await self._parse_nfo_file(nfo_file)
            metadata.update(nfo_data)
        
        # Extract from audio files
        total_duration = 0
        bitrates = []
        sample_rates = []
        
        for audio_file in sorted(audio_files):
            try:
                tag = TinyTag.get(str(audio_file))
                file_info = {
                    'path': str(audio_file),
                    'title': tag.title,
                    'artist': tag.artist,
                    'album': tag.album,
                    'duration': tag.duration or 0,
                    'bitrate': tag.bitrate,
                    'sample_rate': tag.samplerate,
                    'track': tag.track,
                    'year': tag.year,
                    'genre': tag.genre,
                }
                metadata['files'].append(file_info)
                total_duration += tag.duration or 0
                
                if tag.bitrate:
                    bitrates.append(tag.bitrate)
                if tag.samplerate:
                    sample_rates.append(tag.samplerate)
                
                # Use first file's metadata as book metadata if not set
                if not metadata['title'] and tag.title:
                    metadata['title'] = tag.title
                if not metadata['author'] and tag.artist:
                    metadata['author'] = tag.artist
                if not metadata['narrator'] and tag.composer:
                    metadata['narrator'] = tag.composer
                if not metadata['genres'] and tag.genre:
                    metadata['genres'] = [tag.genre]
                if not metadata['published_year'] and tag.year:
                    metadata['published_year'] = int(tag.year)
                    
            except Exception as e:
                print(f"Error reading {audio_file}: {e}")
        
        metadata['duration'] = total_duration
        metadata['bitrate'] = int(sum(bitrates) / len(bitrates)) if bitrates else None
        metadata['sample_rate'] = int(sum(sample_rates) / len(sample_rates)) if sample_rates else None
        
        # Generate chapters from files if no CUE
        if not metadata['chapters'] and metadata['files']:
            metadata['chapters'] = [
                {
                    'index': i,
                    'title': f.get('title', f'Chapter {i+1}'),
                    'start_offset_seconds': sum(f['duration'] for f in metadata['files'][:i]),
                    'end_offset_seconds': sum(f['duration'] for f in metadata['files'][:i+1]),
                    'duration_seconds': f['duration'],
                    'file_path': f['path'],
                }
                for i, f in enumerate(metadata['files'])
            ]
        
        return metadata
    
    async def _parse_cue_file(self, cue_file: Path) -> List[Dict[str, Any]]:
        """Parse CUE file for chapter information."""
        chapters = []
        try:
            content = cue_file.read_text(encoding='utf-8', errors='ignore')
            lines = content.split('\n')
            current_chapter = {}
            index = 0
            
            for line in lines:
                line = line.strip()
                if line.startswith('TRACK'):
                    if current_chapter:
                        chapters.append(current_chapter)
                    current_chapter = {'index': index}
                    index += 1
                elif line.startswith('TITLE'):
                    title = line.split('"')[1] if '"' in line else line.split()[-1]
                    current_chapter['title'] = title
                elif line.startswith('INDEX 01'):
                    # Parse timestamp MM:SS:FF
                    time_str = line.split()[-1]
                    parts = time_str.split(':')
                    if len(parts) == 3:
                        minutes, seconds, frames = map(int, parts)
                        total_seconds = minutes * 60 + seconds + frames / 75
                        current_chapter['start_offset_seconds'] = total_seconds
            
            if current_chapter:
                chapters.append(current_chapter)
            
            # Calculate end offsets and durations
            for i, ch in enumerate(chapters):
                if i + 1 < len(chapters):
                    ch['end_offset_seconds'] = chapters[i + 1]['start_offset_seconds']
                    ch['duration_seconds'] = ch['end_offset_seconds'] - ch['start_offset_seconds']
                    
        except Exception as e:
            print(f"Error parsing CUE file {cue_file}: {e}")
        
        return chapters
    
    async def _parse_nfo_file(self, nfo_file: Path) -> Dict[str, Any]:
        """Parse NFO file for metadata."""
        metadata = {}
        try:
            content = nfo_file.read_text(encoding='utf-8', errors='ignore')
            # Simple key: value parsing
            for line in content.split('\n'):
                if ':' in line:
                    key, value = line.split(':', 1)
                    key = key.strip().lower()
                    value = value.strip()
                    if key in ['title', 'author', 'narrator', 'series', 'publisher', 'isbn', 'asin']:
                        metadata[key] = value
                    elif key == 'year':
                        try:
                            metadata['published_year'] = int(value)
                        except:
                            pass
        except Exception as e:
            print(f"Error parsing NFO file {nfo_file}: {e}")
        return metadata
    
    async def _create_chapters(self, book: Book, chapters_data: List[Dict], audio_files: List[Path]):
        """Create chapter entries for a book."""
        # Delete existing chapters
        from sqlalchemy import delete
        await self.db.execute(delete(Chapter).where(Chapter.book_id == book.id))
        
        for ch_data in chapters_data:
            chapter = Chapter(
                book_id=book.id,
                index=ch_data.get('index', 0),
                title=ch_data.get('title'),
                start_offset_seconds=ch_data.get('start_offset_seconds', 0),
                end_offset_seconds=ch_data.get('end_offset_seconds'),
                duration_seconds=ch_data.get('duration_seconds'),
                file_path=ch_data.get('file_path'),
            )
            self.db.add(chapter)
    
    async def _save_cover(self, cover_file: Path, book_hash: str) -> str:
        """Save cover image to covers directory."""
        covers_dir = Path(self.settings.COVERS_PATH)
        covers_dir.mkdir(parents=True, exist_ok=True)
        
        ext = cover_file.suffix.lower()
        dest = covers_dir / f"{book_hash}{ext}"
        
        # Copy file
        import shutil
        shutil.copy2(cover_file, dest)
        
        return str(dest)