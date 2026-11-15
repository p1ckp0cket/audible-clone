from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import Optional
from datetime import datetime
import os
from pathlib import Path

from app.core.config import get_settings

router = APIRouter()


class OTAUpdateInfo(BaseModel):
    version: str
    release_notes: str
    download_url: str
    force_update: bool = False
    min_supported_version: Optional[str] = None
    released_at: datetime


class OTAUpdateResponse(BaseModel):
    update_available: bool
    current_version: str
    latest_version: str
    update_info: Optional[OTAUpdateInfo] = None


# Self-hosted OTA: versions are stored as APK files in OTA directory
# Structure: /mnt/drive2/ota/{version}/app-release.apk + release_notes.txt
OTA_BASE_DIR = Path("/mnt/drive2/ota")


def get_available_versions() -> list:
    """Scan OTA directory for available versions."""
    versions = []
    if OTA_BASE_DIR.exists():
        for version_dir in sorted(OTA_BASE_DIR.iterdir(), key=lambda x: x.name, reverse=True):
            if version_dir.is_dir():
                apk_file = version_dir / "app-release.apk"
                notes_file = version_dir / "release_notes.txt"
                
                if apk_file.exists():
                    release_notes = ""
                    if notes_file.exists():
                        release_notes = notes_file.read_text()
                    
                    # Parse version from directory name
                    version = version_dir.name
                    try:
                        # Try to parse as semantic version
                        parts = version.split('.')
                        if len(parts) >= 3:
                            versions.append({
                                "version": version,
                                "release_notes": release_notes or f"Version {version}",
                                "download_url": f"/api/v1/ota/download/{version}",
                                "force_update": False,
                                "released_at": datetime.fromtimestamp(apk_file.stat().st_mtime),
                            })
                    except:
                        pass
    return versions


@router.get("/ota/check", response_model=OTAUpdateResponse)
async def check_for_updates(
    current_version: str = Query(..., description="Current app version"),
    platform: str = Query("android", regex="^(android|ios)$"),
    channel: str = Query("stable", regex="^(stable|beta|alpha)$"),
):
    """Check for app updates from self-hosted OTA."""
    settings = get_settings()
    
    versions = get_available_versions()
    
    if not versions:
        return OTAUpdateResponse(
            update_available=False,
            current_version=current_version,
            latest_version=current_version,
            update_info=None,
        )
    
    # Get latest version
    latest = versions[0]
    latest_version = latest["version"]
    
    # Compare versions
    def version_tuple(v):
        try:
            return tuple(map(int, v.split('.')))
        except:
            return (0, 0, 0)
    
    update_available = version_tuple(latest_version) > version_tuple(current_version)
    
    update_info = None
    if update_available:
        update_info = OTAUpdateInfo(
            version=latest["version"],
            release_notes=latest["release_notes"],
            download_url=latest["download_url"],
            force_update=latest.get("force_update", False),
            min_supported_version=latest.get("min_supported_version"),
            released_at=latest["released_at"],
        )
        
        # Check minimum supported version
        if update_info.min_supported_version:
            if version_tuple(current_version) < version_tuple(update_info.min_supported_version):
                update_info.force_update = True
    
    return OTAUpdateResponse(
        update_available=update_available,
        current_version=current_version,
        latest_version=latest_version,
        update_info=update_info,
    )


@router.get("/ota/download/{version}")
async def download_ota(version: str):
    """Download APK for specific version."""
    apk_path = OTA_BASE_DIR / version / "app-release.apk"
    
    if not apk_path.exists():
        raise HTTPException(status_code=404, detail=f"Version {version} not found")
    
    return FileResponse(
        path=str(apk_path),
        filename=f"audible-clone-{version}.apk",
        media_type="application/vnd.android.package-archive",
    )


@router.get("/ota/versions")
async def list_versions():
    """List all available versions."""
    versions = get_available_versions()
    return {"versions": versions}


@router.post("/ota/publish")
async def publish_version(
    version: str,
    release_notes: str,
    force_update: bool = False,
    min_supported_version: Optional[str] = None,
    file: Optional[bytes] = None,  # In real use, this would be a multipart upload
):
    """Publish a new version (for CI/CD or manual upload)."""
    version_dir = OTA_BASE_DIR / version
    version_dir.mkdir(parents=True, exist_ok=True)
    
    # Save release notes
    notes_file = version_dir / "release_notes.txt"
    notes_file.write_text(release_notes)
    
    # In real implementation, save uploaded APK
    # For now, create placeholder
    apk_file = version_dir / "app-release.apk"
    if not apk_file.exists():
        apk_file.write_text(f"# Placeholder for {version} - replace with actual APK")
    
    # Create metadata file
    meta_file = version_dir / "metadata.json"
    import json
    meta_file.write_text(json.dumps({
        "version": version,
        "release_notes": release_notes,
        "force_update": force_update,
        "min_supported_version": min_supported_version,
        "published_at": datetime.utcnow().isoformat(),
    }, indent=2))
    
    return {"message": f"Version {version} published", "version": version}