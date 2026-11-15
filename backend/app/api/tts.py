from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from typing import List, Optional
from datetime import datetime
import os
import shutil
from pathlib import Path
import aiofiles

from app.models.database import Book, TTSTask, BookSource, BookStatus
from app.models import get_db
from app.services.tts_service import TTSService
from app.core.config import get_settings

router = APIRouter()


@router.get("/tts/voices")
async def get_tts_voices(db: AsyncSession = Depends(get_db)):
    """Get available TTS voices."""
    service = TTSService(db)
    voices = service.get_available_voices()
    return {"voices": voices, "engine": service.engine.engine}


@router.post("/tts/upload")
async def upload_ebook_for_tts(
    file: UploadFile = File(...),
    title: Optional[str] = Form(None),
    author: Optional[str] = Form(None),
    language: str = Form("en"),
    engine: str = Form("piper"),
    background_tasks: BackgroundTasks = None,
    db: AsyncSession = Depends(get_db)
):
    """Upload an ebook for TTS conversion."""
    settings = get_settings()
    
    # Validate file type
    allowed_extensions = {'.epub', '.pdf', '.txt', '.mobi'}
    file_ext = Path(file.filename).suffix.lower()
    
    if file_ext not in allowed_extensions:
        raise HTTPException(
            status_code=400, 
            detail=f"Unsupported file type. Allowed: {', '.join(allowed_extensions)}"
        )
    
    # Save uploaded file
    upload_dir = Path(settings.EBOOKS_UPLOAD_PATH)
    upload_dir.mkdir(parents=True, exist_ok=True)
    
    safe_filename = f"{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}_{file.filename}"
    file_path = upload_dir / safe_filename
    
    async with aiofiles.open(file_path, 'wb') as f:
        content = await file.read()
        await f.write(content)
    
    # Create TTS task
    task = TTSTask(
        ebook_path=str(file_path),
        ebook_format=file_ext.lstrip('.'),
        title=title or Path(file.filename).stem,
        author=author,
        language=language,
        engine=engine,
        status="pending",
    )
    db.add(task)
    await db.commit()
    await db.refresh(task)
    
    # Start processing in background
    service = TTSService(db)
    
    async def run_tts():
        try:
            await service.process_ebook(task.id)
        finally:
            pass  # Keep service for now
    
    background_tasks.add_task(run_tts)
    
    return {"message": "Ebook uploaded, TTS conversion started", "task_id": task.id}


@router.get("/tts/task/{task_id}")
async def get_tts_task(task_id: int, db: AsyncSession = Depends(get_db)):
    """Get TTS task status."""
    result = await db.execute(select(TTSTask).where(TTSTask.id == task_id))
    task = result.scalar_one_or_none()
    
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    return {
        "id": task.id,
        "book_id": task.book_id,
        "ebook_path": task.ebook_path,
        "ebook_format": task.ebook_format,
        "title": task.title,
        "author": task.author,
        "language": task.language,
        "voice_mapping": task.voice_mapping,
        "engine": task.engine,
        "status": task.status,
        "progress": task.progress,
        "current_step": task.current_step,
        "error_message": task.error_message,
        "output_path": task.output_path,
        "created_at": task.created_at.isoformat() if task.created_at else None,
        "started_at": task.started_at.isoformat() if task.started_at else None,
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
    }


@router.post("/tts/task/{task_id}/assign-voices")
async def assign_character_voices(
    task_id: int,
    voice_mapping: dict,  # character -> voice_id
    db: AsyncSession = Depends(get_db)
):
    """Assign voices to characters for TTS."""
    result = await db.execute(select(TTSTask).where(TTSTask.id == task_id))
    task = result.scalar_one_or_none()
    
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    if task.status not in ("pending", "parsing"):
        raise HTTPException(status_code=400, detail="Cannot modify voices at this stage")
    
    task.voice_mapping = voice_mapping
    await db.commit()
    
    return {"message": "Voice assignments updated", "voice_mapping": task.voice_mapping}