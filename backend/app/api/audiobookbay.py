from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from typing import List, Optional
from datetime import datetime

from app.models.database import Book, DownloadTask, BookSource, BookStatus
from app.models import get_db
from app.services.audiobookbay_service import AudioBookBayService

router = APIRouter()


@router.get("/audiobookbay/search")
async def search_audiobookbay(
    q: str = Query(..., min_length=1),
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db)
):
    """Search AudioBookBay for audiobooks."""
    service = AudioBookBayService()
    try:
        results = await service.search(q, max_pages=3)  # Limit pages for API
        
        # Paginate results
        start = (page - 1) * per_page
        end = start + per_page
        paginated = results[start:end]
        
        return {
            "books": paginated,
            "pagination": {
                "page": page,
                "per_page": per_page,
                "total": len(results),
                "total_pages": (len(results) + per_page - 1) // per_page,
            }
        }
    finally:
        await service.close()


@router.get("/audiobookbay/book/{book_id}")
async def get_audiobookbay_book(book_id: str, db: AsyncSession = Depends(get_db)):
    """Get detailed info for an AudioBookBay book by details URL."""
    service = AudioBookBayService()
    try:
        # book_id is actually the details URL path
        details_url = f"https://{service.abb_hostname}/audiobook/{book_id}/"
        # Or if full URL passed
        if book_id.startswith("http"):
            details_url = book_id
        
        result = await service.get_book_details(details_url)
        return result
    finally:
        await service.close()


@router.post("/audiobookbay/download")
async def download_audiobookbay_book(
    details_url: str,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db)
):
    """Send an AudioBookBay book to torrent client for download."""
    service = AudioBookBayService()
    try:
        # Extract magnet link
        magnet_link = await service.extract_magnet_link(details_url)
        
        if not magnet_link:
            raise HTTPException(status_code=400, detail="Failed to extract magnet link")
        
        # Get book details for title
        details = await service.get_book_details(details_url)
        title = details.get('title', 'Unknown')
        
        # Create download task
        task = DownloadTask(
            source=BookSource.AUDIOBOOKBAY,
            source_id=details_url,
            source_url=details_url,
            title=title,
            status="pending",
        )
        db.add(task)
        await db.commit()
        await db.refresh(task)
        
        # In a real implementation, this would send to qBittorrent/Transmission
        # For now, we'll just return the magnet link for manual adding
        
        return {
            "message": "Magnet link extracted. Add to your torrent client.",
            "magnet_link": magnet_link,
            "task_id": task.id,
            "title": title,
            "save_path": f"/mnt/drive2/audiobooks/{service.sanitize_title(title)}",
        }
    finally:
        await service.close()


@router.get("/audiobookbay/download/status/{task_id}")
async def get_audiobookbay_download_status(task_id: int, db: AsyncSession = Depends(get_db)):
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
        "download_path": task.download_path,
        "created_at": task.created_at.isoformat() if task.created_at else None,
        "started_at": task.started_at.isoformat() if task.started_at else None,
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
    }