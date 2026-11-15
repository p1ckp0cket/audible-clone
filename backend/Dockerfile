FROM python:3.12-slim

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libmagic1 \
    git \
    curl \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# Install Piper TTS from GitHub releases
RUN mkdir -p /opt/piper/models && \
    curl -L -o /tmp/piper.tar.gz https://github.com/rhasspy/piper/releases/download/v1.2.0/piper_amd64.tar.gz && \
    tar -xzf /tmp/piper.tar.gz -C /opt/piper && \
    rm /tmp/piper.tar.gz && \
    chmod +x /opt/piper/piper

# Create app user
RUN useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/

# Create data directories
RUN mkdir -p /data /mnt/drive2/audiobooks /mnt/drive2/ebooks /mnt/drive2/covers /mnt/drive2/tts_output \
    && chown -R appuser:appuser /app /data /mnt/drive2

# Switch to app user
USER appuser

# Expose port
EXPOSE 8000

# Run the application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]