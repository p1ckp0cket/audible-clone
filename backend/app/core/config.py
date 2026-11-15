from pydantic_settings import BaseSettings
from functools import lru_cache
import os


class Settings(BaseSettings):
    # App
    APP_NAME: str = "Audible Clone API"
    DEBUG: bool = False
    API_V1_PREFIX: str = "/api/v1"
    
    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    
    # Database
    DATABASE_URL: str = "sqlite+aiosqlite:///data/library.db"
    
    # File paths
    AUDIOBOOKS_PATH: str = "/mnt/drive2/audiobooks"
    EBOOKS_UPLOAD_PATH: str = "/mnt/drive2/ebooks"
    COVERS_PATH: str = "/mnt/drive2/covers"
    TTS_OUTPUT_PATH: str = "/mnt/drive2/tts_output"
    
    # Security
    SECRET_KEY: str = "change-me-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days
    
    # External APIs
    LIBRIVOX_API_BASE: str = "https://librivox.org/api/feed/audiobooks"
    OPENLIBRARY_API_BASE: str = "https://openlibrary.org/api"
    GOOGLE_BOOKS_API_BASE: str = "https://www.googleapis.com/books/v1"
    MUSICBRAINZ_API_BASE: str = "https://musicbrainz.org/ws/2"
    
    # AudioBookBay
    ABB_HOSTNAME: str = "audiobookbay.is"
    ABB_PAGE_LIMIT: int = 5
    
    # Torrent client (qBittorrent)
    QBITTORRENT_HOST: str = "localhost"
    QBITTORRENT_PORT: int = 8080
    QBITTORRENT_USERNAME: str = "admin"
    QBITTORRENT_PASSWORD: str = "admin"
    QBITTORRENT_CATEGORY: str = "audiobooks"
    QBITTORRENT_SAVE_PATH: str = "/mnt/drive2/audiobooks"
    
    # TTS
    TTS_ENGINE: str = "auto"  # auto, piper, kokoro (auto = use best available)
    PIPER_MODEL_DIR: str = "/home/paperclip/piper/models"
    KOKORO_MODEL_PATH: str = "/home/paperclip/kokoro/kokoro-v1.0.onnx"
    KOKORO_VOICES_PATH: str = "/home/paperclip/kokoro/voices-v1.0.bin"
    TTS_DEFAULT_VOICE: str = "en_US-lessac-medium"
    TTS_SAMPLE_RATE: int = 22050
    
    # OTA Updates (self-hosted)
    OTA_UPDATE_URL: str = "https://yourdomain.com/ota"  # Self-hosted OTA endpoint
    OTA_CURRENT_VERSION: str = "1.0.0"
    OTA_SELF_HOSTED: bool = True
    
    # CORS
    CORS_ORIGINS: list[str] = ["*"]
    
    class Config:
        env_file = ".env"
        case_sensitive = True


@lru_cache()
def get_settings() -> Settings:
    return Settings()