from sqlalchemy import (
    Column, Integer, String, Text, DateTime, Float, Boolean, 
    ForeignKey, Index, Enum as SQLEnum, JSON
)
from sqlalchemy.orm import relationship, declarative_base
from datetime import datetime
import enum

Base = declarative_base()


class BookStatus(str, enum.Enum):
    AVAILABLE = "available"
    DOWNLOADING = "downloading"
    PROCESSING = "processing"
    ERROR = "error"


class BookSource(str, enum.Enum):
    LOCAL = "local"
    LIBRIVOX = "librivox"
    AUDIOBOOKBAY = "audiobookbay"
    TTS_GENERATED = "tts_generated"
    UPLOADED = "uploaded"


class Book(Base):
    __tablename__ = "books"
    
    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(500), nullable=False, index=True)
    author = Column(String(500), nullable=False, index=True)
    narrator = Column(String(500), nullable=True)
    series = Column(String(500), nullable=True)
    series_number = Column(Float, nullable=True)
    description = Column(Text, nullable=True)
    genres = Column(JSON, nullable=True)  # List of strings
    tags = Column(JSON, nullable=True)    # List of strings
    published_year = Column(Integer, nullable=True)
    publisher = Column(String(500), nullable=True)
    isbn = Column(String(50), nullable=True, index=True)
    asin = Column(String(50), nullable=True, index=True)
    language = Column(String(10), default="en")
    duration_seconds = Column(Integer, nullable=True)
    cover_url = Column(String(1000), nullable=True)
    cover_path = Column(String(1000), nullable=True)
    source = Column(SQLEnum(BookSource), default=BookSource.LOCAL, index=True)
    source_id = Column(String(200), nullable=True, index=True)  # External ID
    source_url = Column(String(1000), nullable=True)
    status = Column(SQLEnum(BookStatus), default=BookStatus.AVAILABLE, index=True)
    file_path = Column(String(1000), nullable=True)
    file_size = Column(Integer, nullable=True)
    file_format = Column(String(20), nullable=True)  # mp3, m4b, flac, etc.
    bitrate = Column(Integer, nullable=True)
    sample_rate = Column(Integer, nullable=True)
    chapters_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    added_at = Column(DateTime, default=datetime.utcnow, index=True)
    last_played_at = Column(DateTime, nullable=True)
    play_count = Column(Integer, default=0)
    rating = Column(Float, nullable=True)
    user_rating = Column(Float, nullable=True)
    progress_seconds = Column(Integer, default=0)
    completed = Column(Boolean, default=False)
    
    # Relationships
    chapters = relationship("Chapter", back_populates="book", cascade="all, delete-orphan", order_by="Chapter.index")
    highlights = relationship("Highlight", back_populates="book", cascade="all, delete-orphan")
    
    # Indexes
    __table_args__ = (
        Index('ix_books_title_author', 'title', 'author'),
        Index('ix_books_source_status', 'source', 'status'),
    )


class Chapter(Base):
    __tablename__ = "chapters"
    
    id = Column(Integer, primary_key=True, index=True)
    book_id = Column(Integer, ForeignKey("books.id", ondelete="CASCADE"), nullable=False, index=True)
    index = Column(Integer, nullable=False)
    title = Column(String(500), nullable=True)
    start_offset_seconds = Column(Float, nullable=False)
    end_offset_seconds = Column(Float, nullable=True)
    duration_seconds = Column(Float, nullable=True)
    file_path = Column(String(1000), nullable=True)  # For split files
    created_at = Column(DateTime, default=datetime.utcnow)
    
    # Relationships
    book = relationship("Book", back_populates="chapters")
    
    __table_args__ = (
        Index('ix_chapters_book_index', 'book_id', 'index'),
    )


class Highlight(Base):
    __tablename__ = "highlights"
    
    id = Column(Integer, primary_key=True, index=True)
    book_id = Column(Integer, ForeignKey("books.id", ondelete="CASCADE"), nullable=False, index=True)
    chapter_id = Column(Integer, ForeignKey("chapters.id", ondelete="SET NULL"), nullable=True, index=True)
    text = Column(Text, nullable=False)
    note = Column(Text, nullable=True)
    color = Column(String(20), default="#FFD700")  # Yellow default
    location_type = Column(String(20), default="time")  # time, cfi, percentage
    location_value = Column(Float, nullable=False)  # seconds, cfi, or percentage
    location_end = Column(Float, nullable=True)  # For range highlights
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    device_id = Column(String(100), nullable=True)  # For sync conflict resolution
    
    # Relationships
    book = relationship("Book", back_populates="highlights")
    chapter = relationship("Chapter")
    
    __table_args__ = (
        Index('ix_highlights_book_location', 'book_id', 'location_value'),
    )


class DownloadTask(Base):
    __tablename__ = "download_tasks"
    
    id = Column(Integer, primary_key=True, index=True)
    book_id = Column(Integer, ForeignKey("books.id", ondelete="CASCADE"), nullable=True, index=True)
    source = Column(SQLEnum(BookSource), nullable=False)
    source_id = Column(String(200), nullable=True)
    source_url = Column(String(1000), nullable=True)
    title = Column(String(500), nullable=False)
    author = Column(String(500), nullable=True)
    status = Column(String(50), default="pending", index=True)  # pending, downloading, completed, failed
    progress = Column(Float, default=0.0)
    error_message = Column(Text, nullable=True)
    torrent_hash = Column(String(100), nullable=True)
    download_path = Column(String(1000), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    started_at = Column(DateTime, nullable=True)
    completed_at = Column(DateTime, nullable=True)
    
    # Relationships
    book = relationship("Book")


class TTSTask(Base):
    __tablename__ = "tts_tasks"
    
    id = Column(Integer, primary_key=True, index=True)
    book_id = Column(Integer, ForeignKey("books.id", ondelete="CASCADE"), nullable=True, index=True)
    ebook_path = Column(String(1000), nullable=False)
    ebook_format = Column(String(20), nullable=False)  # epub, pdf, txt
    title = Column(String(500), nullable=True)
    author = Column(String(500), nullable=True)
    language = Column(String(10), default="en")
    voice_mapping = Column(JSON, nullable=True)  # Character -> voice mapping
    engine = Column(String(50), default="piper")
    status = Column(String(50), default="pending", index=True)  # pending, parsing, synthesizing, merging, completed, failed
    progress = Column(Float, default=0.0)
    current_step = Column(String(100), nullable=True)
    error_message = Column(Text, nullable=True)
    output_path = Column(String(1000), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    started_at = Column(DateTime, nullable=True)
    completed_at = Column(DateTime, nullable=True)
    
    # Relationships
    book = relationship("Book")


class User(Base):
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(100), unique=True, index=True, nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=True)
    hashed_password = Column(String(255), nullable=False)
    is_active = Column(Boolean, default=True)
    is_admin = Column(Boolean, default=True)  # Single user = admin
    created_at = Column(DateTime, default=datetime.utcnow)
    last_login = Column(DateTime, nullable=True)
    preferences = Column(JSON, nullable=True)  # UI preferences, playback settings


class Device(Base):
    __tablename__ = "devices"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(String(100), unique=True, index=True, nullable=False)
    device_name = Column(String(200), nullable=True)
    platform = Column(String(50), nullable=True)  # android, ios, web
    app_version = Column(String(50), nullable=True)
    last_sync = Column(DateTime, nullable=True)
    push_token = Column(String(500), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)