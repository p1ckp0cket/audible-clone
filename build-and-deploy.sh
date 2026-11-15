#!/bin/bash
# ============================================================================
# AUDIBLE CLONE - COMPLETE BUILD & DEPLOY SCRIPT
# Run this on ANY machine with Android Studio / Android SDK installed
# ============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1"; }
success() { echo -e "${GREEN}✅ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }
error() { echo -e "${RED}❌ $1${NC}"; exit 1; }

# ============================================================================
# CONFIGURATION
# ============================================================================
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$PROJECT_DIR/android"
BACKEND_DIR="$PROJECT_DIR/backend"

# ============================================================================
# PRE-FLIGHT CHECKS
# ============================================================================
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check Android SDK
    if [[ -z "${ANDROID_HOME:-}" ]]; then
        if [[ -d "$HOME/Android/Sdk" ]]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        elif [[ -d "$HOME/Library/Android/sdk" ]]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
        else
            error "ANDROID_HOME not set. Install Android Studio and set ANDROID_HOME."
        fi
    fi
    
    if [[ ! -d "$ANDROID_HOME" ]]; then
        error "Android SDK not found at $ANDROID_HOME"
    fi
    
    # Check adb
    if ! command -v adb &> /dev/null; then
        error "adb not in PATH. Add \$ANDROID_HOME/platform-tools to PATH."
    fi
    
    # Check gradle wrapper
    if [[ ! -f "$ANDROID_DIR/gradlew" ]]; then
        error "gradlew not found at $ANDROID_DIR/gradlew"
    fi
    
    # Make gradlew executable
    chmod +x "$ANDROID_DIR/gradlew"
    
    success "Prerequisites OK"
}

# ============================================================================
# BUILD ANDROID APK
# ============================================================================
build_android() {
    log "Building Android APK..."
    cd "$ANDROID_DIR"
    
    # Clean previous builds
    ./gradlew clean --no-daemon
    
    # Build debug APK
    log "Building debug APK..."
    ./gradlew assembleDebug --no-daemon --stacktrace
    
    # Build release APK (for OTA)
    log "Building release APK..."
    ./gradlew assembleRelease --no-daemon --stacktrace
    
    # Verify outputs
    DEBUG_APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    RELEASE_APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
    
    if [[ -f "$DEBUG_APK" ]]; then
        success "Debug APK: $DEBUG_APK ($(du -h "$DEBUG_APK" | cut -f1))"
    else
        error "Debug APK not found"
    fi
    
    if [[ -f "$RELEASE_APK" ]]; then
        success "Release APK: $RELEASE_APK ($(du -h "$RELEASE_APK" | cut -f1))"
    else
        error "Release APK not found"
    fi
}

# ============================================================================
# INSTALL ON CONNECTED DEVICE
# ============================================================================
install_on_device() {
    log "Checking for connected devices..."
    DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    
    if [[ $DEVICES -eq 0 ]]; then
        warn "No devices connected. Skipping install."
        return 0
    fi
    
    log "Installing debug APK on $DEVICES device(s)..."
    adb install -r "$DEBUG_APK" || error "Install failed"
    success "Installed on device(s)"
}

# ============================================================================
# DEPLOY BACKEND TO PAPERCLIP
# ============================================================================
deploy_backend() {
    log "Deploying backend to paperclip server..."
    
    # Check if we can reach paperclip
    if ! ssh -o ConnectTimeout=5 -o BatchMode=yes paperclip@100.72.156.27 "echo ok" 2>/dev/null; then
        warn "Cannot reach paperclip server. Skipping backend deploy."
        return 0
    fi
    
    log "Copying project to paperclip..."
    scp -r "$PROJECT_DIR" paperclip@100.72.156.27:/home/paperclip/
    
    log "Deploying backend stack..."
    ssh paperclip@100.72.156.27 << 'EOF'
        cd /home/paperclip/audiobook-project
        
        # Create .env if missing
        if [[ ! -f .env ]]; then
            cat > .env << 'ENVEOF'
SECRET_KEY=CHANGE_ME_$(openssl rand -hex 32)
QBITTORRENT_PASSWORD=CHANGE_ME_TOO
ENVEOF
            warn "Created .env with placeholder secrets - UPDATE THEM!"
        fi
        
        docker compose up -d --build
        
        echo "Waiting for services..."
        sleep 10
        
        docker compose ps
        curl -k https://localhost/health
EOF
    
    success "Backend deployed to paperclip"
}

# ============================================================================
# COPY RELEASE APK TO OTA DIRECTORY
# ============================================================================
setup_ota() {
    log "Setting up OTA update..."
    
    if [[ -f "$RELEASE_APK" ]]; then
        # Copy to paperclip OTA directory
        if ssh -o ConnectTimeout=5 paperclip@100.72.156.27 "true" 2>/dev/null; then
            log "Copying release APK to OTA directory..."
            scp "$RELEASE_APK" paperclip@100.72.156.27:/mnt/drive2/ota/1.0.0/app-release.apk
            ssh paperclip@100.72.156.27 << 'EOF'
                cat > /mnt/drive2/ota/1.0.0/release_notes.txt << 'NOTEOF'
Initial release - Audible Clone v1.0.0
- Library with cover art, progress tracking
- Full-featured player (speed, sleep timer, chapters)
- LibriVox integration (free public domain books)
- AudioBookBay integration (magnet links)
- TTS ebook conversion (Piper + Kokoro voices)
- Highlights with sync
- Self-hosted OTA updates
NOTEOF
EOF
            success "OTA configured"
        else
            warn "Paperclip not reachable. OTA setup skipped."
        fi
    else
        warn "Release APK not found, OTA setup skipped"
    fi
}

# ============================================================================
# RUN TESTS
# ============================================================================
run_tests() {
    log "Running unit tests..."
    cd "$ANDROID_DIR"
    ./gradlew test --no-daemon || warn "Some tests failed"
    success "Tests completed"
}

# ============================================================================
# MAIN
# ============================================================================
main() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  AUDIBLE CLONE - AUTOMATED BUILD & DEPLOY                     ║"
    echo "║  Project: $PROJECT_DIR"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    check_prerequisites
    build_android
    install_on_device
    deploy_backend
    setup_ota
    run_tests
    
    echo -e "${GREEN}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  BUILD & DEPLOY COMPLETE!                                     ║"
    echo "╠══════════════════════════════════════════════════════════════╣"
    echo "║  Debug APK:   $DEBUG_APK"
    echo "║  Release APK: $RELEASE_APK"
    echo "║  Backend:     https://yourdomain.com/api/v1"
    echo "║  OTA:         /mnt/drive2/ota/1.0.0/app-release.apk"
    echo "╚═════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# Run if executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi