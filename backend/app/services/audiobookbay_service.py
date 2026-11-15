import re
import asyncio
from typing import Dict, Any, List, Optional
from datetime import datetime
from dataclasses import dataclass

import httpx
from bs4 import BeautifulSoup

from app.core.config import get_settings


@dataclass
class ABBBook:
    title: str
    link: str
    cover: str
    language: str
    post_date: str
    format: str
    bitrate: str
    file_size: str
    magnet_link: str = None


class AudioBookBayService:
    def __init__(self):
        self.settings = get_settings()
        self.client = httpx.AsyncClient(
            timeout=30.0,
            follow_redirects=True,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
            }
        )
        self.abb_hostname = self.settings.ABB_HOSTNAME
    
    async def close(self):
        await self.client.aclose()
    
    async def search(self, query: str, max_pages: int = None) -> List[Dict[str, Any]]:
        """Search AudioBookBay for audiobooks."""
        if max_pages is None:
            max_pages = self.settings.ABB_PAGE_LIMIT
        
        results = []
        
        for page in range(1, max_pages + 1):
            url = f"https://{self.abb_hostname}/page/{page}/?s={query.lower().replace(' ', '+')}"
            
            try:
                response = await self.client.get(url)
                response.raise_for_status()
            except Exception as e:
                print(f"[ERROR] Failed to fetch page {page}: {e}")
                break
            
            soup = BeautifulSoup(response.text, "html.parser")
            posts = soup.select(".post")
            
            if not posts:
                print(f"No more results found on page {page}.")
                break
            
            print(f"Processing {len(posts)} posts on page {page}...")
            
            for post in posts:
                try:
                    book = await self._parse_post(post)
                    if book:
                        results.append(book)
                except Exception as e:
                    print(f"[ERROR] Could not process a post: {e}")
                    continue
        
        return results
    
    async def _parse_post(self, post) -> Optional[Dict[str, Any]]:
        """Parse a single post element."""
        title_element = post.select_one(".postTitle > h2 > a")
        if not title_element:
            return None
        
        title = title_element.text.strip()
        link = f"https://{self.abb_hostname}{title_element['href']}"
        
        # Cover image
        cover_url = None
        img = post.select_one("img")
        if img and img.get("src"):
            cover_url = img["src"]
            # Validate cover URL
            if not await self._is_url_valid(cover_url):
                cover_url = None
        
        # Post info
        post_info = post.select_one(".postInfo")
        post_info_text = post_info.get_text(separator=" ", strip=True) if post_info else ""
        
        # Language
        language_match = re.search(
            r"Language:\s*(.*?)(?:\s*Keywords:|$)", post_info_text, re.DOTALL
        )
        language = language_match.group(1).strip() if language_match else "N/A"
        
        # Details
        details_paragraph = post.select_one(".postContent p[style*='text-align:center']")
        post_date, book_format, bitrate, file_size = "N/A", "N/A", "N/A", "N/A"
        
        if details_paragraph:
            details_html = str(details_paragraph)
            
            post_date_match = re.search(r"Posted:\s*([^<]+)", details_html)
            if post_date_match:
                post_date = post_date_match.group(1).strip()
            
            format_match = re.search(r"Format:\s*<span[^>]*>([^<]+)</span>", details_html)
            if format_match:
                book_format = format_match.group(1).strip()
            
            bitrate_match = re.search(r"Bitrate:\s*<span[^>]*>([^<]+)</span>", details_html)
            if bitrate_match:
                bitrate = bitrate_match.group(1).strip()
            
            file_size_match = re.search(
                r"File Size:\s*<span[^>]*>([^<]+)</span>\s*([^<]+)", details_html
            )
            if file_size_match:
                file_size = f"{file_size_match.group(1).strip()} {file_size_match.group(2).strip()}"
        
        return {
            "title": title,
            "link": link,
            "cover": cover_url,
            "language": language,
            "post_date": post_date,
            "format": book_format,
            "bitrate": bitrate,
            "file_size": file_size,
        }
    
    async def _is_url_valid(self, url: str) -> bool:
        """Check if URL is valid and returns 200."""
        try:
            resp = await self.client.head(url, timeout=5.0)
            return resp.status_code == 200
        except Exception:
            return False
    
    async def extract_magnet_link(self, details_url: str) -> Optional[str]:
        """Extract magnet link from book details page."""
        try:
            response = await self.client.get(details_url)
            if response.status_code != 200:
                print(f"[ERROR] Failed to fetch details page: {response.status_code}")
                return None
            
            soup = BeautifulSoup(response.text, "html.parser")
            
            # Extract Info Hash
            info_hash_row = soup.find("td", string=re.compile(r"Info Hash", re.IGNORECASE))
            if not info_hash_row:
                print("[ERROR] Info Hash not found on the page.")
                return None
            
            info_hash = info_hash_row.find_next_sibling("td").text.strip()
            
            # Extract Trackers
            tracker_rows = soup.find_all("td", string=re.compile(r"udp://|http://", re.IGNORECASE))
            trackers = [row.text.strip() for row in tracker_rows]
            
            if not trackers:
                print("[WARNING] No trackers found. Using defaults.")
                trackers = [
                    "udp://tracker.openbittorrent.com:80",
                    "udp://opentor.org:2710",
                    "udp://tracker.ccc.de:80",
                    "udp://tracker.blackunicorn.xyz:6969",
                    "udp://tracker.coppersurfer.tk:6969",
                    "udp://tracker.leechers-paradise.org:6969",
                ]
            
            # Construct magnet link
            import urllib.parse
            trackers_query = "&".join(
                f"tr={urllib.parse.quote(tracker)}" for tracker in trackers
            )
            magnet_link = f"magnet:?xt=urn:btih:{info_hash}&{trackers_query}"
            
            print(f"[DEBUG] Generated Magnet Link: {magnet_link}")
            return magnet_link
            
        except Exception as e:
            print(f"[ERROR] Failed to extract magnet link: {e}")
            return None
    
    async def get_book_details(self, details_url: str) -> Optional[Dict[str, Any]]:
        """Get full details for a book from its details page."""
        try:
            response = await self.client.get(details_url)
            if response.status_code != 200:
                return None
            
            soup = BeautifulSoup(response.text, "html.parser")
            
            # Get magnet link
            magnet_link = await self.extract_magnet_link(details_url)
            
            # Parse additional details
            book = await self._parse_details_page(soup)
            book['magnet_link'] = magnet_link
            book['details_url'] = details_url
            
            return book
        except Exception as e:
            print(f"[ERROR] Failed to get book details: {e}")
            return None
    
    async def _parse_details_page(self, soup: BeautifulSoup) -> Dict[str, Any]:
        """Parse the details page for additional metadata."""
        # Title
        title_element = soup.select_one(".postTitle > h2 > a")
        title = title_element.text.strip() if title_element else "Unknown"
        
        # Cover
        cover_url = None
        img = soup.select_one(".postContent img")
        if img and img.get("src"):
            cover_url = img["src"]
        
        # Description/Content
        content = soup.select_one(".postContent")
        description = content.get_text(separator="\n", strip=True) if content else ""
        
        return {
            "title": title,
            "cover": cover_url,
            "description": description,
        }
    
    def sanitize_title(self, title: str) -> str:
        """Sanitize title for filesystem."""
        return re.sub(r'[<>:\"/\\|?*]', "", title).strip()


async def test_abb():
    """Test function for AudioBookBay service."""
    service = AudioBookBayService()
    try:
        results = await service.search("Harry Potter", max_pages=2)
        print(f"Found {len(results)} results")
        for r in results[:3]:
            print(f"  - {r['title']} ({r['format']}, {r['bitrate']})")
        
        if results:
            details = await service.get_book_details(results[0]['link'])
            print(f"\nDetails for first result:")
            print(f"  Magnet: {details.get('magnet_link', 'N/A')[:100]}...")
    finally:
        await service.close()


if __name__ == "__main__":
    asyncio.run(test_abb())