from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from typing import List, Optional
from datetime import datetime

from app.models.database import Book, DownloadTask, BookSource, BookStatus
from app.models import get_db
from app.services.librivox_service import LibriVoxService

router = APIRouter()


@router.get("/librivox/search")
async def search_librivox(
    q: str = Query(..., min_length=1),
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    genre: Optional[str] = None,
    db: AsyncSession = Depends(get_db)
):
    """Search LibriVox for public domain audiobooks."""
    service = LibriVoxService(db)
    try:
        offset = (page - 1) * per_page
        result = await service.search(query=q, limit=per_page, offset=offset, genre=genre)
        return result
    finally:
        await service.close()


@router.get("/librivox/book/{book_id}")
async def get_librivox_book(book_id: int, db: AsyncSession = Depends(get_db)):
    """Get detailed info for a LibriVox book."""
    service = LibriVoxService(db)
    try:
        result = await service.get_book(book_id)
        return result
    finally:
        await service.close()


@router.get("/librivox/genres")
async def get_librivox_genres(db: AsyncSession = Depends(get_db)):
    """Get available LibriVox genres."""
    service = LibriVoxService(db)
    try:
        genres = await service.get_genres()
        return {"genres": genres}
    finally:
        await service.close()


@router.post("/librivox/download/{book_id}")
async def download_librivox_book(
    book_id: int,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db)
):
    """Download a LibriVox book to the server."""
    # Create download task
    task = DownloadTask(
        source=BookSource.LIBRIVOX,
        source_id=str(book_id),
        title=f"LibriVox Book {book_id}",
        status="pending",
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)
    
    service = LibriVoxService(db)
    
    async def run_download():
        try:
            await service.download_book(book_id, task.id)
        finally:
            await service.close()
    
    background_tasks.add_task(run_download)
    
    return {"message": "Download started", "task_id": task.id}


@router.get("/librivox/download/status/{task_id}")
async def get_librivox_download_status(task_id: int, db: AsyncSession = Depends(get_db)):
    """Get download task status."""
    result = await db.execute(select(DownloadTask).where(DownloadTask.id == task_id))
    task = result.scalar_one_or_none()
    
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    return {
        "id": task.id,
        "book_id": task.book_id,
        "source": task.source.value if task.source else None,
        "source_id": task.source_id,
        "title": task.title,
        "author": task.author,
        "status": task.status,
        "progress": task.progress,
        "error_message": task.error_message,
        "current_step": task.current_step,
        "download_path": task.download_path,
        "created_at": task.created_at.isoformat() if task.created_at else None,
        "started_at": task.started_at.isoformat() if task.started_at else None,
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
    }