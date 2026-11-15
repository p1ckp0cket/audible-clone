from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from typing import List, Optional
from datetime import datetime
import os
import shutil
from pathlib import Path

from app.models.database import Book, Chapter, Highlight, DownloadTask, TTSTask, BookStatus, BookSource
from app.models import get_db
from app.services.library_scanner import LibraryScanner
from app.services.librivox_service import LibriVoxService
from app.services.audiobookbay_service import AudioBookBayService
from app.services.tts_service import TTSService
from app.core.config import get_settings

router = APIRouter()


# ============================================================
# Library Endpoints
# ============================================================

@router.get("/library")
async def list_books(
    page: int = Query(1, ge=1),
    per_page: int = Query(50, ge=1, le=200),
    search: Optional[str] = None,
    author: Optional[str] = None,
    genre: Optional[str] = None,
    source: Optional[BookSource] = None,
    status: Optional[BookStatus] = None,
    sort: str = Query("added_at", regex="^(title|author|added_at|last_played_at|duration_seconds|rating)$"),
    order: str = Query("desc", regex="^(asc|desc)$"),
    db: AsyncSession = Depends(get_db)
):
    """List books with pagination, filtering, and sorting."""
    query = select(Book)
    
    if search:
        query = query.where(
            Book.title.ilike(f"%{search}%") | 
            Book.author.ilike(f"%{search}%") |
            Book.narrator.ilike(f"%{search}%")
        )
    if author:
        query = query.where(Book.author.ilike(f"%{author}%"))
    if genre:
        query = query.where(Book.genres.contains([genre]))
    if source:
        query = query.where(Book.source == source)
    if status:
        query = query.where(Book.status == status)
    
    # Sorting
    sort_col = getattr(Book, sort, Book.added_at)
    if order == "desc":
        query = query.order_by(sort_col.desc())
    else:
        query = query.order_by(sort_col.asc())
    
    # Count total
    count_query = select(func.count()).select_from(query.subquery())
    total = (await db.execute(count_query)).scalar()
    
    # Pagination
    query = query.offset((page - 1) * per_page).limit(per_page)
    result = await db.execute(query)
    books = result.scalars().all()
    
    return {
        "books": [book_to_dict(b) for b in books],
        "pagination": {
            "page": page,
            "per_page": per_page,
            "total": total,
            "total_pages": (total + per_page - 1) // per_page,
        }
    }


@router.get("/library/stats")
async def library_stats(db: AsyncSession = Depends(get_db)):
    """Get library statistics."""
    total_books = (await db.execute(select(func.count(Book.id)))).scalar()
    total_duration = (await db.execute(select(func.sum(Book.duration_seconds)))).scalar() or 0
    total_size = (await db.execute(select(func.sum(Book.file_size)))).scalar() or 0
    
    by_source = {}
    for source in BookSource:
        count = (await db.execute(
            select(func.count(Book.id)).where(Book.source == source)
        )).scalar()
        by_source[source.value] = count
    
    by_status = {}
    for status in BookStatus:
        count = (await db.execute(
            select(func.count(Book.id)).where(Book.status == status)
        )).scalar()
        by_status[status.value] = count
    
    return {
        "total_books": total_books,
        "total_duration_seconds": total_duration,
        "total_size_bytes": total_size,
        "by_source": by_source,
        "by_status": by_status,
    }


@router.get("/library/{book_id}")
async def get_book(book_id: int, db: AsyncSession = Depends(get_db)):
    """Get detailed book information with chapters."""
    result = await db.execute(select(Book).where(Book.id == book_id))
    book = result.scalar_one_or_none()
    
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    
    # Get chapters
    chapters_result = await db.execute(
        select(Chapter).where(Chapter.book_id == book_id).order_by(Chapter.index)
    )
    chapters = chapters_result.scalars().all()
    
    # Get highlights
    highlights_result = await db.execute(
        select(Highlight).where(Highlight.book_id == book_id).order_by(Highlight.location_value)
    )
    highlights = highlights_result.scalars().all()
    
    return {
        **book_to_dict(book),
        "chapters": [chapter_to_dict(c) for c in chapters],
        "highlights": [highlight_to_dict(h) for h in highlights],
    }


@router.post("/library/scan")
async def scan_library(
    background_tasks: BackgroundTasks,
    force_refresh: bool = False,
    db: AsyncSession = Depends(get_db)
):
    """Trigger a library scan."""
    scanner = LibraryScanner(db)
    
    async def run_scan():
        await scanner.scan_library(force_refresh=force_refresh)
    
    background_tasks.add_task(run_scan)
    return {"message": "Library scan started"}


@router.get("/library/{book_id}/stream")
async def stream_book(book_id: int, db: AsyncSession = Depends(get_db)):
    """Stream audiobook file."""
    from fastapi.responses import FileResponse
    
    result = await db.execute(select(Book).where(Book.id == book_id))
    book = result.scalar_one_or_none()
    
    if not book or not book.file_path:
        raise HTTPException(status_code=404, detail="Book not found")
    
    # For now, return the first audio file or directory
    # In production, this would handle range requests for streaming
    path = Path(book.file_path)
    
    if path.is_file():
        return FileResponse(path, media_type="audio/mpeg")
    elif path.is_dir():
        # Find first audio file
        for ext in ['.mp3', '.m4b', '.m4a', '.flac', '.ogg']:
            files = list(path.rglob(f"*{ext}"))
            if files:
                return FileResponse(files[0], media_type="audio/mpeg")
    
    raise HTTPException(status_code=404, detail="Audio file not found")


@router.get("/library/{book_id}/chapter/{chapter_id}/stream")
async def stream_chapter(book_id: int, chapter_id: int, db: AsyncSession = Depends(get_db)):
    """Stream a specific chapter."""
    from fastapi.responses import FileResponse
    
    result = await db.execute(
        select(Chapter).where(Chapter.id == chapter_id, Chapter.book_id == book_id)
    )
    chapter = result.scalar_one_or_none()
    
    if not chapter or not chapter.file_path:
        raise HTTPException(status_code=404, detail="Chapter not found")
    
    path = Path(chapter.file_path)
    if path.exists():
        return FileResponse(path, media_type="audio/mpeg")
    
    raise HTTPException(status_code=404, detail="Chapter file not found")


@router.patch("/library/{book_id}")
async def update_book(
    book_id: int,
    updates: dict,
    db: AsyncSession = Depends(get_db)
):
    """Update book metadata."""
    result = await db.execute(select(Book).where(Book.id == book_id))
    book = result.scalar_one_or_none()
    
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    
    # Allowed fields to update
    allowed_fields = [
        'title', 'author', 'narrator', 'series', 'series_number',
        'description', 'genres', 'tags', 'published_year', 'publisher',
        'isbn', 'asin', 'language', 'rating', 'user_rating'
    ]
    
    for field, value in updates.items():
        if field in allowed_fields and hasattr(book, field):
            setattr(book, field, value)
    
    book.updated_at = datetime.utcnow()
    await db.commit()
    
    return book_to_dict(book)


@router.delete("/library/{book_id}")
async def delete_book(book_id: int, delete_files: bool = False, db: AsyncSession = Depends(get_db)):
    """Delete a book from library."""
    result = await db.execute(select(Book).where(Book.id == book_id))
    book = result.scalar_one_or_none()
    
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    
    if delete_files and book.file_path:
        path = Path(book.file_path)
        if path.exists():
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()
    
    await db.delete(book)
    await db.commit()
    
    return {"message": "Book deleted"}


def book_to_dict(book: Book) -> dict:
    return {
        "id": book.id,
        "title": book.title,
        "author": book.author,
        "narrator": book.narrator,
        "series": book.series,
        "series_number": book.series_number,
        "description": book.description,
        "genres": book.genres,
        "tags": book.tags,
        "published_year": book.published_year,
        "publisher": book.publisher,
        "isbn": book.isbn,
        "asin": book.asin,
        "language": book.language,
        "duration_seconds": book.duration_seconds,
        "cover_url": book.cover_url,
        "cover_path": book.cover_path,
        "source": book.source.value if book.source else None,
        "source_id": book.source_id,
        "source_url": book.source_url,
        "status": book.status.value if book.status else None,
        "file_path": book.file_path,
        "file_size": book.file_size,
        "file_format": book.file_format,
        "bitrate": book.bitrate,
        "sample_rate": book.sample_rate,
        "chapters_count": book.chapters_count,
        "created_at": book.created_at.isoformat() if book.created_at else None,
        "updated_at": book.updated_at.isoformat() if book.updated_at else None,
        "added_at": book.added_at.isoformat() if book.added_at else None,
        "last_played_at": book.last_played_at.isoformat() if book.last_played_at else None,
        "play_count": book.play_count,
        "rating": book.rating,
        "user_rating": book.user_rating,
        "progress_seconds": book.progress_seconds,
        "completed": book.completed,
    }


def chapter_to_dict(chapter: Chapter) -> dict:
    return {
        "id": chapter.id,
        "book_id": chapter.book_id,
        "index": chapter.index,
        "title": chapter.title,
        "start_offset_seconds": chapter.start_offset_seconds,
        "end_offset_seconds": chapter.end_offset_seconds,
        "duration_seconds": chapter.duration_seconds,
        "file_path": chapter.file_path,
    }


def highlight_to_dict(highlight: Highlight) -> dict:
    return {
        "id": highlight.id,
        "book_id": highlight.book_id,
        "chapter_id": highlight.chapter_id,
        "text": highlight.text,
        "note": highlight.note,
        "color": highlight.color,
        "location_type": highlight.location_type,
        "location_value": highlight.location_value,
        "location_end": highlight.location_end,
        "created_at": highlight.created_at.isoformat() if highlight.created_at else None,
        "updated_at": highlight.updated_at.isoformat() if highlight.updated_at else None,
    }