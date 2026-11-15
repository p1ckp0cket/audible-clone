# Audible Clone - Build & Deploy Instructions

## Quick Start (Single Command)

```bash
# On ANY machine with Android Studio/SDK installed:
cd /opt/data/audiobook-project
chmod +x build-and-deploy.sh
./build-and-deploy.sh
```

## What This Script Does (Zero Manual Steps)

| Step | Action |
|------|--------|
| 1. Prerequisites | Checks Android SDK, adb, gradle wrapper |
| 2. Build | `./gradlew clean assembleDebug assembleRelease` |
| 3. Install | Auto-installs on connected device/emulator |
| 4. Deploy | Copies to paperclip, runs `docker compose up -d` |
| 5. OTA | Copies release APK to `/mnt/drive2/ota/1.0.0/` |
| 5. Tests | Runs unit tests |

## Prerequisites (One-Time Setup)

### On Your Machine:
- [ ] Android Studio installed
- [ ] Android SDK (API 34) installed
- [ ] `ANDROID_HOME` set in shell profile
- [ ] `adb` in PATH
- [ ] SSH access to paperclip server

### On Paperclip Server:
- [ ] Docker + Docker Compose installed
- [ ] Tailscale running

## What Gets Built

| Artifact | Location | Purpose |
|----------|----------|---------|
| Debug APK | `android/app/build/outputs/apk/debug/app-debug.apk` | Testing, install via adb |
| Release APK | `android/app/build/outputs/apk/release/app-release.apk` | OTA updates, distribution |

## After Running

### Backend (auto-deployed to paperclip):
```
API:        https://yourdomain.com/api/v1
Health:     https://yourdomain.com/health
Docs:       https://yourdomain.com/docs
```

### Android App:
- Installed on connected device automatically
- Or manually: `adb install -r app-debug.apk`

### OTA Updates:
```
Version 1.0.0 at /mnt/drive2/ota/1.0.0/app-release.apk
Check: curl -k https://yourdomain.com/api/v1/ota/check?current_version=1.0.0
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `ANDROID_HOME not set` | `export ANDROID_HOME=$HOME/Android/Sdk` (add to ~/.bashrc) |
| `adb not found` | `export PATH=$PATH:$ANDROID_HOME/platform-tools` |
| `gradlew: Permission denied` | `chmod +x android/gradlew` |
| `Docker daemon not running` | `sudo systemctl start docker` (Linux) or start Docker Desktop |
| `Cannot reach paperclip` | Check Tailscale: `tailscale status` |
| `qBittorrent not downloading` | WebUI at http://100.72.156.27:8080 → Add "audiobooks" category |

## CI/CD Alternative (No Local Build Needed)

Push to GitHub → GitHub Actions builds automatically:

```bash
git init && git add . && git commit -m "Initial"
gh repo create audible-clone --public --push
# Go to GitHub → Actions → "Build Android APK" → Run workflow
# Download APK from Actions → Artifacts
```

## File Structure

```
/opt/data/audiobook-project/
├── build-and-deploy.sh          # ← RUN THIS
├── .github/workflows/build-android.yml  # GitHub Actions
├── android/                     # Android app source
├── backend/                     # FastAPI backend
├── nginx/                       # Reverse proxy config
├── docker-compose.yml           # Full stack
└── TEST_PLAN.txt               # Complete test procedures
```

## Need Help?

Check logs:
```bash
# Backend logs
ssh paperclip@100.72.156.27 "docker compose logs -f audiobook-api"

# Android build logs
./android/gradlew assembleDebug --stacktrace

# Device logs
adb logcat -d | grep -i audibleclone
```