from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from typing import List, Optional
from datetime import datetime
import json

from app.models.database import Book, Highlight, Chapter
from app.models import get_db

router = APIRouter()


@router.get("/highlights/book/{book_id}")
async def get_book_highlights(
    book_id: int,
    chapter_id: Optional[int] = None,
    db: AsyncSession = Depends(get_db)
):
    """Get all highlights for a book."""
    query = select(Highlight).where(Highlight.book_id == book_id)
    
    if chapter_id:
        query = query.where(Highlight.chapter_id == chapter_id)
    
    query = query.order_by(Highlight.location_value)
    result = await db.execute(query)
    highlights = result.scalars().all()
    
    return {"highlights": [highlight_to_dict(h) for h in highlights]}


@router.post("/highlights")
async def create_highlight(
    book_id: int,
    text: str,
    location_value: float,
    location_type: str = "time",
    chapter_id: Optional[int] = None,
    note: Optional[str] = None,
    color: str = "#FFD700",
    location_end: Optional[float] = None,
    device_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db)
):
    """Create a new highlight/annotation."""
    # Verify book exists
    book_result = await db.execute(select(Book).where(Book.id == book_id))
    book = book_result.scalar_one_or_none()
    
    if not book:
        raise HTTPException(status_code=404, detail="Book not found")
    
    # Verify chapter if provided
    if chapter_id:
        chapter_result = await db.execute(
            select(Chapter).where(Chapter.id == chapter_id, Chapter.book_id == book_id)
        )
        chapter = chapter_result.scalar_one_or_none()
        if not chapter:
            raise HTTPException(status_code=404, detail="Chapter not found")
    
    highlight = Highlight(
        book_id=book_id,
        chapter_id=chapter_id,
        text=text,
        note=note,
        color=color,
        location_type=location_type,
        location_value=location_value,
        location_end=location_end,
        device_id=device_id,
    )
    
    db.add(highlight)
    await db.commit()
    await db.refresh(highlight)
    
    return highlight_to_dict(highlight)


@router.patch("/highlights/{highlight_id}")
async def update_highlight(
    highlight_id: int,
    updates: dict,
    db: AsyncSession = Depends(get_db)
):
    """Update a highlight."""
    result = await db.execute(select(Highlight).where(Highlight.id == highlight_id))
    highlight = result.scalar_one_or_none()
    
    if not highlight:
        raise HTTPException(status_code=404, detail="Highlight not found")
    
    allowed_fields = ['text', 'note', 'color', 'location_value', 'location_end']
    
    for field, value in updates.items():
        if field in allowed_fields and hasattr(highlight, field):
            setattr(highlight, field, value)
    
    highlight.updated_at = datetime.utcnow()
    await db.commit()
    
    return highlight_to_dict(highlight)


@router.delete("/highlights/{highlight_id}")
async def delete_highlight(highlight_id: int, db: AsyncSession = Depends(get_db)):
    """Delete a highlight."""
    result = await db.execute(select(Highlight).where(Highlight.id == highlight_id))
    highlight = result.scalar_one_or_none()
    
    if not highlight:
        raise HTTPException(status_code=404, detail="Highlight not found")
    
    await db.delete(highlight)
    await db.commit()
    
    return {"message": "Highlight deleted"}


@router.post("/highlights/sync")
async def sync_highlights(
    book_id: int,
    highlights: List[dict],  # List of highlight dicts with device_id
    device_id: str,
    db: AsyncSession = Depends(get_db)
):
    """Sync highlights from a device (bidirectional)."""
    # This implements a simple last-write-wins sync per device
    # In production, use proper CRDT or operational transform
    
    # Get existing highlights for this book and device
    existing_result = await db.execute(
        select(Highlight).where(
            Highlight.book_id == book_id,
            Highlight.device_id == device_id
        )
    )
    existing = {h.location_value: h for h in existing_result.scalars().all()}
    
    created = []
    updated = []
    deleted = []
    
    # Process incoming highlights
    incoming_locations = set()
    
    for h_data in highlights:
        loc = h_data.get('location_value')
        incoming_locations.add(loc)
        
        if loc in existing:
            # Update existing
            highlight = existing[loc]
            for field in ['text', 'note', 'color', 'location_end', 'location_type']:
                if field in h_data:
                    setattr(highlight, field, h_data[field])
            highlight.updated_at = datetime.utcnow()
            updated.append(highlight_to_dict(highlight))
        else:
            # Create new
            highlight = Highlight(
                book_id=book_id,
                chapter_id=h_data.get('chapter_id'),
                text=h_data['text'],
                note=h_data.get('note'),
                color=h_data.get('color', '#FFD700'),
                location_type=h_data.get('location_type', 'time'),
                location_value=loc,
                location_end=h_data.get('location_end'),
                device_id=device_id,
            )
            db.add(highlight)
            await db.flush()
            created.append(highlight_to_dict(highlight))
    
    # Delete highlights that exist locally but not in incoming (from this device)
    for loc, highlight in existing.items():
        if loc not in incoming_locations:
            await db.delete(highlight)
            deleted.append(highlight.id)
    
    await db.commit()
    
    # Return all highlights for this book (merged)
    all_result = await db.execute(
        select(Highlight).where(Highlight.book_id == book_id).order_by(Highlight.location_value)
    )
    all_highlights = all_result.scalars().all()
    
    return {
        "created": len(created),
        "updated": len(updated),
        "deleted": len(deleted),
        "highlights": [highlight_to_dict(h) for h in all_highlights],
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
        "device_id": highlight.device_id,
    }