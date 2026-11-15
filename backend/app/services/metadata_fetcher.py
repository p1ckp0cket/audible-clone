import httpx
import asyncio
from typing import Dict, Any, Optional, List
from dataclasses import dataclass
import re

from app.core.config import get_settings


@dataclass
class BookMetadata:
    title: str = ""
    author: str = ""
    narrator: str = ""
    series: str = ""
    series_number: float = 0
    description: str = ""
    genres: List[str] = None
    tags: List[str] = None
    published_year: int = 0
    publisher: str = ""
    isbn: str = ""
    asin: str = ""
    language: str = "en"
    cover_url: str = ""
    duration_seconds: int = 0
    
    def __post_init__(self):
        if self.genres is None:
            self.genres = []
        if self.tags is None:
            self.tags = []


class MetadataFetcher:
    def __init__(self):
        self.settings = get_settings()
        self.client = httpx.AsyncClient(timeout=30.0, follow_redirects=True)
    
    async def close(self):
        await self.client.aclose()
    
    async def fetch_metadata(self, title: str, author: str) -> Dict[str, Any]:
        """Fetch metadata from multiple sources and merge results."""
        results = {}
        
        # Try OpenLibrary first (free, no key needed)
        ol_data = await self._fetch_openlibrary(title, author)
        if ol_data:
            results.update(ol_data)
        
        # Try Google Books (better covers, more data)
        gb_data = await self._fetch_google_books(title, author)
        if gb_data:
            # Merge, preferring non-empty values
            for k, v in gb_data.items():
                if v and not results.get(k):
                    results[k] = v
        
        # Try MusicBrainz (good for audiobooks)
        mb_data = await self._fetch_musicbrainz(title, author)
        if mb_data:
            for k, v in mb_data.items():
                if v and not results.get(k):
                    results[k] = v
        
        return results
    
    async def _fetch_openlibrary(self, title: str, author: str) -> Optional[Dict[str, Any]]:
        """Fetch from OpenLibrary API."""
        try:
            # Search by title and author
            params = {
                'title': title,
                'author': author,
                'limit': 5,
            }
            resp = await self.client.get(
                f"{self.settings.OPENLIBRARY_API_BASE}/volumes/brief/json",
                params=params
            )
            if resp.status_code != 200:
                return None
            
            data = resp.json()
            records = data.get('records', {})
            if not records:
                return None
            
            # Get first record
            first_key = list(records.keys())[0]
            record = records[first_key]
            
            # Get detailed info
            ol_key = record.get('key', '').replace('/books/', '')
            if ol_key:
                detail_resp = await self.client.get(
                    f"{self.settings.OPENLIBRARY_API_BASE}/books/{ol_key}.json"
                )
                if detail_resp.status_code == 200:
                    detail = detail_resp.json()
                    return self._parse_openlibrary(detail)
            
            return self._parse_openlibrary(record)
        except Exception as e:
            print(f"OpenLibrary error: {e}")
            return None
    
    def _parse_openlibrary(self, data: Dict) -> Dict[str, Any]:
        result = {}
        
        if data.get('title'):
            result['title'] = data['title']
        
        authors = data.get('authors', [])
        if authors:
            result['author'] = authors[0].get('name', '')
        
        if data.get('description'):
            desc = data['description']
            if isinstance(desc, dict):
                result['description'] = desc.get('value', '')
            else:
                result['description'] = str(desc)
        
        if data.get('subjects'):
            result['genres'] = data['subjects'][:10]
        
        if data.get('publish_date'):
            try:
                year_match = re.search(r'\b(19|20)\d{2}\b', str(data['publish_date']))
                if year_match:
                    result['published_year'] = int(year_match.group())
            except:
                pass
        
        if data.get('publishers'):
            result['publisher'] = data['publishers'][0]
        
        if data.get('isbn_13'):
            result['isbn'] = data['isbn_13'][0]
        elif data.get('isbn_10'):
            result['isbn'] = data['isbn_10'][0]
        
        # Cover URL
        if data.get('cover', {}).get('large'):
            result['cover_url'] = data['cover']['large']
        elif data.get('cover', {}).get('medium'):
            result['cover_url'] = data['cover']['medium']
        elif data.get('cover', {}).get('small'):
            result['cover_url'] = data['cover']['small']
        elif data.get('key'):
            # Construct cover URL from OLID
            olid = data['key'].replace('/books/OL', '').replace('/works/OL', '').replace('/authors/OL', '')
            result['cover_url'] = f"https://covers.openlibrary.org/b/olid/{olid}-L.jpg"
        
        return result
    
    async def _fetch_google_books(self, title: str, author: str) -> Optional[Dict[str, Any]]:
        """Fetch from Google Books API."""
        try:
            query = f'intitle:{title} inauthor:{author}'
            params = {'q': query, 'maxResults': 5}
            resp = await self.client.get(
                f"{self.settings.GOOGLE_BOOKS_API_BASE}/volumes",
                params=params
            )
            if resp.status_code != 200:
                return None
            
            data = resp.json()
            items = data.get('items', [])
            if not items:
                return None
            
            # Find best match
            for item in items:
                volume = item.get('volumeInfo', {})
                if self._title_match(volume.get('title', ''), title):
                    return self._parse_google_books(volume)
            
            return self._parse_google_books(items[0].get('volumeInfo', {}))
        except Exception as e:
            print(f"Google Books error: {e}")
            return None
    
    def _parse_google_books(self, data: Dict) -> Dict[str, Any]:
        result = {}
        
        if data.get('title'):
            result['title'] = data['title']
        
        if data.get('authors'):
            result['author'] = ', '.join(data['authors'])
        
        if data.get('description'):
            result['description'] = data['description']
        
        if data.get('categories'):
            result['genres'] = data['categories']
        
        if data.get('publishedDate'):
            try:
                year = int(data['publishedDate'][:4])
                if 1000 <= year <= 2100:
                    result['published_year'] = year
            except:
                pass
        
        if data.get('publisher'):
            result['publisher'] = data['publisher']
        
        # ISBN
        identifiers = data.get('industryIdentifiers', [])
        for ident in identifiers:
            if ident.get('type') == 'ISBN_13':
                result['isbn'] = ident.get('identifier')
                break
            elif ident.get('type') == 'ISBN_10' and not result.get('isbn'):
                result['isbn'] = ident.get('identifier')
        
        if data.get('language'):
            result['language'] = data['language']
        
        # Cover - prefer highest resolution
        image_links = data.get('imageLinks', {})
        for size in ['extraLarge', 'large', 'medium', 'small', 'thumbnail']:
            if image_links.get(size):
                result['cover_url'] = image_links[size]
                break
        
        if data.get('pageCount'):
            # Estimate duration: ~150 words/min, ~300 pages = 10 hours
            result['duration_seconds'] = int(data['pageCount'] * 120)  # rough estimate
        
        return result
    
    async def _fetch_musicbrainz(self, title: str, author: str) -> Optional[Dict[str, Any]]:
        """Fetch from MusicBrainz API."""
        try:
            query = f'release:"{title}" AND artist:"{author}"'
            params = {
                'query': query,
                'fmt': 'json',
                'limit': 5,
            }
            headers = {'User-Agent': 'AudibleClone/1.0 (your@email.com)'}
            resp = await self.client.get(
                f"{self.settings.MUSICBRAINZ_API_BASE}/release",
                params=params,
                headers=headers
            )
            if resp.status_code != 200:
                return None
            
            data = resp.json()
            releases = data.get('releases', [])
            if not releases:
                return None
            
            return self._parse_musicbrainz(releases[0])
        except Exception as e:
            print(f"MusicBrainz error: {e}")
            return None
    
    def _parse_musicbrainz(self, data: Dict) -> Dict[str, Any]:
        result = {}
        
        if data.get('title'):
            result['title'] = data['title']
        
        # Get artist credit
        artist_credit = data.get('artist-credit', [])
        if artist_credit:
            artists = [ac.get('artist', {}).get('name', '') for ac in artist_credit]
            result['author'] = ', '.join(artists)
        
        if data.get('date'):
            try:
                year = int(data['date'][:4])
                result['published_year'] = year
            except:
                pass
        
        if data.get('label-info'):
            labels = [li.get('label', {}).get('name', '') for li in data['label-info']]
            if labels:
                result['publisher'] = labels[0]
        
        # Genres from tags
        tags = data.get('tags', [])
        if tags:
            result['genres'] = [t.get('name', '') for t in tags[:10]]
        
        # Cover art from Cover Art Archive
        mbid = data.get('id')
        if mbid:
            result['cover_url'] = f"https://coverartarchive.org/release/{mbid}/front-500"
        
        return result
    
    def _title_match(self, title1: str, title2: str) -> bool:
        """Simple title matching."""
        t1 = re.sub(r'[^\w\s]', '', title1.lower())
        t2 = re.sub(r'[^\w\s]', '', title2.lower())
        return t1 in t2 or t2 in t1 or t1 == t2
    
    async def search_librivox(self, query: str, limit: int = 20, offset: int = 0) -> Dict[str, Any]:
        """Search LibriVox API."""
        try:
            params = {
                'title': query,
                'limit': min(limit, 500),
                'offset': offset,
                'format': 'json',
            }
            resp = await self.client.get(self.settings.LIBRIVOX_API_BASE, params=params)
            if resp.status_code == 200:
                return resp.json()
            return {}
        except Exception as e:
            print(f"LibriVox search error: {e}")
            return {}
    
    async def get_librivox_book(self, book_id: int) -> Dict[str, Any]:
        """Get detailed info for a LibriVox book."""
        try:
            params = {
                'id': book_id,
                'format': 'json',
                'extended': 1,
                'coverart': 1,
            }
            resp = await self.client.get(self.settings.LIBRIVOX_API_BASE, params=params)
            if resp.status_code == 200:
                return resp.json()
            return {}
        except Exception as e:
            print(f"LibriVox get error: {e}")
            return {}