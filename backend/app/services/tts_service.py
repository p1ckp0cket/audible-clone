import asyncio
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, asdict
from datetime import datetime
import re

import aiofiles
import ebooklib
from ebooklib import epub
from bs4 import BeautifulSoup

from app.core.config import get_settings
from app.models.database import TTSTask, Book, Chapter, BookStatus, BookSource
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select


@dataclass
class CharacterVoice:
    character: str
    voice_id: str
    voice_name: str
    engine: str = "piper"
    language: str = "en"
    speed: float = 1.0
    pitch: float = 0.0


@dataclass
class TTSChapter:
    index: int
    title: str
    text: str
    characters: List[str]  # Characters speaking in this chapter
    audio_path: str = ""
    duration_seconds: float = 0.0


class TTSEngine:
    """Multi-engine TTS supporting Piper (fast, CPU) and Kokoro (high quality)."""
    
    def __init__(self):
        self.settings = get_settings()
        self.piper_path = self._find_piper()
        self.piper_models_dir = Path(self.settings.PIPER_MODEL_DIR)
        self.kokoro_model_path = Path(self.settings.KOKORO_MODEL_PATH)
        self.kokoro_voices_path = Path(self.settings.KOKORO_VOICES_PATH)
        
        # Cache available voices
        self._piper_voices = None
        self._kokoro_voices = None
    
    def _find_piper(self) -> Optional[str]:
        paths = [
            "/usr/local/bin/piper",
            "/usr/bin/piper",
            "/opt/piper/piper",
            shutil.which("piper"),
        ]
        for p in paths:
            if p and Path(p).exists():
                return p
        return None
    
    def get_piper_voices(self) -> List[Dict[str, Any]]:
        """Get available Piper voices with metadata."""
        if self._piper_voices is not None:
            return self._piper_voices
        
        voices = []
        
        if self.piper_models_dir.exists():
            for onnx_file in self.piper_models_dir.glob("*.onnx"):
                json_file = onnx_file.with_suffix(".onnx.json")
                
                voice_info = {
                    "id": onnx_file.stem,
                    "name": self._format_voice_name(onnx_file.stem),
                    "path": str(onnx_file),
                    "config_path": str(json_file) if json_file.exists() else None,
                    "engine": "piper",
                    "language": self._extract_language(onnx_file.stem),
                    "quality": "high",
                }
                
                if json_file.exists():
                    try:
                        with open(json_file) as f:
                            config = json.load(f)
                            voice_info["sample_rate"] = config.get("audio", {}).get("sample_rate", 22050)
                            voice_info["speaker_id"] = config.get("speaker_id", 0)
                    except:
                        pass
                
                voices.append(voice_info)
        
        voices.sort(key=lambda v: (v["language"], v["name"]))
        self._piper_voices = voices
        return voices
    
    def get_kokoro_voices(self) -> List[Dict[str, Any]]:
        """Get available Kokoro voices."""
        if self._kokoro_voices is not None:
            return self._kokoro_voices
        
        voices = []
        
        if self.kokoro_voices_path.exists():
            try:
                import numpy as np
                # Kokoro voices are stored as numpy .bin files (npz format)
                data = np.load(self.kokoro_voices_path, allow_pickle=True)
                if hasattr(data, 'item'):
                    kokoro_data = data.item()
                else:
                    kokoro_data = dict(data)
                
                for voice_name, info in kokoro_data.items():
                    voices.append({
                        "id": voice_name,
                        "name": voice_name.replace("_", " ").title(),
                        "engine": "kokoro",
                        "language": "en",  # Default, could be inferred
                        "speaker_id": hash(voice_name) % 100,  # Deterministic ID
                        "quality": "premium",
                    })
            except Exception as e:
                print(f"Error loading Kokoro voices: {e}")
        
        if not voices:
            voices = [
                {"id": "af", "name": "Adult Female (US)", "engine": "kokoro", "language": "en-US", "speaker_id": 0, "quality": "premium"},
                {"id": "am", "name": "Adult Male (US)", "engine": "kokoro", "language": "en-US", "speaker_id": 1, "quality": "premium"},
                {"id": "bf", "name": "British Female", "engine": "kokoro", "language": "en-GB", "speaker_id": 2, "quality": "premium"},
                {"id": "bm", "name": "British Male", "engine": "kokoro", "language": "en-GB", "speaker_id": 3, "quality": "premium"},
            ]
        
        voices.sort(key=lambda v: (v["language"], v["name"]))
        self._kokoro_voices = voices
        return voices
    
    def get_all_voices(self) -> List[Dict[str, Any]]:
        """Get all available voices from both engines."""
        all_voices = []
        all_voices.extend(self.get_piper_voices())
        all_voices.extend(self.get_kokoro_voices())
        return all_voices
    
    def _format_voice_name(self, voice_id: str) -> str:
        parts = voice_id.split("-")
        if len(parts) >= 3:
            lang = parts[0] + "-" + parts[1]
            name = parts[2]
            quality = parts[3] if len(parts) > 3 else "medium"
            
            lang_names = {
                "en_US": "US English",
                "en_GB": "British English",
                "en_AU": "Australian English",
            }
            lang_display = lang_names.get(lang, lang)
            return f"{name.title()} ({lang_display}, {quality.title()})"
        return voice_id.replace("_", " ").title()
    
    def _extract_language(self, voice_id: str) -> str:
        parts = voice_id.split("-")
        if len(parts) >= 2:
            return parts[0] + "-" + parts[1]
        return "en-US"
    
    async def synthesize(self, text: str, voice: CharacterVoice, output_path: Path) -> bool:
        if voice.engine == "piper":
            return await self._synthesize_piper(text, voice, output_path)
        elif voice.engine == "kokoro":
            return await self._synthesize_kokoro(text, voice, output_path)
        return False
    
    async def _synthesize_piper(self, text: str, voice: CharacterVoice, output_path: Path) -> bool:
        if not self.piper_path:
            print("Piper not found")
            return False
        
        model_path = self.piper_models_dir / f"{voice.voice_id}.onnx"
        if not model_path.exists():
            models = list(self.piper_models_dir.glob(f"{voice.voice_id}*.onnx"))
            if models:
                model_path = models[0]
            else:
                print(f"Piper model not found: {voice.voice_id}")
                return False
        
        cmd = [
            self.piper_path,
            "--model", str(model_path),
            "--output_file", str(output_path),
        ]
        
        if voice.speed != 1.0:
            cmd.extend(["--length_scale", str(1.0 / voice.speed)])
        
        if hasattr(voice, 'speaker_id') and voice.speaker_id:
            cmd.extend(["--speaker", str(voice.speaker_id)])
        
        try:
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await process.communicate(input=text.encode())
            
            if process.returncode != 0:
                print(f"Piper error: {stderr.decode()}")
                return False
            
            return output_path.exists() and output_path.stat().st_size > 0
        except Exception as e:
            print(f"Piper synthesis error: {e}")
            return False
    
    async def _synthesize_kokoro(self, text: str, voice: CharacterVoice, output_path: Path) -> bool:
        try:
            import kokoro_onnx
            
            model = kokoro_onnx.Kokoro(str(self.kokoro_model_path), str(self.kokoro_voices_path))
            
            audio, sample_rate = model.create(
                text,
                voice=voice.voice_id,
                speed=voice.speed,
            )
            
            import soundfile as sf
            sf.write(str(output_path), audio, sample_rate)
            
            return output_path.exists() and output_path.stat().st_size > 0
            
        except ImportError:
            print("kokoro-onnx not installed, falling back to Piper")
            fallback_voice = CharacterVoice(
                character=voice.character,
                voice_id="en_US-amy-medium",
                voice_name="Amy (Fallback)",
                engine="piper",
            )
            return await self._synthesize_piper(text, fallback_voice, output_path)
        except Exception as e:
            print(f"Kokoro synthesis error: {e}")
            return False


class EPUBParser:
    """Parse EPUB files for TTS conversion with advanced character detection."""
    
    def __init__(self):
        self.chapters = []
        self.metadata = {}
        self.characters = set()
        self.character_dialogue_counts = {}
    
    def parse(self, epub_path: Path) -> Dict[str, Any]:
        book = epub.read_epub(str(epub_path))
        
        self.metadata = {
            'title': self._get_metadata(book, 'title'),
            'author': self._get_metadata(book, 'creator'),
            'language': self._get_metadata(book, 'language', 'en'),
            'publisher': self._get_metadata(book, 'publisher'),
            'identifier': self._get_metadata(book, 'identifier'),
            'description': self._get_metadata(book, 'description'),
        }
        
        self.chapters = []
        self.characters = set()
        self.character_dialogue_counts = {}
        
        spine_items = [item for item in book.get_items_of_type(ebooklib.ITEM_DOCUMENT)]
        
        chapter_index = 0
        for item in spine_items:
            content = item.get_content().decode('utf-8', errors='ignore')
            soup = BeautifulSoup(content, 'html.parser')
            
            title = ''
            for heading in soup.find_all(['h1', 'h2', 'h3', 'h4']):
                title = heading.get_text(strip=True)
                if title:
                    break
            
            if not title:
                title = f"Chapter {chapter_index + 1}"
            
            text = self._extract_text(soup)
            
            characters = self._detect_characters_advanced(text, soup)
            for char in characters:
                self.characters.add(char)
                self.character_dialogue_counts[char] = self.character_dialogue_counts.get(char, 0) + 1
            
            if text.strip():
                self.chapters.append({
                    'index': chapter_index,
                    'title': title,
                    'text': text,
                    'characters': list(characters),
                    'html': content,
                    'word_count': len(text.split()),
                })
                chapter_index += 1
        
        sorted_characters = sorted(
            self.characters, 
            key=lambda c: self.character_dialogue_counts.get(c, 0), 
            reverse=True
        )
        
        return {
            'metadata': self.metadata,
            'chapters': self.chapters,
            'characters': sorted_characters,
            'character_stats': self.character_dialogue_counts,
        }
    
    def _get_metadata(self, book, name: str, default: str = '') -> str:
        meta = book.get_metadata('DC', name)
        if meta:
            return meta[0][0]
        return default
    
    def _extract_text(self, soup: BeautifulSoup) -> str:
        for tag in soup(['script', 'style', 'nav', 'header', 'footer']):
            tag.decompose()
        
        paragraphs = []
        for p in soup.find_all(['p', 'div', 'span', 'h1', 'h2', 'h3', 'h4', 'blockquote']):
            text = p.get_text(separator=' ', strip=True)
            if text and len(text) > 10:
                paragraphs.append(text)
        
        return '\n\n'.join(paragraphs)
    
    def _detect_characters_advanced(self, text: str, soup: BeautifulSoup) -> set:
        characters = set()
        
        patterns = [
            r'"[^"]+"\s*[-–—]\s*([A-Z][a-zA-Z\'\-]+)',
            r'"[^"]+"\s*,\s*([A-Z][a-zA-Z\'\-]+)\s+(?:said|asked|replied|whispered|shouted|murmured|yelled|screamed|hissed|breathed|gasped|laughed|chuckled|snorted|grunted|sighed|groaned|moaned|cried|sobbed|whimpered)',
            r'([A-Z][a-zA-Z\'\-]+)\s+(?:said|asked|replied|whispered|shouted|murmured|yelled|screamed|hissed|breathed|gasped|laughed|chuckled|snorted|grunted|sighed|groaned|moaned|cried|sobbed|whimpered)\s*[,:]\s*"[^"]+"',
            r'^([A-Z][a-zA-Z\'\-]+):\s*"[^"]+"',
            r'[\*_]{1,2}([A-Z][a-zA-Z\'\-]+)[\*_]{1,2}\s*[:\-]',
        ]
        
        for pattern in patterns:
            matches = re.findall(pattern, text, re.MULTILINE | re.IGNORECASE)
            for match in matches:
                char = match.strip()
                if len(char) > 1 and not char.lower() in {'he', 'she', 'it', 'they', 'we', 'you', 'i'}:
                    characters.add(char)
        
        for tag in soup.find_all(['p', 'span', 'div'], class_=re.compile(r'speaker|character|dialogue', re.I)):
            speaker = tag.get('data-speaker') or tag.get('speaker')
            if speaker:
                characters.add(speaker.strip())
        
        return characters


class TTSService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.settings = get_settings()
        self.engine = TTSEngine()
        self.parser = EPUBParser()
    
    async def process_ebook(self, task_id: int, auto_assign_voices: bool = True) -> bool:
        task = await self._get_task(task_id)
        if not task:
            return False
        
        try:
            await self._update_task(task, "parsing", progress=0.05, current_step="Parsing ebook")
            
            ebook_path = Path(task.ebook_path)
            if task.ebook_format == 'epub':
                parsed = self.parser.parse(ebook_path)
            else:
                parsed = await self._parse_other(ebook_path, task.ebook_format)
            
            if auto_assign_voices and not task.voice_mapping:
                task.voice_mapping = await self._auto_assign_voices(parsed['characters'], parsed.get('character_stats', {}))
                await self.db.commit()
            
            if not task.voice_mapping:
                task.voice_mapping = {char: self.settings.TTS_DEFAULT_VOICE for char in parsed['characters']}
            
            await self._update_task(task, "synthesizing", progress=0.1, current_step="Starting synthesis")
            
            output_dir = Path(self.settings.TTS_OUTPUT_PATH) / f"tts_task_{task.id}"
            output_dir.mkdir(parents=True, exist_ok=True)
            
            chapters_data = []
            total_chapters = len(parsed['chapters'])
            
            for i, chapter in enumerate(parsed['chapters']):
                progress = 0.1 + (0.8 * i / max(total_chapters, 1))
                await self._update_task(
                    task, "synthesizing", 
                    progress=progress,
                    current_step=f"Synthesizing chapter {i+1}/{total_chapters}: {chapter['title']}"
                )
                
                chapter_audio = await self._synthesize_chapter(chapter, task.voice_mapping, output_dir)
                
                if chapter_audio:
                    duration = await self._get_audio_duration(chapter_audio)
                    chapters_data.append({
                        'index': chapter['index'],
                        'title': chapter['title'],
                        'file_path': chapter_audio,
                        'duration_seconds': duration,
                        'word_count': chapter.get('word_count', 0),
                    })
            
            await self._update_task(task, "merging", progress=0.9, current_step="Merging chapters into audiobook")
            
            final_path = await self._create_m4b_audiobook(chapters_data, output_dir, parsed['metadata'])
            
            book = await self._create_book_entry(task, parsed, chapters_data, final_path)
            
            task.status = "completed"
            task.progress = 1.0
            task.completed_at = datetime.utcnow()
            task.output_path = final_path
            task.book_id = book.id
            
            await self.db.commit()
            return True
            
        except Exception as e:
            await self._update_task(task, "failed", error=str(e))
            return False
    
    async def _auto_assign_voices(self, characters: List[str], char_stats: Dict[str, int]) -> Dict[str, str]:
        voices = self.engine.get_all_voices()
        
        kokoro_voices = [v for v in voices if v['engine'] == 'kokoro']
        piper_voices = [v for v in voices if v['engine'] == 'piper']
        
        sorted_chars = sorted(characters, key=lambda c: char_stats.get(c, 0), reverse=True)
        
        assignment = {}
        premium_voices = kokoro_voices + piper_voices[:4]
        
        for i, char in enumerate(sorted_chars):
            if i < len(premium_voices):
                voice = premium_voices[i]
                assignment[char] = f"{voice['engine']}:{voice['id']}"
            else:
                piper_idx = (i - len(premium_voices)) % len(piper_voices)
                voice = piper_voices[piper_idx]
                assignment[char] = f"{voice['engine']}:{voice['id']}"
        
        # Always add narrator
        if 'narrator' not in assignment:
            narrator_voice = kokoro_voices[0] if kokoro_voices else piper_voices[0]
            assignment['narrator'] = f"{narrator_voice['engine']}:{narrator_voice['id']}"
        
        return assignment
    
    def parse_voice_assignment(self, assignment_str: str) -> CharacterVoice:
        if ':' in assignment_str:
            engine, voice_id = assignment_str.split(':', 1)
        else:
            engine, voice_id = 'piper', assignment_str
        
        all_voices = self.engine.get_all_voices()
        voice_info = next((v for v in all_voices if v['engine'] == engine and v['id'] == voice_id), None)
        
        return CharacterVoice(
            character="",
            voice_id=voice_id,
            voice_name=voice_info['name'] if voice_info else voice_id,
            engine=engine,
            language=voice_info.get('language', 'en') if voice_info else 'en',
            speaker_id=voice_info.get('speaker_id') if voice_info else None,
        )
    
    async def _synthesize_chapter(self, chapter: Dict, voice_mapping: Dict, output_dir: Path) -> Optional[str]:
        text = chapter['text']
        characters = chapter.get('characters', [])
        
        if not characters:
            narrator_voice = voice_mapping.get('narrator', list(voice_mapping.values())[0] if voice_mapping else 'piper:en_US-lessac-medium')
            voice = self.parse_voice_assignment(narrator_voice)
            voice.character = 'narrator'
            
            output_path = output_dir / f"chapter_{chapter['index']:04d}.wav"
            success = await self.engine.synthesize(text, voice, output_path)
            return str(output_path) if success else None
        
        segments = self._split_text_by_character(text, characters, voice_mapping)
        
        segment_files = []
        for seg_idx, (char, seg_text, voice_assignment) in enumerate(segments):
            voice = self.parse_voice_assignment(voice_assignment)
            voice.character = char
            
            seg_path = output_dir / f"chapter_{chapter['index']:04d}_seg_{seg_idx:03d}.wav"
            
            success = await self.engine.synthesize(seg_text, voice, seg_path)
            if success:
                segment_files.append(str(seg_path))
        
        if segment_files:
            merged_path = output_dir / f"chapter_{chapter['index']:04d}.wav"
            await self._merge_audio_files_with_pauses(segment_files, merged_path)
            return str(merged_path)
        
        return None
    
    def _split_text_by_character(self, text: str, characters: List[str], voice_mapping: Dict) -> List[Tuple[str, str, str]]:
        segments = []
        
        char_pattern = '|'.join(re.escape(c) for c in sorted(characters, key=len, reverse=True))
        
        patterns = [
            (rf'"([^"]+)"\s*[-–—]\s*({char_pattern})', 'trailing'),
            (rf'({char_pattern}):\s*"([^"]+)"', 'leading'),
            (rf'({char_pattern})\s+(?:said|asked|replied|whispered|shouted)\s*"([^"]+)"', 'leading'),
        ]
        
        dialogue_spans = []
        
        for pattern, style in patterns:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                if style == 'trailing':
                    dialogue_text = match.group(1)
                    speaker = match.group(2)
                else:
                    speaker = match.group(1)
                    dialogue_text = match.group(2)
                
                dialogue_spans.append({
                    'start': match.start(),
                    'end': match.end(),
                    'speaker': speaker,
                    'text': dialogue_text,
                    'full_match': match.group(0),
                })
        
        dialogue_spans.sort(key=lambda x: x['start'])
        
        last_end = 0
        for span in dialogue_spans:
            if span['start'] > last_end:
                narration = text[last_end:span['start']].strip()
                if narration:
                    narrator_voice = voice_mapping.get('narrator', list(voice_mapping.values())[0])
                    segments.append(('narrator', narration, narrator_voice))
            
            speaker = span['speaker']
            voice_assignment = voice_mapping.get(speaker, voice_mapping.get('narrator', list(voice_mapping.values())[0]))
            segments.append((speaker, span['text'], voice_assignment))
            
            last_end = span['end']
        
        if last_end < len(text):
            narration = text[last_end:].strip()
            if narration:
                narrator_voice = voice_mapping.get('narrator', list(voice_mapping.values())[0])
                segments.append(('narrator', narration, narrator_voice))
        
        if not segments:
            narrator_voice = voice_mapping.get('narrator', list(voice_mapping.values())[0] if voice_mapping else 'piper:en_US-lessac-medium')
            segments.append(('narrator', text, narrator_voice))
        
        return segments
    
    async def _merge_audio_files_with_pauses(self, input_files: List[str], output_file: Path, pause_duration: float = 0.3) -> bool:
        try:
            silence_file = output_file.with_suffix('.silence.wav')
            cmd_silence = [
                'ffmpeg', '-y', '-f', 'lavfi', '-i', f'anullsrc=r=22050:cl=mono', 
                '-t', str(pause_duration), str(silence_file)
            ]
            await asyncio.create_subprocess_exec(*cmd_silence)
            await asyncio.sleep(0.5)
            
            concat_file = output_file.with_suffix('.txt')
            async with aiofiles.open(concat_file, 'w') as f:
                for i, infile in enumerate(input_files):
                    await f.write(f"file '{infile}'\n")
                    if i < len(input_files) - 1 and silence_file.exists():
                        await f.write(f"file '{silence_file}'\n")
            
            cmd = [
                'ffmpeg', '-y', '-f', 'concat', '-safe', '0',
                '-i', str(concat_file),
                '-c', 'copy', str(output_file)
            ]
            
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            await process.communicate()
            
            concat_file.unlink(missing_ok=True)
            silence_file.unlink(missing_ok=True)
            
            return output_file.exists() and output_file.stat().st_size > 0
        except Exception as e:
            print(f"Merge with pauses error: {e}")
            return False
    
    async def _create_m4b_audiobook(self, chapters_data: List[Dict], output_dir: Path, metadata: Dict) -> str:
        final_path = output_dir / f"{self._sanitize(metadata.get('title', 'Audiobook'))}.m4b"
        
        concat_file = output_dir / "concat.txt"
        async with aiofiles.open(concat_file, 'w') as f:
            for ch in chapters_data:
                await f.write(f"file '{ch['file_path']}'\n")
        
        chapter_file = output_dir / "chapters.txt"
        async with aiofiles.open(chapter_file, 'w') as f:
            await f.write(";FFMETADATA1\n")
            await f.write(f"title={metadata.get('title', 'Audiobook')}\n")
            await f.write(f"artist={metadata.get('author', 'Unknown')}\n")
            await f.write(f"album={metadata.get('title', 'Audiobook')}\n")
            await f.write(f"date={metadata.get('published_year', '')}\n")
            await f.write(f"language={metadata.get('language', 'en')}\n")
            await f.write(f"genre=Audiobook\n")
            
            current_time = 0
            for ch in chapters_data:
                duration_ms = int(ch.get('duration_seconds', 0) * 1000)
                await f.write(f"[CHAPTER]\n")
                await f.write(f"TIMEBASE=1/1000\n")
                await f.write(f"START={current_time}\n")
                await f.write(f"END={current_time + duration_ms}\n")
                await f.write(f"title={ch['title']}\n")
                current_time += duration_ms
        
        cmd = [
            'ffmpeg', '-y',
            '-f', 'concat', '-safe', '0', '-i', str(concat_file),
            '-i', str(chapter_file),
            '-map_metadata', '1',
            '-c:a', 'aac', '-b:a', '128k', '-ar', '44100',
            '-map_chapters', '1',
            str(final_path)
        ]
        
        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await process.communicate()
        
        if process.returncode != 0:
            print(f"M4B creation error: {stderr.decode()}")
            cmd_simple = [
                'ffmpeg', '-y', '-f', 'concat', '-safe', '0',
                '-i', str(concat_file),
                '-c:a', 'aac', '-b:a', '128k',
                str(final_path)
            ]
            await asyncio.create_subprocess_exec(*cmd_simple)
        
        concat_file.unlink(missing_ok=True)
        chapter_file.unlink(missing_ok=True)
        
        return str(final_path)
    
    async def _get_audio_duration(self, audio_path: str) -> float:
        try:
            cmd = [
                'ffprobe', '-v', 'error',
                '-show_entries', 'format=duration',
                '-of', 'default=noprint_wrappers=1:nokey=1',
                audio_path
            ]
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, _ = await process.communicate()
            return float(stdout.decode().strip())
        except Exception:
            return 0.0
    
    async def _create_book_entry(self, task: TTSTask, parsed: Dict, 
                                chapters_data: List[Dict], final_path: str) -> Book:
        book_hash = f"tts_{task.id}"
        
        total_duration = sum(c.get('duration_seconds', 0) for c in chapters_data)
        total_size = Path(final_path).stat().st_size if Path(final_path).exists() else 0
        
        book = Book(
            title=task.title or parsed['metadata'].get('title', 'TTS Audiobook'),
            author=task.author or parsed['metadata'].get('author', 'Unknown'),
            description=f"Generated from ebook via TTS ({task.engine}) with full cast voices",
            language=task.language or parsed['metadata'].get('language', 'en'),
            duration_seconds=int(total_duration),
            source=BookSource.TTS_GENERATED,
            source_id=book_hash,
            status=BookStatus.AVAILABLE,
            file_path=final_path,
            file_size=total_size,
            file_format='m4b',
            bitrate=128,
            sample_rate=44100,
            chapters_count=len(chapters_data),
            added_at=datetime.utcnow(),
        )
        
        self.db.add(book)
        await self.db.flush()
        
        current_offset = 0.0
        for ch_data in chapters_data:
            duration = ch_data.get('duration_seconds', 0)
            chapter = Chapter(
                book_id=book.id,
                index=ch_data['index'],
                title=ch_data['title'],
                start_offset_seconds=current_offset,
                end_offset_seconds=current_offset + duration,
                duration_seconds=duration,
                file_path=ch_data['file_path'],
            )
            self.db.add(chapter)
            current_offset += duration
        
        return book
    
    def _sanitize(self, name: str) -> str:
        return re.sub(r'[<>:\"/\\|?*]', "", name).strip()
    
    async def _parse_other(self, path: Path, format: str) -> Dict[str, Any]:
        if format == 'txt':
            text = path.read_text(encoding='utf-8', errors='ignore')
            return {
                'metadata': {'title': path.stem, 'author': 'Unknown', 'language': 'en'},
                'chapters': [{
                    'index': 0,
                    'title': path.stem,
                    'text': text,
                    'characters': [],
                    'word_count': len(text.split()),
                }],
                'characters': [],
            }
        return {'metadata': {}, 'chapters': [], 'characters': []}
    
    async def _get_task(self, task_id: int):
        result = await self.db.execute(select(TTSTask).where(TTSTask.id == task_id))
        return result.scalar_one_or_none()
    
    async def _update_task(self, task: TTSTask, status: str, progress: float = None, 
                          current_step: str = None, error: str = None):
        task.status = status
        if progress is not None:
            task.progress = progress
        if current_step:
            task.current_step = current_step
        if error:
            task.error_message = error
        if status in ("parsing", "synthesizing", "merging") and not task.started_at:
            task.started_at = datetime.utcnow()
        await self.db.commit()
    
    def get_available_voices(self) -> List[Dict[str, Any]]:
        return self.engine.get_all_voices()