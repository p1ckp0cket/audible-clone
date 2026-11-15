from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from typing import List, Optional
from datetime import datetime
import os
import shutil
from pathlib import Path
import aiofiles
import hashlib

from app.models.database import Book, BookSource, BookStatus
from app.models import get_db
from app.core.config import get_settings
from app.services.metadata_fetcher import MetadataFetcher
from app.services.library_scanner import LibraryScanner

router = APIRouter()


@router.post("/upload/audiobook")
async def upload_audiobook(
    files: List[UploadFile] = File(...),
    cover: Optional[UploadFile] = File(None),
    title: Optional[str] = Form(None),
    author: Optional[str] = Form(None),
    narrator: Optional[str] = Form(None),
    series: Optional[str] = Form(None),
    series_number: Optional[float] = Form(None),
    description: Optional[str] = Form(None),
    genres: Optional[str] = Form(None),  # JSON array string
    tags: Optional[str] = Form(None),    # JSON array string
    published_year: Optional[int] = Form(None),
    publisher: Optional[str] = Form(None),
    isbn: Optional[str] = Form(None),
    asin: Optional[str] = Form(None),
    language: str = Form("en"),
    db: AsyncSession = Depends(get_db)
):
    """Upload audiobook files directly."""
    settings = get_settings()
    
    if not files:
        raise HTTPException(status_code=400, detail="No files provided")
    
    # Validate file types
    allowed_extensions = {'.mp3', '.m4b', '.m4a', '.flac', '.ogg', '.wav', '.aac', '.opus'}
    for file in files:
        ext = Path(file.filename).suffix.lower()
        if ext not in allowed_extensions:
            raise HTTPException(
                status_code=400, 
                detail=f"Unsupported file type: {ext}. Allowed: {', '.join(allowed_extensions)}"
            )
    
    # Create book directory
    book_title = title or Path(files[0].filename).stem
    safe_title = "".join(c for c in book_title if c.isalnum() or c in (' ', '-', '_', '.', '(', ')')).rstrip()
    safe_author = "".join(c for c in (author or 'Unknown') if c.isalnum() or c in (' ', '-', '_', '.', '(', ')')).rstrip()
    book_dir_name = f"{safe_title} - {safe_author}"
    book_dir = Path(settings.AUDIOBOOKS_PATH) / book_dir_name
    book_dir.mkdir(parents=True, exist_ok=True)
    
    # Save audio files
    saved_files = []
    total_size = 0
    
    for i, file in enumerate(files):
        ext = Path(file.filename).suffix.lower()
        filename = f"{i+1:03d}_{safe_title}{ext}"
        file_path = book_dir / filename
        
        async with aiofiles.open(file_path, 'wb') as f:
            content = await file.read()
            await f.write(content)
        
        saved_files.append(str(file_path))
        total_size += file_path.stat().st_size
    
    # Save cover if provided
    cover_path = None
    if cover:
        cover_ext = Path(cover.filename).suffix.lower()
        cover_filename = f"cover{cover_ext}"
        cover_path = book_dir / cover_filename
        
        async with aiofiles.open(cover_path, 'wb') as f:
            content = await cover.read()
            await f.write(content)
    
    # Generate book hash
    book_hash = hashlib.md5(str(book_dir).encode()).hexdigest()[:16]
    
    # Create book entry
    book = Book(
        title=title or book_title,
        author=author or 'Unknown',
        narrator=narrator,
        series=series,
        series_number=series_number,
        description=description,
        genres=eval(genres) if genres else [],
        tags=eval(tags) if tags else [],
        published_year=published_year,
        publisher=publisher,
        isbn=isbn,
        asin=asin,
        language=language,
        cover_path=str(cover_path) if cover_path else None,
        source=BookSource.UPLOADED,
        source_id=book_hash,
        status=BookStatus.AVAILABLE,
        file_path=str(book_dir),
        file_size=total_size,
        file_format=', '.join(sorted(set(Path(f).suffix.lower().lstrip('.') for f in saved_files))),
        chapters_count=len(saved_files),
        added_at=datetime.utcnow(),
    )
    
    db.add(book)
    await db.flush()
    
    # Create chapters (one per file for now)
    for i, file_path in enumerate(saved_files):
        chapter = Chapter(
            book_id=book.id,
            index=i,
            title=f"Chapter {i+1}",
            start_offset_seconds=0,  # Would need to calculate
            end_offset_seconds=0,
            file_path=file_path,
        )
        db.add(chapter)
    
    await db.commit()
    
    return {"message": "Audiobook uploaded successfully", "book_id": book.id}


@router.post("/upload/cover/{book_id}")
async def upload_cover(
    book_id: int,
    cover: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    """Upload cover art for a book."""
    settings = get_settings()
    
    result = await db.execute(select(Book).where(Book.id == book_id))
    book = result.scalar_one_or_none()
    
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    
    # Save cover
    covers_dir = Path(settings.COVERS_PATH)
    covers_dir.mkdir(parents=True, exist_ok=True)
    
    ext = Path(cover.filename).suffix.lower()
    cover_filename = f"{book.source_id or book.id}{ext}"
    cover_path = covers_dir / cover_filename
    
    async with aiofiles.open(cover_path, 'wb') as f:
        content = await cover.read()
        await f.write(content)
    
    book.cover_path = str(cover_path)
    book.updated_at = datetime.utcnow()
    await db.commit()
    
    return {"message": "Cover uploaded", "cover_path": str(cover_path)}


from app.models.database import Chapter